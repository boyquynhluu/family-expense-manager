package com.family.expensemanager.common.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelReportGeneratorTest {

    private record RowData(String note) {
    }

    private final ExcelReportGenerator generator = new ExcelReportGenerator();
    private final List<ReportColumn<RowData>> columns = List.of(new ReportColumn<>("Ghi chú", RowData::note));

    @Test
    void generate_prefixesANoteThatWouldOtherwiseBeReadAsAFormula() throws IOException {
        Cell cell = firstDataCell(generator.generate(minimalTemplate(), 0, columns, List.of(new RowData("=1+1"))));

        assertThat(cell.getStringCellValue()).isEqualTo("'=1+1");
    }

    @Test
    void generate_leavesAnOrdinaryNoteUntouched() throws IOException {
        Cell cell = firstDataCell(
                generator.generate(minimalTemplate(), 0, columns, List.of(new RowData("Tiền chợ tháng 9"))));

        assertThat(cell.getStringCellValue()).isEqualTo("Tiền chợ tháng 9");
    }

    /** One sheet with an empty, otherwise-unstyled row at index 0 — enough for {@link ExcelReportGenerator}. */
    private InputStream minimalTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row templateRow = sheet.createRow(0);
            templateRow.createCell(0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private Cell firstDataCell(byte[] xlsx) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            return workbook.getSheetAt(0).getRow(0).getCell(0);
        }
    }
}
