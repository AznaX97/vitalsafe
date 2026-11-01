package com.vitalsafe.api.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thrown when input is well-formed but breaks a business rule. Becomes a 400 with per-field messages. */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public FieldValidationException(Map<String, String> fields) {
        super("Validation failed");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, String> getFields() {
        return fields;
    }
}