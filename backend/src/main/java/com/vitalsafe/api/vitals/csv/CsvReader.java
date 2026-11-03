package com.vitalsafe.api.vitals.csv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A small, strict CSV reader (RFC 4180 style) with hard limits, so a hostile file cannot use much memory
 * or time. Error messages contain row numbers and fixed text, never the file's own content.
 */
public final class CsvReader {

    public static class CsvFormatException extends RuntimeException {
        public CsvFormatException(String message) {
            super(message);
        }
    }

    private CsvReader() {
    }

    /** Decodes UTF-8 strictly and drops a leading byte-order mark. */
    public static String decode(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new CsvFormatException("The file must be UTF-8 text");
        }
        if (text.indexOf('\0') >= 0) {
            throw new CsvFormatException("The file contains binary data");
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** maxRows counts every row, including the header. */
    public static List<List<String>> parse(String text, int maxRows, int maxColumns, int maxFieldChars) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        boolean closedQuote = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int rowNumber = rows.size() + 1;

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == ',') {
                endField(row, field, maxColumns, rowNumber);
                fieldStarted = false;
                closedQuote = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                endField(row, field, maxColumns, rowNumber);
                rows.add(row);
                checkRowCount(rows, maxRows);
                row = new ArrayList<>();
                fieldStarted = false;
                closedQuote = false;
            } else if (closedQuote) {
                throw new CsvFormatException("Row " + rowNumber + ": unexpected text after a closing quote");
            } else if (c == '"') {
                if (fieldStarted) {
                    throw new CsvFormatException("Row " + rowNumber + ": a quote inside an unquoted field");
                }
                inQuotes = true;
                fieldStarted = true;
            } else {
                field.append(c);
                fieldStarted = true;
            }

            if (field.length() > maxFieldChars) {
                throw new CsvFormatException("Row " + rowNumber + ": a field is longer than " + maxFieldChars + " characters");
            }
        }

        if (inQuotes) {
            throw new CsvFormatException("The file ends inside a quoted field");
        }
        if (fieldStarted || !row.isEmpty()) {
            endField(row, field, maxColumns, rows.size() + 1);
            rows.add(row);
            checkRowCount(rows, maxRows);
        }
        return rows;
    }

    private static void endField(List<String> row, StringBuilder field, int maxColumns, int rowNumber) {
        if (row.size() >= maxColumns) {
            throw new CsvFormatException("Row " + rowNumber + ": more than " + maxColumns + " columns");
        }
        row.add(field.toString());
        field.setLength(0);
    }

    private static void checkRowCount(List<List<String>> rows, int maxRows) {
        if (rows.size() > maxRows) {
            throw new CsvFormatException("The file has more than " + (maxRows - 1) + " data rows");
        }
    }
}