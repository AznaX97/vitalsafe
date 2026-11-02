package com.vitalsafe.api.targets.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Any bound may be left out. Leaving one out clears it. */
public record TargetRequest(
        @Size(max = 16, message = "unit is too long") String unit,
        BigDecimal lowValue,
        BigDecimal highValue,
        BigDecimal lowSecondaryValue,
        BigDecimal highSecondaryValue
) {}