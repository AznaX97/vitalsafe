package com.vitalsafe.api.targets;

import com.vitalsafe.api.common.FieldValidationException;
import com.vitalsafe.api.targets.dto.TargetRequest;
import com.vitalsafe.api.targets.dto.TargetResponse;
import com.vitalsafe.api.vitals.VitalRules;
import com.vitalsafe.api.vitals.VitalType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TargetService {

    private final TargetRangeRepository ranges;

    public TargetService(TargetRangeRepository ranges) {
        this.ranges = ranges;
    }

    @Transactional(readOnly = true)
    public List<TargetResponse> list(UUID userId) {
        return ranges.findByUserIdOrderByTypeAscUnitAsc(userId).stream().map(TargetResponse::from).toList();
    }

    /** Creates the range, or replaces the one for this type and unit. Bounds left out are cleared. */
    @Transactional
    public TargetResponse upsert(UUID userId, VitalType type, TargetRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        String unit = VitalRules.resolveUnit(type, request.unit(), errors);

        BigDecimal low = null;
        BigDecimal high = null;
        BigDecimal lowSecondary = null;
        BigDecimal highSecondary = null;
        if (unit != null) {
            low = VitalRules.checkBound(type, unit, false, request.lowValue(), "lowValue", errors);
            high = VitalRules.checkBound(type, unit, false, request.highValue(), "highValue", errors);
            lowSecondary = VitalRules.checkBound(type, unit, true, request.lowSecondaryValue(), "lowSecondaryValue", errors);
            highSecondary = VitalRules.checkBound(type, unit, true, request.highSecondaryValue(), "highSecondaryValue", errors);
        }

        boolean anyBound = request.lowValue() != null || request.highValue() != null
                || request.lowSecondaryValue() != null || request.highSecondaryValue() != null;
        if (!anyBound) {
            errors.put("lowValue", "Set at least one bound (lowValue, highValue, lowSecondaryValue or highSecondaryValue)");
        }
        if (low != null && high != null && low.compareTo(high) > 0) {
            errors.put("lowValue", "lowValue must not be higher than highValue");
        }
        if (lowSecondary != null && highSecondary != null && lowSecondary.compareTo(highSecondary) > 0) {
            errors.put("lowSecondaryValue", "lowSecondaryValue must not be higher than highSecondaryValue");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        TargetRange range = ranges.findByUserIdAndTypeAndUnit(userId, type, unit).orElseGet(() -> {
            TargetRange created = new TargetRange();
            created.setUserId(userId);
            created.setType(type);
            created.setUnit(unit);
            return created;
        });
        range.setLowPrimary(low);
        range.setHighPrimary(high);
        range.setLowSecondary(lowSecondary);
        range.setHighSecondary(highSecondary);
        range.setUpdatedAt(Instant.now());

        try {
            return TargetResponse.from(ranges.saveAndFlush(range));
        } catch (DataIntegrityViolationException e) {
            // Two requests creating the same range at the same moment: the database picks one winner
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The range was changed at the same time. Try again.");
        }
    }

    @Transactional
    public void delete(UUID userId, VitalType type, String unit) {
        Map<String, String> errors = new LinkedHashMap<>();
        String resolved = VitalRules.resolveUnit(type, unit, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        TargetRange range = ranges.findByUserIdAndTypeAndUnit(userId, type, resolved)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No target range is set"));
        ranges.delete(range);
    }
}