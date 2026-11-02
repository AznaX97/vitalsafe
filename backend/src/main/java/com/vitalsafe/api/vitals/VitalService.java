package com.vitalsafe.api.vitals;

import com.vitalsafe.api.common.FieldValidationException;
import com.vitalsafe.api.common.PageResponse;
import com.vitalsafe.api.vitals.dto.VitalRequest;
import com.vitalsafe.api.vitals.dto.VitalResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VitalService {

    static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NOTE_LENGTH = 1000;
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);

    private final VitalReadingRepository readings;

    public VitalService(VitalReadingRepository readings) {
        this.readings = readings;
    }

    @Transactional
    public VitalResponse create(UUID userId, VitalRequest request) {
        VitalReading reading = new VitalReading();
        reading.setUserId(userId);   // always the caller, taken from the verified token
        apply(reading, request);
        return VitalResponse.from(readings.save(reading));
    }

    @Transactional(readOnly = true)
    public PageResponse<VitalResponse> list(UUID userId, VitalType type, Instant from, Instant to,
                                            int page, int size) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (page < 0) {
            errors.put("page", "page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.put("size", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            errors.put("from", "from must not be after to");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        Instant start = from == null ? TimeBounds.EARLIEST : from;
        Instant end = to == null ? TimeBounds.LATEST : to;
        PageRequest pageable = PageRequest.of(page, size);
        Page<VitalReading> result = type == null
                ? readings.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(userId, start, end, pageable)
                : readings.findByUserIdAndTypeAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                        userId, type, start, end, pageable);
        return PageResponse.from(result, VitalResponse::from);
    }

    @Transactional(readOnly = true)
    public VitalResponse get(UUID userId, UUID id) {
        return VitalResponse.from(findOwned(userId, id));
    }

    @Transactional
    public VitalResponse update(UUID userId, UUID id, VitalRequest request) {
        VitalReading reading = findOwned(userId, id);
        apply(reading, request);
        return VitalResponse.from(readings.save(reading));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        readings.delete(findOwned(userId, id));
    }

    /** Returns 404 (not 403) when the reading belongs to someone else, so ids cannot be probed. */
    VitalReading findOwned(UUID userId, UUID id) {
        return readings.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reading not found"));
    }

    private void apply(VitalReading reading, VitalRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        VitalRules.Checked checked = null;
        try {
            checked = VitalRules.validate(request.type(), request.value(), request.secondaryValue(), request.unit());
        } catch (FieldValidationException e) {
            errors.putAll(e.getFields());
        }

        String timeError = timeError(request.measuredAt());
        if (timeError != null) {
            errors.put("measuredAt", timeError);
        }
        if (request.note() != null && request.note().length() > MAX_NOTE_LENGTH) {
            errors.put("note", "note can have at most " + MAX_NOTE_LENGTH + " characters");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        reading.setType(request.type());
        reading.setUnit(checked.unit());
        reading.setValuePrimary(checked.primary());
        reading.setValueSecondary(checked.secondary());
        reading.setMeasuredAt(request.measuredAt());
        reading.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        reading.setUpdatedAt(Instant.now());
    }

    private static String timeError(Instant measuredAt) {
        if (measuredAt == null) {
            return "measuredAt is required";
        }
        if (measuredAt.isBefore(TimeBounds.EARLIEST)) {
            return "measuredAt is too far in the past";
        }
        if (measuredAt.isAfter(Instant.now().plus(CLOCK_TOLERANCE))) {
            return "measuredAt cannot be in the future";
        }
        return null;
    }
}