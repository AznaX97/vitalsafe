package com.vitalsafe.api.vitals.dto;

import com.vitalsafe.api.vitals.VitalReading;
import com.vitalsafe.api.vitals.VitalType;

import java.math.BigDecimal;
import java.util.UUID;

/** What the API returns. It never contains the owner's id. Times are ISO-8601 text. */
public record VitalResponse(
        UUID id,
        VitalType type,
        BigDecimal value,
        BigDecimal secondaryValue,
        String unit,
        String measuredAt,
        String note,
        String createdAt,
        String updatedAt
) {
    public static VitalResponse from(VitalReading reading) {
        return new VitalResponse(
                reading.getId(),
                reading.getType(),
                reading.getValuePrimary(),
                reading.getValueSecondary(),
                reading.getUnit(),
                reading.getMeasuredAt().toString(),
                reading.getNote(),
                reading.getCreatedAt().toString(),
                reading.getUpdatedAt().toString());
    }
}