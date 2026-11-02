package com.vitalsafe.api.vitals.summary;

import com.vitalsafe.api.targets.TargetRange;
import com.vitalsafe.api.vitals.VitalReading;
import com.vitalsafe.api.vitals.VitalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryCalculatorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");   // UTC+05:30, no daylight saving

    private static VitalReading reading(String at, String value, String secondary) {
        VitalReading reading = new VitalReading();
        reading.setId(UUID.randomUUID());
        reading.setType(VitalType.HEART_RATE);
        reading.setUnit("/min");
        reading.setMeasuredAt(Instant.parse(at));
        reading.setValuePrimary(new BigDecimal(value));
        reading.setValueSecondary(secondary == null ? null : new BigDecimal(secondary));
        return reading;
    }

    private static List<String> starts(SummaryCalculator.Result result) {
        return result.buckets().stream().map(SummaryCalculator.BucketStat::start).toList();
    }

    @Test
    void noReadingsGivesAnEmptyResult() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(), UTC, SummaryCalculator.Bucket.DAY, null);

        assertThat(result.count()).isZero();
        assertThat(result.primary()).isNull();
        assertThat(result.secondary()).isNull();
        assertThat(result.latest()).isNull();
        assertThat(result.target()).isNull();
        assertThat(result.buckets()).isEmpty();
    }

    @Test
    void minMaxAndAverageAreCalculated() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(
                reading("2026-01-12T08:00:00Z", "60", null),
                reading("2026-01-13T08:00:00Z", "70", null),
                reading("2026-01-14T08:00:00Z", "80", null)), UTC, SummaryCalculator.Bucket.DAY, null);

        assertThat(result.count()).isEqualTo(3);
        assertThat(result.primary().min()).isEqualByComparingTo("60");
        assertThat(result.primary().max()).isEqualByComparingTo("80");
        assertThat(result.primary().average()).isEqualByComparingTo("70");
        assertThat(result.secondary()).isNull();
    }

    @Test
    void theAverageIsRoundedToOneDecimalPlaceHalfUp() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(
                reading("2026-01-12T08:00:00Z", "72.5", null),
                reading("2026-01-13T08:00:00Z", "72.6", null)), UTC, SummaryCalculator.Bucket.DAY, null);

        assertThat(result.primary().average()).isEqualByComparingTo("72.6");
    }

    @Test
    void theSecondValueHasItsOwnStatistics() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(
                reading("2026-01-12T08:00:00Z", "120", "80"),
                reading("2026-01-13T08:00:00Z", "140", "90")), UTC, SummaryCalculator.Bucket.DAY, null);

        assertThat(result.primary().average()).isEqualByComparingTo("130");
        assertThat(result.secondary().min()).isEqualByComparingTo("80");
        assertThat(result.secondary().max()).isEqualByComparingTo("90");
        assertThat(result.secondary().average()).isEqualByComparingTo("85");
    }

    @Test
    void theLatestReadingIsTheNewestEvenWhenTheListIsOutOfOrder() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(
                reading("2026-01-14T08:00:00Z", "80", null),
                reading("2026-01-12T08:00:00Z", "60", null),
                reading("2026-01-13T08:00:00Z", "70", null)), UTC, SummaryCalculator.Bucket.DAY, null);

        assertThat(result.latest().measuredAt()).isEqualTo("2026-01-14T08:00:00Z");
        assertThat(result.latest().value()).isEqualByComparingTo("80");
    }

    @Test
    void dayBucketsAreSortedAndGroupReadingsOfTheSameDay() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(
                reading("2026-01-14T20:00:00Z", "80", null),
                reading("2026-01-12T08:00:00Z", "60", null),
                reading("2026-01-12T18:00:00Z", "70", null)), UTC, SummaryCalculator.Bucket.DAY, null);

        assertThat(starts(result)).containsExactly("2026-01-12", "2026-01-14");
        assertThat(result.buckets().get(0).count()).isEqualTo(2);
        assertThat(result.buckets().get(0).primary().average()).isEqualByComparingTo("65");
    }

    @Test
    void theTimeZoneDecidesWhichDayAReadingBelongsTo() {
        List<VitalReading> readings = List.of(
                reading("2026-01-14T10:00:00Z", "60", null),
                reading("2026-01-14T20:00:00Z", "70", null));   // 01:30 on 15 January in Colombo

        assertThat(starts(SummaryCalculator.calculate(readings, UTC, SummaryCalculator.Bucket.DAY, null)))
                .containsExactly("2026-01-14");
        assertThat(starts(SummaryCalculator.calculate(readings, COLOMBO, SummaryCalculator.Bucket.DAY, null)))
                .containsExactly("2026-01-14", "2026-01-15");
    }

    @Test
    void weeksStartOnMonday() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(
                reading("2026-01-12T08:00:00Z", "60", null),    // Monday
                reading("2026-01-14T08:00:00Z", "70", null),    // Wednesday
                reading("2026-01-18T08:00:00Z", "80", null),    // Sunday
                reading("2026-01-19T08:00:00Z", "90", null)),   // next Monday
                UTC, SummaryCalculator.Bucket.WEEK, null);

        assertThat(starts(result)).containsExactly("2026-01-12", "2026-01-19");
        assertThat(result.buckets().get(0).count()).isEqualTo(3);
        assertThat(result.buckets().get(1).count()).isEqualTo(1);
    }

    @Test
    void monthsStartOnTheFirstInTheChosenTimeZone() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(
                reading("2026-01-31T08:00:00Z", "60", null),
                reading("2026-01-31T20:00:00Z", "70", null)),   // already 1 February in Colombo
                COLOMBO, SummaryCalculator.Bucket.MONTH, null);

        assertThat(starts(result)).containsExactly("2026-01-01", "2026-02-01");
    }

    @Test
    void readingsAreCountedAgainstTheRangeTheUserSet() {
        TargetRange range = new TargetRange();
        range.setLowPrimary(new BigDecimal("50"));
        range.setHighPrimary(new BigDecimal("100"));

        SummaryCalculator.Result result = SummaryCalculator.calculate(List.of(
                reading("2026-01-12T08:00:00Z", "40", null),
                reading("2026-01-13T08:00:00Z", "70", null),
                reading("2026-01-14T08:00:00Z", "120", null),
                reading("2026-01-15T08:00:00Z", "100", null)), UTC, SummaryCalculator.Bucket.DAY, range);

        SummaryCalculator.TargetBreakdown target = result.target();
        assertThat(target.within()).isEqualTo(2);
        assertThat(target.above()).isEqualTo(1);
        assertThat(target.below()).isEqualTo(1);
        assertThat(target.mixed()).isZero();
        assertThat(target.lowValue()).isEqualByComparingTo("50");
        assertThat(target.highValue()).isEqualByComparingTo("100");
    }

    @Test
    void withoutARangeThereIsNoBreakdown() {
        SummaryCalculator.Result result = SummaryCalculator.calculate(
                List.of(reading("2026-01-12T08:00:00Z", "60", null)), UTC, SummaryCalculator.Bucket.DAY, null);

        assertThat(result.target()).isNull();
    }
}