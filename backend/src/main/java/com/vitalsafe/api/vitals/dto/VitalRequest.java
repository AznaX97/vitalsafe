package com.vitalsafe.api.vitals.dto;

import com.vitalsafe.api.vitals.VitalType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record VitalRequest(
        @NotNull(message = "type is required") VitalType type,
        BigDecimal value,
        BigDecimal secondaryValue,
        @Size(max = 16, message = "unit is too long") String unit,
        @NotNull(message = "measuredAt is required") Instant measuredAt,
        @Size(max = 1000, message = "note can have at most 1000 characters") String note
) {}