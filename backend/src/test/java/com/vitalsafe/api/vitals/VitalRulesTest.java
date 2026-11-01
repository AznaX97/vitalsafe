package com.vitalsafe.api.vitals;

import com.vitalsafe.api.common.FieldValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VitalRulesTest {

    private static BigDecimal n(String text) {
        return text == null ? null : new BigDecimal(text);
    }

    private static VitalRules.Checked accepted(VitalType type, String value, String secondary, String unit) {
        return VitalRules.validate(type, n(value), n(secondary), unit);
    }

    private static Map<String, String> rejected(VitalType type, String value, String secondary, String unit) {
        try {
            VitalRules.validate(type, n(value), n(secondary), unit);
        } catch (FieldValidationException e) {
            return e.getFields();
        }
        throw new AssertionError("Expected the reading to be rejected");
    }

    @Test
    void aNormalBloodPressureIsAcceptedWithTheDefaultUnit() {
        VitalRules.Checked checked = accepted(VitalType.BLOOD_PRESSURE, "120", "80", null);

        assertThat(checked.primary()).isEqualByComparingTo("120");
        assertThat(checked.secondary()).isEqualByComparingTo("80");
        assertThat(checked.unit()).isEqualTo("mm[Hg]");
    }

    @Test
    void bloodPressureNeedsADiastolicValue() {
        assertThat(rejected(VitalType.BLOOD_PRESSURE, "120", null, null).get("secondaryValue"))
                .contains("required");
    }

    @Test
    void diastolicMustBeLowerThanSystolic() {
        assertThat(rejected(VitalType.BLOOD_PRESSURE, "80", "120", null).get("secondaryValue")).contains("lower");
        assertThat(rejected(VitalType.BLOOD_PRESSURE, "100", "100", null).get("secondaryValue")).contains("lower");
    }

    @Test
    void impossibleBloodPressuresAreRejected() {
        assertThat(rejected(VitalType.BLOOD_PRESSURE, "5", "3", null))
                .containsKeys("value", "secondaryValue");
        assertThat(rejected(VitalType.BLOOD_PRESSURE, "400", "80", null).get("value"))
                .contains("between 50 and 300");
    }

    @Test
    void bloodPressureMustBeWholeNumbers() {
        assertThat(rejected(VitalType.BLOOD_PRESSURE, "120.5", "80", null).get("value")).contains("whole number");
    }

    @Test
    void heartRateUsesPerMinuteAndHasNoSecondValue() {
        assertThat(accepted(VitalType.HEART_RATE, "72", null, null).unit()).isEqualTo("/min");
        assertThat(rejected(VitalType.HEART_RATE, "72", "5", null).get("secondaryValue")).contains("not used");
    }

    @Test
    void aUnitThatDoesNotBelongToTheTypeIsRejected() {
        assertThat(rejected(VitalType.HEART_RATE, "72", null, "bpm").get("unit")).contains("/min");
    }

    @Test
    void aMissingValueIsRejected() {
        assertThat(rejected(VitalType.HEART_RATE, null, null, null).get("value")).contains("required");
    }

    @Test
    void weightAllowsTwoDecimalsButNotThree() {
        assertThat(accepted(VitalType.BODY_WEIGHT, "72.55", null, null).primary()).isEqualByComparingTo("72.55");
        assertThat(rejected(VitalType.BODY_WEIGHT, "72.555", null, null).get("value"))
                .contains("at most 2 decimal places");
    }

    @Test
    void trailingZerosAreTidiedSoEqualReadingsAreStoredTheSameWay() {
        assertThat(accepted(VitalType.BODY_TEMPERATURE, "36.60", null, null).primary().toPlainString())
                .isEqualTo("36.6");
        assertThat(accepted(VitalType.OXYGEN_SATURATION, "100.00", null, null).primary().toPlainString())
                .isEqualTo("100");
    }

    @Test
    void aFahrenheitValueTypedAsCelsiusIsCaughtAsATypo() {
        assertThat(accepted(VitalType.BODY_TEMPERATURE, "37.2", null, null).unit()).isEqualTo("Cel");
        assertThat(rejected(VitalType.BODY_TEMPERATURE, "98.6", null, null).get("value"))
                .contains("between 30 and 45");
    }

    @Test
    void oxygenSaturationCannotExceedOneHundredPercent() {
        assertThat(rejected(VitalType.OXYGEN_SATURATION, "101", null, null).get("value")).contains("between 50 and 100");
    }

    @Test
    void glucoseMustStateItsUnit() {
        assertThat(rejected(VitalType.BLOOD_GLUCOSE, "100", null, null).get("unit")).contains("required");
    }

    @Test
    void glucoseRangesDependOnTheUnit() {
        assertThat(accepted(VitalType.BLOOD_GLUCOSE, "100", null, "mg/dL").unit()).isEqualTo("mg/dL");
        assertThat(accepted(VitalType.BLOOD_GLUCOSE, "5.5", null, "mmol/L").unit()).isEqualTo("mmol/L");
        // 100 mmol/L is impossible, and 5.5 mg/dL is impossible: the unit decides which range applies
        assertThat(rejected(VitalType.BLOOD_GLUCOSE, "100", null, "mmol/L")).containsKey("value");
        assertThat(rejected(VitalType.BLOOD_GLUCOSE, "5.5", null, "mg/dL")).containsKey("value");
    }

    @Test
    void unitsAreCaseSensitiveLikeUcum() {
        assertThat(rejected(VitalType.BLOOD_GLUCOSE, "100", null, "mg/dl").get("unit")).contains("mg/dL");
    }

    @Test
    void severalProblemsAreReportedTogether() {
        assertThat(rejected(VitalType.BLOOD_GLUCOSE, "9999", "3", "stones"))
                .containsKeys("unit", "secondaryValue");
    }

    @Test
    void anAbsurdlyLargeExponentIsRejectedQuickly() {
        assertThat(rejected(VitalType.HEART_RATE, "1E+999999999", null, null)).containsKey("value");
    }
}