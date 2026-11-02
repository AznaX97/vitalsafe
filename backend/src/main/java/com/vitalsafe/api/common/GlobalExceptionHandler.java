package com.vitalsafe.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return validationBody(fields);
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFieldValidation(FieldValidationException ex) {
        return validationBody(ex.getFields());
    }

    @ExceptionHandler(ImportValidationException.class)
    public ResponseEntity<Map<String, Object>> handleImportValidation(ImportValidationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Import failed: nothing was imported");
        body.put("rowErrors", ex.getTotalErrors());
        body.put("rows", ex.getRows());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Malformed request body"));
    }

    // For example a path like /vitals/not-a-uuid, or a query parameter with the wrong type
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid value for '" + ex.getName() + "'"));
    }

    // Without this, the catch-all below would turn a forbidden request into a 500
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
        if (ex instanceof ResponseStatusException rse) {
            String reason = rse.getReason() != null ? rse.getReason() : "Request failed";
            return ResponseEntity.status(rse.getStatusCode()).body(Map.of("error", reason));
        }
        if (ex instanceof ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode()).body(Map.of("error", "Request failed"));
        }
        // Log only the exception type: a message could contain personal or health data
        log.error("Unhandled error: {}", ex.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Something went wrong"));
    }

    private static ResponseEntity<Map<String, Object>> validationBody(Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Validation failed");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }
}