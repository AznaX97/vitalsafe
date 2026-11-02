package com.vitalsafe.api.vitals.summary;

import com.vitalsafe.api.common.FieldValidationException;
import com.vitalsafe.api.targets.TargetRange;
import com.vitalsafe.api.targets.TargetRangeRepository;
import com.vitalsafe.api.vitals.VitalReading;
import com.vitalsafe.api.vitals.VitalReadingRepository;
import com.vitalsafe.api.vitals.VitalType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The values are encrypted, so the database cannot add them up. Readings are loaded, decrypted and summed
 * here, and the number loaded per request is capped.
 */
@Service
public class VitalSummaryService {

    private static final Duration DEFAULT_RANGE = Duration.ofDays(30);
    private static final String DISCLAIMER =
            "Statistics of the readings you logged. Not medical advice and not a diagnosis.";

    private final VitalReadingRepository readings;
    private final TargetRangeRepository targets;
    private final int maxReadings;

    public VitalSummaryService(VitalReadingRepository readings,
                               TargetRangeRepository targets,
                               @Value("${app.summary.max-readings:10000}") int maxReadings) {
        this.readings = readings;
        this.targets = targets;
        this.maxReadings = maxReadings;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summarize(UUID userId, VitalType type, String unit, Instant from, Instant to,
                                     String zone, SummaryCalculator.Bucket bucket) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (type == null) {
            errors.put("type", "type is required");
        }
        ZoneId zoneId = parseZone(zone);
        if (zoneId == null) {
            errors.put("zone", "zone must be a valid time zone id such as UTC or Asia/Colombo");
        }
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_RANGE);
        if (start.isAfter(end)) {
            errors.put("from", "from must not be after to");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        List<VitalReading> found = readings.findByUserIdAndTypeAndMeasuredAtBetweenOrderByMeasuredAtAscIdAsc(
                userId, type, start, end, PageRequest.of(0, maxReadings + 1));
        if (found.size() > maxReadings) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(413),
                    "Too many readings in this range. Use a shorter range.");
        }

        String wanted = unit == null || unit.isBlank() ? null : unit.trim();
        List<VitalReading> chosen = wanted == null
                ? found
                : found.stream().filter(reading -> reading.getUnit().equals(wanted)).toList();

        // Averaging mg/dL together with mmol/L would be meaningless, so mixed units are refused
        Set<String> units = chosen.stream().map(VitalReading::getUnit).collect(Collectors.toCollection(TreeSet::new));
        if (units.size() > 1) {
            throw new FieldValidationException(Map.of("unit",
                    "Readings use more than one unit (" + String.join(", ", units)
                            + "). Choose one with the unit parameter."));
        }
        String resolvedUnit = units.isEmpty() ? wanted : units.iterator().next();

        TargetRange target = resolvedUnit == null
                ? null
                : targets.findByUserIdAndTypeAndUnit(userId, type, resolvedUnit).orElse(null);

        SummaryCalculator.Result result = SummaryCalculator.calculate(chosen, zoneId, bucket, target);
        return new SummaryResponse(type, resolvedUnit, start.toString(), end.toString(), zoneId.getId(), bucket,
                result.count(), result.primary(), result.secondary(), result.latest(), result.target(),
                result.buckets(), DISCLAIMER);
    }

    private static ZoneId parseZone(String zone) {
        if (zone == null || zone.isBlank() || zone.length() > 40) {
            return null;
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            return null;
        }
    }
}