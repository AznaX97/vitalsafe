package com.vitalsafe.api.targets;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TargetEvaluatorTest {

    private static BigDecimal n(String text) {
        return text == null ? null : new BigDecimal(text);
    }

    private static TargetRange range(String low, String high, String lowSecondary, String highSecondary) {
        TargetRange range = new TargetRange();
        range.setLowPrimary(n(low));
        range.setHighPrimary(n(high));
        range.setLowSecondary(n(lowSecondary));
        range.setHighSecondary(n(highSecondary));
        return range;
    }

    private static TargetEvaluator.Status classify(String primary, String secondary, TargetRange range) {
        return TargetEvaluator.classify(n(primary), n(secondary), range);
    }

    @Test
    void aValueInsideTheRangeIsWithin() {
        assertThat(classify("70", null, range("50", "100", null, null))).isEqualTo(TargetEvaluator.Status.WITHIN);
    }

    @Test
    void valuesExactlyOnABoundAreWithin() {
        TargetRange range = range("50", "100", null, null);

        assertThat(classify("50", null, range)).isEqualTo(TargetEvaluator.Status.WITHIN);
        assertThat(classify("100", null, range)).isEqualTo(TargetEvaluator.Status.WITHIN);
    }

    @Test
    void aboveAndBelowAreReported() {
        TargetRange range = range("50", "100", null, null);

        assertThat(classify("101", null, range)).isEqualTo(TargetEvaluator.Status.ABOVE);
        assertThat(classify("49", null, range)).isEqualTo(TargetEvaluator.Status.BELOW);
    }

    @Test
    void aOneSidedRangeOnlyChecksThatSide() {
        assertThat(classify("500", null, range("50", null, null, null))).isEqualTo(TargetEvaluator.Status.WITHIN);
        assertThat(classify("1", null, range(null, "100", null, null))).isEqualTo(TargetEvaluator.Status.WITHIN);
        assertThat(classify("120", null, range(null, "100", null, null))).isEqualTo(TargetEvaluator.Status.ABOVE);
    }

    @Test
    void theSecondValueIsCheckedWhenThereIsOne() {
        TargetRange range = range("90", "130", "60", "85");

        assertThat(classify("120", "80", range)).isEqualTo(TargetEvaluator.Status.WITHIN);
        assertThat(classify("120", "95", range)).isEqualTo(TargetEvaluator.Status.ABOVE);
        assertThat(classify("120", "55", range)).isEqualTo(TargetEvaluator.Status.BELOW);
    }

    @Test
    void partsOnOppositeSidesAreMixed() {
        assertThat(classify("150", "50", range("90", "130", "60", "85"))).isEqualTo(TargetEvaluator.Status.MIXED);
    }

    @Test
    void aSecondaryValueIsIgnoredWhenNoSecondaryBoundIsSet() {
        assertThat(classify("120", "200", range("90", "130", null, null))).isEqualTo(TargetEvaluator.Status.WITHIN);
    }
}