package com.vitalsafe.api.vitals.summary;

import com.vitalsafe.api.vitals.VitalType;

import java.util.List;

public record SummaryResponse(
        VitalType type,
        String unit,
        String from,
        String to,
        String zone,
        SummaryCalculator.Bucket bucket,
        int count,
        SummaryCalculator.Stat primary,
        SummaryCalculator.Stat secondary,
        SummaryCalculator.Latest latest,
        SummaryCalculator.TargetBreakdown target,
        List<SummaryCalculator.BucketStat> buckets,
        String disclaimer
) {}