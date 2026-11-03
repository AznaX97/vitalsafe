package com.vitalsafe.api.common;

import java.util.List;
import java.util.Map;

/** An import that was refused because some rows are not acceptable. Nothing was imported. */
public class ImportValidationException extends RuntimeException {

    public record RowError(int row, Map<String, String> fields) {}

    private final List<RowError> rows;
    private final int totalErrors;

    public ImportValidationException(List<RowError> rows, int totalErrors) {
        super("Import failed");
        this.rows = List.copyOf(rows);
        this.totalErrors = totalErrors;
    }

    public List<RowError> getRows() {
        return rows;
    }

    public int getTotalErrors() {
        return totalErrors;
    }
}