package com.family.expensemanager.common.report;

import java.util.function.Function;

/**
 * One column of a report: a header label (used by CSV; ignored by Excel, whose template
 * already carries the header text) and the function that pulls this column's value out
 * of a row object. The same list of columns feeds both {@link CsvReportGenerator} and
 * {@link ExcelReportGenerator}, so a report's shape is defined exactly once.
 */
public record ReportColumn<T>(String header, Function<T, Object> valueExtractor) {
}
