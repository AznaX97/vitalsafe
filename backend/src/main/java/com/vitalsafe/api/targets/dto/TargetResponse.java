package com.vitalsafe.api.targets.dto;

import com.vitalsafe.api.targets.TargetRange;
import com.vitalsafe.api.vitals.VitalType;

import java.math.BigDecimal;

public record TargetResponse(
        VitalType type,
        String unit,
        BigDecimal lowValue,
        BigDecimal highValue,
        BigDecimal lowSecondaryValue,
        BigDecimal highSecondaryValue,
        String updatedAt
) {
    public static TargetResponse from(TargetRange range) {
        return new TargetResponse(
                range.getType(),
                range.getUnit(),
                range.getLowPrimary(),
                range.getHighPrimary(),
                range.getLowSecondary(),
                range.getHighSecondary(),
                range.getUpdatedAt().toString());
    }
}