package com.family.expensemanager.common.report;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Renders any {@link ReportColumn}/row model into a CSV byte stream. Shared by every CSV report. */
@Component
public class CsvReportGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public <T> byte[] generate(List<ReportColumn<T>> columns, List<T> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Leading BOM so Excel detects UTF-8 and renders Vietnamese diacritics correctly.
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader(columns.stream().map(ReportColumn::header).toArray(String[]::new))
                    .build();
            try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (T row : rows) {
                    printer.printRecord(columns.stream().map(c -> formatValue(c.valueExtractor().apply(row))).toList());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMAT);
        }
        if (value instanceof LocalDate date) {
            return date.format(DATE_FORMAT);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return FormulaInjectionGuard.sanitize(value.toString());
    }
}
