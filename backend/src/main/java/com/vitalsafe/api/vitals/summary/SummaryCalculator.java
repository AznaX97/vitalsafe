package com.vitalsafe.api.vitals.summary;

import com.vitalsafe.api.targets.TargetEvaluator;
import com.vitalsafe.api.targets.TargetRange;
import com.vitalsafe.api.vitals.VitalReading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure statistics over readings that were already loaded and decrypted. No database access. */
public final class SummaryCalculator {

    public enum Bucket { DAY, WEEK, MONTH }

    public record Stat(BigDecimal min, BigDecimal max, BigDecimal average) {}

    public record Latest(String measuredAt, BigDecimal value, BigDecimal secondaryValue) {}

    public record BucketStat(String start, int count, Stat primary, Stat secondary) {}

    /** How many readings fell within, above or below the range the user set. */
    public record TargetBreakdown(BigDecimal lowValue, BigDecimal highValue,
                                  BigDecimal lowSecondaryValue, BigDecimal highSecondaryValue,
                                  int within, int above, int below, int mixed) {}

    public record Result(int count, Stat primary, Stat secondary, Latest latest,
                         TargetBreakdown target, List<BucketStat> buckets) {}

    private SummaryCalculator() {
    }

    public static Result calculate(List<VitalReading> readings, ZoneId zone, Bucket bucket, TargetRange target) {
        if (readings.isEmpty()) {
            return new Result(0, null, null, null, null, List.of());
        }

        VitalReading newest = readings.stream()
                .max(Comparator.comparing(VitalReading::getMeasuredAt))
                .orElseThrow();
        Latest latest = new Latest(newest.getMeasuredAt().toString(), newest.getValuePrimary(), newest.getValueSecondary());

        Map<LocalDate, List<VitalReading>> grouped = new TreeMap<>();
        for (VitalReading reading : readings) {
            grouped.computeIfAbsent(bucketStart(reading.getMeasuredAt(), zone, bucket), key -> new ArrayList<>())
                    .add(reading);
        }
        List<BucketStat> buckets = grouped.entrySet().stream()
                .map(entry -> new BucketStat(
                        entry.getKey().toString(),
                        entry.getValue().size(),
                        stat(primaries(entry.getValue())),
                        stat(secondaries(entry.getValue()))))
                .toList();

        return new Result(readings.size(), stat(primaries(readings)), stat(secondaries(readings)), latest,
                breakdown(readings, target), buckets);
    }

    static LocalDate bucketStart(Instant at, ZoneId zone, Bucket bucket) {
        LocalDate day = at.atZone(zone).toLocalDate();
        return switch (bucket) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    private static List<BigDecimal> primaries(List<VitalReading> readings) {
        return readings.stream().map(VitalReading::getValuePrimary).toList();
    }

    private static List<BigDecimal> secondaries(List<VitalReading> readings) {
        return readings.stream().map(VitalReading::getValueSecondary).filter(value -> value != null).toList();
    }

    private static Stat stat(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal min = values.stream().min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = values.stream().max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
        return new Stat(min, max, average);
    }

    private static TargetBreakdown breakdown(List<VitalReading> readings, TargetRange target) {
        if (target == null) {
            return null;
        }
        int within = 0;
        int above = 0;
        int below = 0;
        int mixed = 0;
        for (VitalReading reading : readings) {
            switch (TargetEvaluator.classify(reading.getValuePrimary(), reading.getValueSecondary(), target)) {
                case WITHIN -> within++;
                case ABOVE -> above++;
                case BELOW -> below++;
                case MIXED -> mixed++;
            }
        }
        return new TargetBreakdown(target.getLowPrimary(), target.getHighPrimary(),
                target.getLowSecondary(), target.getHighSecondary(), within, above, below, mixed);
    }
}