package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.expense.dto.CategoryResponse;
import com.family.expensemanager.expense.dto.TransactionRequest;
import com.family.expensemanager.expense.dto.WalletResponse;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    @Mock
    private TransactionService transactionService;
    @Mock
    private WalletService walletService;
    @Mock
    private CategoryService categoryService;

    private TransactionImportService importService;

    @BeforeEach
    void setUp() {
        importService = new TransactionImportService(transactionService, walletService, categoryService);
    }

    @Test
    void importFile_importsValidCsvRow() {
        when(walletService.listByFamily(1L)).thenReturn(List.of(wallet(10L, "Ví chính")));
        when(categoryService.listByFamily(1L)).thenReturn(List.of(category(20L, "Ăn uống", "EXPENSE")));
        MockMultipartFile file = csvFile(
                "Thời gian,Ví,Danh mục,Loại,Số tiền,Ghi chú\n"
                        + "2026-01-05,Ví chính,Ăn uống,Chi tiêu,50000,Trưa\n");

        var result = importService.importFile(1L, 100L, "a@b.com", "An", file);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(eq(1L), eq(100L), eq("a@b.com"), eq("An"), captor.capture());
        assertThat(captor.getValue().walletId()).isEqualTo(10L);
        assertThat(captor.getValue().categoryId()).isEqualTo(20L);
        assertThat(captor.getValue().type()).isEqualTo("EXPENSE");
        assertThat(captor.getValue().amount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(captor.getValue().occurredAt()).isEqualTo(LocalDate.of(2026, 1, 5).atStartOfDay());
        assertThat(captor.getValue().note()).isEqualTo("Trưa");
    }

    @Test
    void importFile_reportsError_whenWalletNotFound() {
        when(walletService.listByFamily(1L)).thenReturn(List.of());
        when(categoryService.listByFamily(1L)).thenReturn(List.of(category(20L, "Ăn uống", "EXPENSE")));
        MockMultipartFile file = csvFile(
                "Thời gian,Ví,Danh mục,Loại,Số tiền,Ghi chú\n"
                        + "2026-01-05,Không tồn tại,Ăn uống,Chi tiêu,50000,\n");

        var result = importService.importFile(1L, 100L, "a@b.com", "An", file);

        assertThat(result.importedCount()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
        verify(transactionService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void importFile_reportsError_whenCategoryTypeMismatch() {
        // Category exists but only as INCOME, while the row says "Chi tiêu" (EXPENSE).
        when(walletService.listByFamily(1L)).thenReturn(List.of(wallet(10L, "Ví chính")));
        when(categoryService.listByFamily(1L)).thenReturn(List.of(category(20L, "Ăn uống", "INCOME")));
        MockMultipartFile file = csvFile(
                "Thời gian,Ví,Danh mục,Loại,Số tiền,Ghi chú\n"
                        + "2026-01-05,Ví chính,Ăn uống,Chi tiêu,50000,\n");

        var result = importService.importFile(1L, 100L, "a@b.com", "An", file);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("danh mục");
    }

    @Test
    void importFile_reportsError_whenAmountInvalid() {
        when(walletService.listByFamily(1L)).thenReturn(List.of(wallet(10L, "Ví chính")));
        when(categoryService.listByFamily(1L)).thenReturn(List.of(category(20L, "Ăn uống", "EXPENSE")));
        MockMultipartFile file = csvFile(
                "Thời gian,Ví,Danh mục,Loại,Số tiền,Ghi chú\n"
                        + "2026-01-05,Ví chính,Ăn uống,Chi tiêu,không phải số,\n");

        var result = importService.importFile(1L, 100L, "a@b.com", "An", file);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("Số tiền");
    }

    @Test
    void importFile_reportsError_whenTypeLabelInvalid() {
        when(walletService.listByFamily(1L)).thenReturn(List.of(wallet(10L, "Ví chính")));
        when(categoryService.listByFamily(1L)).thenReturn(List.of(category(20L, "Ăn uống", "EXPENSE")));
        MockMultipartFile file = csvFile(
                "Thời gian,Ví,Danh mục,Loại,Số tiền,Ghi chú\n"
                        + "2026-01-05,Ví chính,Ăn uống,Không rõ,50000,\n");

        var result = importService.importFile(1L, 100L, "a@b.com", "An", file);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("Loại");
    }

    @Test
    void importFile_reportsError_whenDateInvalid() {
        when(walletService.listByFamily(1L)).thenReturn(List.of(wallet(10L, "Ví chính")));
        when(categoryService.listByFamily(1L)).thenReturn(List.of(category(20L, "Ăn uống", "EXPENSE")));
        MockMultipartFile file = csvFile(
                "Thời gian,Ví,Danh mục,Loại,Số tiền,Ghi chú\n"
                        + "05/01/2026,Ví chính,Ăn uống,Chi tiêu,50000,\n");

        var result = importService.importFile(1L, 100L, "a@b.com", "An", file);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("Ngày");
    }

    @Test
    void importFile_continuesAfterRowError_andImportsRemainingValidRows() {
        when(walletService.listByFamily(1L)).thenReturn(List.of(wallet(10L, "Ví chính")));
        when(categoryService.listByFamily(1L)).thenReturn(List.of(category(20L, "Ăn uống", "EXPENSE")));
        MockMultipartFile file = csvFile(
                "Thời gian,Ví,Danh mục,Loại,Số tiền,Ghi chú\n"
                        + "2026-01-05,Ví không tồn tại,Ăn uống,Chi tiêu,50000,\n"
                        + "2026-01-06,Ví chính,Ăn uống,Chi tiêu,30000,\n");

        var result = importService.importFile(1L, 100L, "a@b.com", "An", file);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).rowNumber()).isEqualTo(2);
        verify(transactionService, never()).create(eq(1L), eq(100L), eq("a@b.com"), eq("An"),
                argThat(r -> r.amount().compareTo(BigDecimal.valueOf(50000)) == 0));
        verify(transactionService).create(eq(1L), eq(100L), eq("a@b.com"), eq("An"),
                argThat(r -> r.amount().compareTo(BigDecimal.valueOf(30000)) == 0));
    }

    @Test
    void importFile_throwsBadRequest_whenFileEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> importService.importFile(1L, 100L, "a@b.com", "An", file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void importFile_throwsBadRequest_whenRequiredHeadersMissing() {
        MockMultipartFile file = csvFile("Ví,Danh mục\nA,B\n");

        assertThatThrownBy(() -> importService.importFile(1L, 100L, "a@b.com", "An", file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void importFile_parsesExcelFile_withTitleRowsAboveHeader_andNativeDateCell() throws Exception {
        when(walletService.listByFamily(1L)).thenReturn(List.of(wallet(10L, "Ví chính")));
        when(categoryService.listByFamily(1L)).thenReturn(List.of(category(20L, "Ăn uống", "EXPENSE")));

        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("BÁO CÁO GIAO DỊCH THU CHI");
            sheet.createRow(1); // blank spacer row, same layout as the export template

            Row headerRow = sheet.createRow(2);
            String[] headers = {"Thời gian", "Ví", "Danh mục", "Loại", "Số tiền", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));

            Row dataRow = sheet.createRow(3);
            var dateCell = dataRow.createCell(0);
            dateCell.setCellValue(LocalDate.of(2026, 1, 5));
            dateCell.setCellStyle(dateStyle);
            dataRow.createCell(1).setCellValue("Ví chính");
            dataRow.createCell(2).setCellValue("Ăn uống");
            dataRow.createCell(3).setCellValue("Chi tiêu");
            dataRow.createCell(4).setCellValue(50000);
            dataRow.createCell(5).setCellValue("Trưa");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            bytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "data.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        var result = importService.importFile(1L, 100L, "a@b.com", "An", file);

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(eq(1L), eq(100L), eq("a@b.com"), eq("An"), captor.capture());
        assertThat(captor.getValue().occurredAt()).isEqualTo(LocalDate.of(2026, 1, 5).atStartOfDay());
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "data.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static WalletResponse wallet(long id, String name) {
        return new WalletResponse(id, 1L, name, "VND", BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    private static CategoryResponse category(long id, String name, String type) {
        return new CategoryResponse(id, 1L, name, type, null, null, null);
    }

    private static TransactionRequest argThat(java.util.function.Predicate<TransactionRequest> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
