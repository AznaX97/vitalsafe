package com.vitalsafe.api.vitals;

import com.vitalsafe.api.common.FieldValidationException;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catches impossible values (typos, wrong units). These limits are NOT medical judgements:
 * they only stop numbers that cannot be real readings. VitalSafe never labels a value healthy or unhealthy.
 */
public final class VitalRules {

    /** A reading that passed: values with a tidy scale, and the unit that applies. */
    public record Checked(BigDecimal primary, BigDecimal secondary, String unit) {}

    private record Range(BigDecimal min, BigDecimal max) {
        static Range of(String min, String max) {
            return new Range(new BigDecimal(min), new BigDecimal(max));
        }

        boolean contains(BigDecimal value) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
        }

        String describe() {
            return min.toPlainString() + " and " + max.toPlainString();
        }
    }

    private record UnitRule(String unit, Range primary) {}

    private record Spec(boolean wholeNumbers, boolean unitRequired, List<UnitRule> units, Range secondary) {}

    private static final int MAX_DECIMALS = 2;
    private static final Map<VitalType, Spec> SPECS = new EnumMap<>(VitalType.class);

    static {
        SPECS.put(VitalType.BLOOD_PRESSURE, new Spec(true, false,
                List.of(new UnitRule("mm[Hg]", Range.of("50", "300"))), Range.of("30", "200")));
        SPECS.put(VitalType.HEART_RATE, new Spec(true, false,
                List.of(new UnitRule("/min", Range.of("20", "250"))), null));
        SPECS.put(VitalType.BODY_WEIGHT, new Spec(false, false,
                List.of(new UnitRule("kg", Range.of("1", "500"))), null));
        SPECS.put(VitalType.BODY_TEMPERATURE, new Spec(false, false,
                List.of(new UnitRule("Cel", Range.of("30", "45"))), null));
        SPECS.put(VitalType.OXYGEN_SATURATION, new Spec(false, false,
                List.of(new UnitRule("%", Range.of("50", "100"))), null));
        // mg/dL and mmol/L differ by a factor of about 18, so the unit must always be stated
        SPECS.put(VitalType.BLOOD_GLUCOSE, new Spec(false, true,
                List.of(new UnitRule("mg/dL", Range.of("10", "1000")), new UnitRule("mmol/L", Range.of("0.5", "55"))),
                null));
    }

    private VitalRules() {
    }

    public static Checked validate(VitalType type, BigDecimal value, BigDecimal secondaryValue, String unit) {
        Spec spec = SPECS.get(type);
        Map<String, String> errors = new LinkedHashMap<>();
        String allowedUnits = String.join(", ", spec.units().stream().map(UnitRule::unit).toList());

        // ---- unit ----
        UnitRule unitRule = null;
        String wanted = unit == null ? "" : unit.trim();
        if (wanted.isEmpty()) {
            if (spec.unitRequired()) {
                errors.put("unit", "unit is required for " + type + " (one of: " + allowedUnits + ")");
            } else {
                unitRule = spec.units().get(0);
            }
        } else {
            for (UnitRule candidate : spec.units()) {
                if (candidate.unit().equals(wanted)) {   // UCUM is case-sensitive
                    unitRule = candidate;
                }
            }
            if (unitRule == null) {
                errors.put("unit", "unit must be one of: " + allowedUnits);
            }
        }

        // ---- main value ----
        String primaryLabel = type == VitalType.BLOOD_PRESSURE ? "value (systolic)" : "value";
        checkNumber("value", primaryLabel, value, spec.wholeNumbers(),
                unitRule == null ? null : unitRule.primary(), errors);

        // ---- second value (diastolic pressure only) ----
        if (spec.secondary() == null) {
            if (secondaryValue != null) {
                errors.put("secondaryValue", "secondaryValue is not used for " + type);
            }
        } else {
            checkNumber("secondaryValue", "secondaryValue (diastolic)", secondaryValue, true, spec.secondary(), errors);
            if (!errors.containsKey("value") && !errors.containsKey("secondaryValue")
                    && secondaryValue.compareTo(value) >= 0) {
                errors.put("secondaryValue", "secondaryValue (diastolic) must be lower than value (systolic)");
            }
        }

        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return new Checked(tidy(value), secondaryValue == null ? null : tidy(secondaryValue), unitRule.unit());
    }

    private static void checkNumber(String field, String label, BigDecimal number, boolean whole,
                                    Range range, Map<String, String> errors) {
        if (number == null) {
            errors.put(field, label + " is required");
            return;
        }
        int decimals = Math.max(number.stripTrailingZeros().scale(), 0);
        if (whole && decimals > 0) {
            errors.put(field, label + " must be a whole number");
        } else if (decimals > MAX_DECIMALS) {
            errors.put(field, label + " can have at most " + MAX_DECIMALS + " decimal places");
        } else if (range != null && !range.contains(number)) {
            errors.put(field, label + " must be between " + range.describe());
        }
    }

    // 98.60 becomes 98.6 and 100.00 becomes 100, so equal readings are stored the same way
    private static BigDecimal tidy(BigDecimal number) {
        BigDecimal stripped = number.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}