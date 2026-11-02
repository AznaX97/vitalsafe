package com.vitalsafe.api.vitals.csv;

import com.vitalsafe.api.common.FieldValidationException;
import com.vitalsafe.api.common.ImportValidationException;
import com.vitalsafe.api.vitals.TimeBounds;
import com.vitalsafe.api.vitals.VitalReading;
import com.vitalsafe.api.vitals.VitalReadingRepository;
import com.vitalsafe.api.vitals.VitalService;
import com.vitalsafe.api.vitals.VitalType;
import com.vitalsafe.api.vitals.dto.VitalRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class VitalCsvService {

    public static final int MAX_BYTES = 512 * 1024;
    static final int MAX_DATA_ROWS = 1000;
    private static final int MAX_COLUMNS = 10;
    private static final int MAX_FIELD_CHARS = 2000;
    private static final int MAX_REPORTED_ERRORS = 50;
    private static final int EXPORT_PAGE_SIZE = 500;
    private static final List<String> COLUMNS = List.of("type", "value", "secondaryValue", "unit", "measuredAt", "note");
    private static final List<String> REQUIRED = List.of("type", "value", "measuredAt");

    public record ImportResult(int imported, int skippedDuplicates) {}

    private final VitalService vitals;
    private final VitalReadingRepository readings;
    private final int maxExportRows;

    public VitalCsvService(VitalService vitals, VitalReadingRepository readings, @Value("${app.csv.max-export-rows:20000}") int maxExportRows) {
        this.vitals = vitals;
        this.readings = readings;
        this.maxExportRows = maxExportRows;
    }

    /** Not transactional here: the single save at the end runs in its own transaction, after every row is checked. */
    public ImportResult importCsv(UUID userId, byte[] bytes) {
        List<List<String>> rows = read(bytes);
        List<String> header = rows.get(0);
        Map<String, Integer> columns = columnsOf(header);

        List<VitalReading> prepared = new ArrayList<>();
        List<ImportValidationException.RowError> rowErrors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> errors = new LinkedHashMap<>();
            VitalRequest request = null;
            if (cells.size() != header.size()) {
                errors.put("row", "Expected " + header.size() + " columns but found " + cells.size());
            } else {
                request = toRequest(cells, columns, errors);
            }
            if (request != null && errors.isEmpty()) {
                try {
                    prepared.add(vitals.prepare(userId, request));
                } catch (FieldValidationException e) {
                    errors.putAll(e.getFields());
                }
            }
            if (!errors.isEmpty()) {
                rowErrors.add(new ImportValidationException.RowError(i + 1, errors));   // the header is row 1
            }
        }
        if (!rowErrors.isEmpty()) {
            throw new ImportValidationException(
                    rowErrors.subList(0, Math.min(MAX_REPORTED_ERRORS, rowErrors.size())), rowErrors.size());
        }
        if (prepared.isEmpty()) {
            throw bad("The file has no data rows");
        }

        // Skip readings that already exist (same type and second), and repeats inside the file itself
        Instant earliest = prepared.stream().map(VitalReading::getMeasuredAt).min(Instant::compareTo).orElseThrow();
        Instant latest = prepared.stream().map(VitalReading::getMeasuredAt).max(Instant::compareTo).orElseThrow();
        Set<String> known = new HashSet<>();
        for (VitalReadingRepository.TypeAndTime existing
                : readings.findByUserIdAndMeasuredAtBetween(userId, earliest.minusSeconds(1), latest.plusSeconds(1))) {
            known.add(key(existing.getType(), existing.getMeasuredAt()));
        }
        List<VitalReading> toSave = new ArrayList<>();
        int skipped = 0;
        for (VitalReading reading : prepared) {
            if (known.add(key(reading.getType(), reading.getMeasuredAt()))) {
                toSave.add(reading);
            } else {
                skipped++;
            }
        }
        vitals.saveAll(toSave);
        return new ImportResult(toSave.size(), skipped);
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID userId, Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldValidationException(Map.of("from", "from must not be after to"));
        }
        Instant start = from == null ? TimeBounds.EARLIEST : from;
        Instant end = to == null ? TimeBounds.LATEST : to;

        Page<VitalReading> page = readings.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                userId, start, end, PageRequest.of(0, EXPORT_PAGE_SIZE));
        if (page.getTotalElements() > maxExportRows) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(413),
                    "Too many readings for one export. Use from and to to export in parts.");
        }

        StringBuilder out = new StringBuilder(String.join(",", COLUMNS)).append("\r\n");
        while (true) {
            for (VitalReading reading : page.getContent()) {
                out.append(CsvWriter.line(Arrays.asList(
                        reading.getType().name(),
                        reading.getValuePrimary().toPlainString(),
                        reading.getValueSecondary() == null ? "" : reading.getValueSecondary().toPlainString(),
                        reading.getUnit(),
                        reading.getMeasuredAt().toString(),
                        CsvWriter.neutralise(reading.getNote())))).append("\r\n");
            }
            if (!page.hasNext()) {
                break;
            }
            page = readings.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                    userId, start, end, page.nextPageable());
        }
        return out.toString();
    }

    // ---------- parsing ----------

    private static List<List<String>> read(byte[] bytes) {
        try {
            List<List<String>> rows = CsvReader.parse(CsvReader.decode(bytes), MAX_DATA_ROWS + 1, MAX_COLUMNS, MAX_FIELD_CHARS);
            if (rows.isEmpty()) {
                throw bad("The file is empty");
            }
            return rows;
        } catch (CsvReader.CsvFormatException e) {
            throw bad(e.getMessage());
        }
    }

    private static Map<String, Integer> columnsOf(List<String> header) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = canonical(header.get(i));
            if (name == null) {
                throw bad("Column " + (i + 1) + " is not recognised. Allowed columns: " + String.join(", ", COLUMNS));
            }
            if (index.put(name, i) != null) {
                throw bad("Column " + (i + 1) + " repeats an earlier column");
            }
        }
        for (String required : REQUIRED) {
            if (!index.containsKey(required)) {
                throw bad("Missing required column: " + required);
            }
        }
        return index;
    }

    private static String canonical(String headerCell) {
        String trimmed = headerCell.trim();
        for (String name : COLUMNS) {
            if (name.equalsIgnoreCase(trimmed)) {
                return name;
            }
        }
        return null;
    }

    private static VitalRequest toRequest(List<String> cells, Map<String, Integer> columns, Map<String, String> errors) {
        VitalType type = null;
        String typeText = cell(cells, columns, "type").trim();
        if (typeText.isEmpty()) {
            errors.put("type", "type is required");
        } else {
            try {
                type = VitalType.valueOf(typeText.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.put("type", "type must be one of: " + String.join(", ", Arrays.stream(VitalType.values()).map(Enum::name).toList()));
            }
        }
        BigDecimal value = number(cell(cells, columns, "value"), "value", errors);
        BigDecimal secondary = number(cell(cells, columns, "secondaryValue"), "secondaryValue", errors);
        String unit = cell(cells, columns, "unit").trim();
        String note = cell(cells, columns, "note");

        Instant measuredAt = null;
        String timeText = cell(cells, columns, "measuredAt").trim();
        if (timeText.isEmpty()) {
            errors.put("measuredAt", "measuredAt is required");
        } else {
            try {
                measuredAt = OffsetDateTime.parse(timeText).toInstant();
            } catch (DateTimeParseException e) {
                errors.put("measuredAt", "measuredAt must be an ISO-8601 time with a zone, for example 2026-01-15T08:00:00Z");
            }
        }
        if (!errors.isEmpty()) {
            return null;
        }
        return new VitalRequest(type, value, secondary, unit.isEmpty() ? null : unit, measuredAt, note.isBlank() ? null : note);
    }

    private static String cell(List<String> cells, Map<String, Integer> columns, String name) {
        Integer position = columns.get(name);
        return position == null ? "" : cells.get(position);
    }

    private static BigDecimal number(String text, String field, Map<String, String> errors) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= 24) {
            try {
                return new BigDecimal(trimmed);
            } catch (NumberFormatException e) {
                // falls through to the message below
            }
        }
        errors.put(field, field + " must be a number");
        return null;
    }

    private static String key(VitalType type, Instant at) {
        return type.name() + "|" + at.getEpochSecond();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}