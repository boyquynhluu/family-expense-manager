package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.expense.dto.CategoryResponse;
import com.family.expensemanager.expense.dto.ImportResult;
import com.family.expensemanager.expense.dto.ImportRowError;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.WalletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The reverse of {@link TransactionReportService}'s export: reads a CSV or Excel file
 * back in and bulk-creates transactions from it (README "5. Chỉ export, không import").
 * Expects the exact column headers the export produces — "Thời gian", "Ví", "Danh mục",
 * "Loại", "Số tiền", "Ghi chú" — so a user can export, edit in a spreadsheet, and
 * re-import, or hand-build a file in the same shape for a one-time bulk load of old data.
 *
 * Each row is created via {@link TransactionService#create}, same as a manual entry, so
 * budget-crossing emails / cache eviction / events all fire identically. A row that fails
 * (unknown wallet/category, bad amount, etc.) is skipped and reported — the rest of the
 * file still imports, since one typo shouldn't block importing everything else.
 */
@Service
@RequiredArgsConstructor
@Slf4j(topic = "TransactionImportService")
public class TransactionImportService {

    private static final int MAX_IMPORT_ROWS = 1000;
    private static final int MAX_HEADER_SCAN_ROWS = 20;
    private static final int MIN_HEADER_MATCHES = 4;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String LABEL_EXPENSE = "Chi tiêu";
    private static final String LABEL_INCOME = "Thu nhập";
    private static final String COL_DATE = "Thời gian";
    private static final String COL_WALLET = "Ví";
    private static final String COL_CATEGORY = "Danh mục";
    private static final String COL_TYPE = "Loại";
    private static final String COL_AMOUNT = "Số tiền";
    private static final String COL_NOTE = "Ghi chú";
    private static final Set<String> EXPECTED_HEADERS =
            Set.of(COL_DATE, COL_WALLET, COL_CATEGORY, COL_TYPE, COL_AMOUNT, COL_NOTE);
    private static final Set<String> REQUIRED_HEADERS =
            Set.of(COL_DATE, COL_WALLET, COL_CATEGORY, COL_TYPE, COL_AMOUNT);

    private final TransactionService transactionService;
    private final WalletService walletService;
    private final CategoryService categoryService;

    public ImportResult importFile(
            Long familyId, Long userId, String userEmail, String userDisplayName, MultipartFile file) {
        log.info("importFile - start, familyId={}, filename={}", familyId, file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new BadRequestException("File trống");
        }

        List<Map<String, String>> rawRows = isExcel(file) ? parseExcel(file) : parseCsv(file);
        if (rawRows.isEmpty()) {
            throw new BadRequestException("File không có dữ liệu để nhập");
        }
        if (rawRows.size() > MAX_IMPORT_ROWS) {
            throw new BadRequestException("Chỉ hỗ trợ nhập tối đa " + MAX_IMPORT_ROWS + " dòng mỗi lần");
        }
        requireValidHeaders(rawRows.get(0).keySet());

        Map<String, WalletResponse> walletsByName = walletService.listByFamily(familyId).stream()
                .collect(Collectors.toMap(w -> key(w.name()), w -> w, (a, b) -> a));
        Map<String, CategoryResponse> categoriesByKey = categoryService.listByFamily(familyId).stream()
                .collect(Collectors.toMap(c -> key(c.name()) + "|" + c.type(), c -> c, (a, b) -> a));

        List<ImportRowError> errors = new ArrayList<>();
        int imported = 0;
        for (int i = 0; i < rawRows.size(); i++) {
            int rowNumber = i + 2; // row 1 is the header
            int errorsBefore = errors.size();
            processRow(rowNumber, rawRows.get(i), familyId, userId, userEmail, userDisplayName, walletsByName,
                    categoriesByKey, errors);
            if (errors.size() == errorsBefore) {
                imported++;
            }
        }
        log.info("importFile - done, familyId={}, totalRows={}, imported={}, errors={}",
                familyId, rawRows.size(), imported, errors.size());
        return new ImportResult(rawRows.size(), imported, errors);
    }

    private void processRow(int rowNumber, Map<String, String> raw, Long familyId, Long userId, String userEmail,
                             String userDisplayName, Map<String, WalletResponse> walletsByName,
                             Map<String, CategoryResponse> categoriesByKey, List<ImportRowError> errors) {
        String dateStr = value(raw, COL_DATE);
        String walletName = value(raw, COL_WALLET);
        String categoryName = value(raw, COL_CATEGORY);
        String typeLabel = value(raw, COL_TYPE);
        String amountStr = value(raw, COL_AMOUNT);
        String note = value(raw, COL_NOTE);

        if (dateStr.isEmpty() || walletName.isEmpty() || categoryName.isEmpty() || typeLabel.isEmpty()
                || amountStr.isEmpty()) {
            errors.add(new ImportRowError(rowNumber,
                    "Thiếu dữ liệu bắt buộc (Thời gian/Ví/Danh mục/Loại/Số tiền)"));
            return;
        }

        String type;
        if (LABEL_EXPENSE.equalsIgnoreCase(typeLabel)) {
            type = "EXPENSE";
        } else if (LABEL_INCOME.equalsIgnoreCase(typeLabel)) {
            type = "INCOME";
        } else {
            errors.add(new ImportRowError(rowNumber,
                    "Loại không hợp lệ: \"" + typeLabel + "\" (phải là \"" + LABEL_EXPENSE + "\" hoặc \""
                            + LABEL_INCOME + "\")"));
            return;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            errors.add(new ImportRowError(rowNumber, "Ngày không hợp lệ: \"" + dateStr + "\" (định dạng yyyy-MM-dd)"));
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr.replace(",", "").trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            errors.add(new ImportRowError(rowNumber, "Số tiền không hợp lệ: \"" + amountStr + "\""));
            return;
        }

        WalletResponse wallet = walletsByName.get(key(walletName));
        if (wallet == null) {
            errors.add(new ImportRowError(rowNumber, "Không tìm thấy ví: \"" + walletName + "\""));
            return;
        }

        CategoryResponse category = categoriesByKey.get(key(categoryName) + "|" + type);
        if (category == null) {
            errors.add(new ImportRowError(rowNumber,
                    "Không tìm thấy danh mục \"" + categoryName + "\" thuộc loại \"" + typeLabel + "\""));
            return;
        }

        TransactionRequest request = new TransactionRequest(
                wallet.id(), category.id(), type, amount, date.atStartOfDay(), note.isEmpty() ? null : note);
        try {
            transactionService.create(familyId, userId, userEmail, userDisplayName, request);
        } catch (Exception e) {
            log.warn("importFile - dòng {} thất bại", rowNumber, e);
            errors.add(new ImportRowError(rowNumber, "Lỗi khi tạo giao dịch: " + e.getMessage()));
        }
    }

    private void requireValidHeaders(Set<String> headers) {
        if (!headers.containsAll(REQUIRED_HEADERS)) {
            throw new BadRequestException(
                    "File thiếu cột bắt buộc. Cần có: " + String.join(", ", REQUIRED_HEADERS));
        }
    }

    private String value(Map<String, String> row, String column) {
        String v = row.get(column);
        return v == null ? "" : v.trim();
    }

    private String key(String name) {
        return name.trim().toLowerCase();
    }

    private boolean isExcel(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        return (filename != null && filename.toLowerCase().endsWith(".xlsx"))
                || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType);
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) {
        try {
            byte[] bytes = file.getInputStream().readAllBytes();
            int offset = hasUtf8Bom(bytes) ? 3 : 0;
            try (Reader reader = new InputStreamReader(
                    new ByteArrayInputStream(bytes, offset, bytes.length - offset), StandardCharsets.UTF_8)) {
                CSVFormat format = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .setIgnoreSurroundingSpaces(true)
                        .build();
                CSVParser parser = CSVParser.parse(reader, format);
                List<Map<String, String>> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    rows.add(record.toMap());
                }
                return rows;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file CSV", e);
        }
    }

    private boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
    }

    private List<Map<String, String>> parseExcel(MultipartFile file) {
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowIndex = findHeaderRowIndex(sheet);
            Row headerRow = sheet.getRow(headerRowIndex);

            Map<Integer, String> headerByColumn = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                String text = cell.getStringCellValue().trim();
                if (!text.isEmpty()) {
                    headerByColumn.put(cell.getColumnIndex(), text);
                }
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row)) {
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : headerByColumn.entrySet()) {
                    map.put(entry.getValue(), cellToString(row.getCell(entry.getKey())));
                }
                rows.add(map);
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file Excel", e);
        }
    }

    /** Scans the first few rows for one that looks like the header, tolerating title/spacer rows above it. */
    private int findHeaderRowIndex(Sheet sheet) {
        int lastScan = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN_ROWS);
        for (int r = sheet.getFirstRowNum(); r <= lastScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            long matches = 0;
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && EXPECTED_HEADERS.contains(cell.getStringCellValue().trim())) {
                    matches++;
                }
            }
            if (matches >= MIN_HEADER_MATCHES) {
                return r;
            }
        }
        throw new BadRequestException(
                "Không tìm thấy dòng tiêu đề hợp lệ trong file (cần có các cột: " + String.join(", ", REQUIRED_HEADERS) + ")");
    }

    private boolean isRowBlank(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK && !cellToString(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String cellToString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMAT)
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
