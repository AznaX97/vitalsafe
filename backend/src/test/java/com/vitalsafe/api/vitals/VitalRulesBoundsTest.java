package com.vitalsafe.api.vitals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VitalRulesBoundsTest {

    private final Map<String, String> errors = new LinkedHashMap<>();

    private static BigDecimal n(String text) {
        return new BigDecimal(text);
    }

    @Test
    void theDefaultUnitIsChosenWhenNoneIsGiven() {
        assertThat(VitalRules.resolveUnit(VitalType.HEART_RATE, null, errors)).isEqualTo("/min");
        assertThat(VitalRules.resolveUnit(VitalType.BODY_WEIGHT, "  ", errors)).isEqualTo("kg");
        assertThat(errors).isEmpty();
    }

    @Test
    void glucoseMustStateItsUnit() {
        assertThat(VitalRules.resolveUnit(VitalType.BLOOD_GLUCOSE, null, errors)).isNull();
        assertThat(errors.get("unit")).contains("required");
    }

    @Test
    void anAllowedUnitIsAccepted() {
        assertThat(VitalRules.resolveUnit(VitalType.BLOOD_GLUCOSE, "mmol/L", errors)).isEqualTo("mmol/L");
        assertThat(errors).isEmpty();
    }

    @Test
    void aUnitThatDoesNotBelongIsRefusedAndUnitsAreCaseSensitive() {
        assertThat(VitalRules.resolveUnit(VitalType.HEART_RATE, "bpm", errors)).isNull();
        assertThat(errors.get("unit")).contains("/min");
        errors.clear();
        assertThat(VitalRules.resolveUnit(VitalType.BLOOD_GLUCOSE, "mg/dl", errors)).isNull();
        assertThat(errors.get("unit")).contains("mg/dL");
    }

    @Test
    void aBoundInsideTheLimitsIsAcceptedAndTidied() {
        BigDecimal tidy = VitalRules.checkBound(VitalType.BODY_WEIGHT, "kg", false, n("70.50"), "highValue", errors);

        assertThat(tidy.toPlainString()).isEqualTo("70.5");
        assertThat(errors).isEmpty();
    }

    @Test
    void anImpossibleBoundIsRefusedWithTheFieldName() {
        assertThat(VitalRules.checkBound(VitalType.HEART_RATE, "/min", false, n("5"), "lowValue", errors)).isNull();
        assertThat(errors.get("lowValue")).contains("between 20 and 250");
    }

    @Test
    void theLimitsDependOnTheUnit() {
        assertThat(VitalRules.checkBound(VitalType.BLOOD_GLUCOSE, "mg/dL", false, n("5.5"), "lowValue", errors)).isNull();
        assertThat(errors).containsKey("lowValue");
        errors.clear();
        assertThat(VitalRules.checkBound(VitalType.BLOOD_GLUCOSE, "mmol/L", false, n("5.5"), "lowValue", errors)).isNotNull();
        assertThat(errors).isEmpty();
    }

    @Test
    void wholeNumberTypesRefuseDecimals() {
        assertThat(VitalRules.checkBound(VitalType.HEART_RATE, "/min", false, n("50.5"), "lowValue", errors)).isNull();
        assertThat(errors.get("lowValue")).contains("whole number");
    }

    @Test
    void theSecondValueOnlyExistsForBloodPressure() {
        assertThat(VitalRules.checkBound(VitalType.BLOOD_PRESSURE, "mm[Hg]", true, n("80"), "highSecondaryValue", errors))
                .isEqualByComparingTo("80");
        assertThat(errors).isEmpty();

        assertThat(VitalRules.checkBound(VitalType.HEART_RATE, "/min", true, n("80"), "highSecondaryValue", errors)).isNull();
        assertThat(errors.get("highSecondaryValue")).contains("not used");
    }

    @Test
    void aMissingBoundIsSimplyNotChecked() {
        assertThat(VitalRules.checkBound(VitalType.HEART_RATE, "/min", false, null, "lowValue", errors)).isNull();
        assertThat(errors).isEmpty();
    }
}