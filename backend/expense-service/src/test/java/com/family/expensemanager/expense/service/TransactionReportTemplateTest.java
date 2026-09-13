package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.report.CsvReportGenerator;
import com.family.expensemanager.common.report.ExcelReportGenerator;
import com.family.expensemanager.common.report.ReportColumn;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Sanity-checks the transaction-report-template.xlsx resource against the shared report generators. */
class TransactionReportTemplateTest {

    private record Row(LocalDateTime occurredAt, String wallet, BigDecimal amount) {
    }

    // Mirrors TransactionReportService.columns(): "Thời gian" is truncated to LocalDate so the
    // template's date-only format (and the CSV output) never show hour/minute/second.
    private static List<ReportColumn<Row>> columns() {
        return List.of(
                new ReportColumn<>("Thời gian", r -> r.occurredAt().toLocalDate()),
                new ReportColumn<>("Ví", Row::wallet),
                new ReportColumn<>("Số tiền", Row::amount));
    }

    @Test
    void excelTemplateFillsDataRowsWithClonedStylesAndDateOnlyColumn() throws IOException {
        List<Row> rows = List.of(
                new Row(LocalDateTime.of(2026, 1, 1, 8, 30), "Ví chính", new BigDecimal("150000")),
                new Row(LocalDateTime.of(2026, 1, 2, 9, 0), "Ví phụ", new BigDecimal("20000")));

        InputStream template = getClass().getClassLoader()
                .getResourceAsStream("templates/transaction-report-template.xlsx");
        assertThat(template).isNotNull();

        byte[] output = new ExcelReportGenerator().generate(template, 3, columns(), rows);
        assertThat(output).isNotEmpty();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("BÁO CÁO GIAO DỊCH THU CHI");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Thời gian");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Ví chính");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Ví phụ");
            assertThat(sheet.getRow(3).getCell(2).getNumericCellValue()).isEqualTo(150000d);

            assertThat(sheet.getRow(3).getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(sheet.getRow(3).getCell(0).getCellStyle().getDataFormatString()).isEqualTo("yyyy\\-mm\\-dd");
        }
    }

    @Test
    void csvGeneratorProducesUtf8BomAndDateOnlyColumn() {
        byte[] output = new CsvReportGenerator().generate(
                columns(), List.of(new Row(LocalDateTime.of(2026, 1, 1, 8, 30), "Ví chính", BigDecimal.TEN)));

        String content = new String(output, StandardCharsets.UTF_8);
        assertThat(content).startsWith("﻿").contains("Ví").contains("Ví chính").contains("2026-01-01");
        assertThat(content).doesNotContain("08:30");
    }
}
