package com.vitalsafe.api.vitals.csv;

import java.util.List;
import java.util.stream.Collectors;

public final class CsvWriter {

    private CsvWriter() {
    }

    /** Quotes a field when it contains a comma, a quote, a line break, or leading or trailing spaces. */
    public static String field(String text) {
        if (text == null) {
            return "";
        }
        boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                || !text.equals(text.strip());
        String escaped = text.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }

    public static String line(List<String> fields) {
        return fields.stream().map(CsvWriter::field).collect(Collectors.joining(","));
    }

    /**
     * Spreadsheet programs treat a cell that starts with = + - @ (or a tab or carriage return) as a formula.
     * A leading apostrophe makes them show it as plain text instead.
     */
    public static String neutralise(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        char first = text.charAt(0);
        boolean risky = first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
        return risky ? "'" + text : text;
    }
}