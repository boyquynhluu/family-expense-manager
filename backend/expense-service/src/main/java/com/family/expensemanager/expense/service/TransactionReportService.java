package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.common.report.CsvReportGenerator;
import com.family.expensemanager.common.report.ExcelReportGenerator;
import com.family.expensemanager.common.report.ReportColumn;
import com.family.expensemanager.common.report.ReportFormat;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.domain.entity.Transaction;
import com.family.expensemanager.expense.dto.TransactionReportFilter;
import com.family.expensemanager.expense.dto.TransactionReportRow;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the transaction report row/column model once and hands it to whichever generator
 * matches the requested {@link ReportFormat} — the same data feeds both CSV and Excel exports.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j(topic = "TransactionReportService")
public class TransactionReportService {

    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final String EXCEL_TEMPLATE_PATH = "templates/transaction-report-template.xlsx";
    private static final int EXCEL_DATA_START_ROW = 3;

    private final TransactionDao transactionDao;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final CsvReportGenerator csvReportGenerator;
    private final ExcelReportGenerator excelReportGenerator;

    public byte[] export(Long familyId, TransactionReportFilter filter, ReportFormat format) {
        try {
            log.info("export - start, familyId={}, format={}", familyId, format);
            List<TransactionReportRow> rows = buildRows(familyId, filter);
            List<ReportColumn<TransactionReportRow>> columns = columns();
            return format == ReportFormat.EXCEL
                    ? excelReportGenerator.generate(openTemplate(), EXCEL_DATA_START_ROW, columns, rows)
                    : csvReportGenerator.generate(columns, rows);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransactionReportService.export", e);
        }
    }

    private InputStream openTemplate() {
        try {
            return new ClassPathResource(EXCEL_TEMPLATE_PATH).getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<TransactionReportRow> buildRows(Long familyId, TransactionReportFilter filter) {
        filter.validate();
        Map<Long, String> walletNames = walletService.listByFamily(familyId).stream()
                .collect(Collectors.toMap(w -> w.id(), w -> w.name()));
        Map<Long, String> categoryNames = categoryService.listByFamily(familyId).stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name()));

        return transactionDao.selectByFamilyId(familyId).stream()
                .filter(t -> matches(t, filter))
                .sorted(Comparator.comparing(Transaction::getOccurredAt))
                .map(t -> new TransactionReportRow(
                        t.getOccurredAt(),
                        walletNames.getOrDefault(t.getWalletId(), ""),
                        categoryNames.getOrDefault(t.getCategoryId(), ""),
                        TYPE_EXPENSE.equals(t.getType()) ? "Chi tiêu" : "Thu nhập",
                        t.getAmount(),
                        t.getNote() == null ? "" : t.getNote()))
                .toList();
    }

    private boolean matches(Transaction t, TransactionReportFilter filter) {
        if (filter.walletId() != null && !filter.walletId().equals(t.getWalletId())) {
            return false;
        }
        if (filter.categoryId() != null && !filter.categoryId().equals(t.getCategoryId())) {
            return false;
        }
        if (filter.type() != null && !filter.type().equalsIgnoreCase(t.getType())) {
            return false;
        }
        if (filter.fromDate() != null && t.getOccurredAt().toLocalDate().isBefore(filter.fromDate())) {
            return false;
        }
        if (filter.toDate() != null && t.getOccurredAt().toLocalDate().isAfter(filter.toDate())) {
            return false;
        }
        if (filter.minAmount() != null && t.getAmount().compareTo(filter.minAmount()) < 0) {
            return false;
        }
        if (filter.maxAmount() != null && t.getAmount().compareTo(filter.maxAmount()) > 0) {
            return false;
        }
        String query = filter.normalizedQuery();
        return query == null || (t.getNote() != null && t.getNote().toLowerCase(Locale.ROOT).contains(query));
    }

    private List<ReportColumn<TransactionReportRow>> columns() {
        return List.of(
                new ReportColumn<>("Thời gian", r -> r.occurredAt().toLocalDate()),
                new ReportColumn<>("Ví", TransactionReportRow::walletName),
                new ReportColumn<>("Danh mục", TransactionReportRow::categoryName),
                new ReportColumn<>("Loại", TransactionReportRow::typeLabel),
                new ReportColumn<>("Số tiền", TransactionReportRow::amount),
                new ReportColumn<>("Ghi chú", TransactionReportRow::note));
    }
}
