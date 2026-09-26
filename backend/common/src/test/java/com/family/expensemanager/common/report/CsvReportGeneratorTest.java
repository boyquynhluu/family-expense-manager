package com.family.expensemanager.common.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

class CsvReportGeneratorTest {

    private record Row(String note) {
    }

    private final CsvReportGenerator generator = new CsvReportGenerator();
    private final List<ReportColumn<Row>> columns = List.of(new ReportColumn<>("Ghi chú", Row::note));

    @Test
    void generate_prefixesANoteThatWouldOtherwiseBeReadAsAFormula() throws IOException {
        byte[] csv = generator.generate(columns, List.of(new Row("=cmd|'/c calc'!A0")));

        CSVRecord record = firstDataRow(csv);
        assertThat(record.get("Ghi chú")).isEqualTo("'=cmd|'/c calc'!A0");
    }

    @Test
    void generate_leavesAnOrdinaryNoteUntouched() throws IOException {
        byte[] csv = generator.generate(columns, List.of(new Row("Tiền chợ tháng 9")));

        assertThat(firstDataRow(csv).get("Ghi chú")).isEqualTo("Tiền chợ tháng 9");
    }

    private CSVRecord firstDataRow(byte[] csv) throws IOException {
        // Skip the UTF-8 BOM the generator writes so Excel detects the encoding.
        try (var reader = new InputStreamReader(
                new ByteArrayInputStream(csv, 3, csv.length - 3), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder().setHeader().build())) {
            return parser.getRecords().get(0);
        }
    }
}
