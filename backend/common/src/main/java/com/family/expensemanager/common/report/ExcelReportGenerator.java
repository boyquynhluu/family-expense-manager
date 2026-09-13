package com.family.expensemanager.common.report;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fills a pre-styled .xlsx template with row data. Shared by every Excel report: the template
 * supplies the title/header styling and one pre-styled, otherwise-empty row at
 * {@code dataStartRowIndex} whose per-column style (borders, number/date format, alignment) is
 * cloned onto every generated data row, so callers never touch POI styling directly.
 */
@Component
public class ExcelReportGenerator {

    public <T> byte[] generate(InputStream template, int dataStartRowIndex,
                                List<ReportColumn<T>> columns, List<T> rows) {
        try (InputStream in = template; Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            CellStyle[] styles = captureStyles(sheet, dataStartRowIndex, columns.size());

            int rowIndex = dataStartRowIndex;
            for (T rowData : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < columns.size(); col++) {
                    Cell cell = row.createCell(col);
                    cell.setCellStyle(styles[col]);
                    setCellValue(cell, columns.get(col).valueExtractor().apply(rowData));
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CellStyle[] captureStyles(Sheet sheet, int templateRowIndex, int columnCount) {
        Row templateRow = sheet.getRow(templateRowIndex);
        CellStyle[] styles = new CellStyle[columnCount];
        for (int col = 0; col < columnCount; col++) {
            Cell cell = templateRow != null ? templateRow.getCell(col) : null;
            styles[col] = cell != null ? cell.getCellStyle() : sheet.getWorkbook().createCellStyle();
        }
        return styles;
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
