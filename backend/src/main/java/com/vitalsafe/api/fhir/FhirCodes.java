package com.vitalsafe.api.fhir;

import com.vitalsafe.api.vitals.VitalType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The standard codes used when a reading becomes a FHIR Observation. LOINC names the measurement, UCUM the unit. */
public final class FhirCodes {

    public static final String LOINC = "http://loinc.org";
    public static final String UCUM = "http://unitsofmeasure.org";
    public static final String CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category";

    public record Code(String code, String display) {}

    public static final Code BLOOD_PRESSURE_PANEL = new Code("85354-9", "Blood pressure panel with all children optional");
    public static final Code SYSTOLIC = new Code("8480-6", "Systolic blood pressure");
    public static final Code DIASTOLIC = new Code("8462-4", "Diastolic blood pressure");
    public static final Code LOINC_HEART_RATE = new Code("8867-4", "Heart rate");
    public static final Code LOINC_BODY_WEIGHT = new Code("29463-7", "Body weight");
    public static final Code LOINC_BODY_TEMPERATURE = new Code("8310-5", "Body temperature");
    public static final Code OXYGEN_ARTERIAL = new Code("2708-6", "Oxygen saturation in Arterial blood");
    public static final Code OXYGEN_PULSE_OXIMETRY = new Code("59408-5", "Oxygen saturation in Arterial blood by Pulse oximetry");
    public static final Code GLUCOSE_MASS = new Code("2339-0", "Glucose [Mass/volume] in Blood");
    public static final Code GLUCOSE_MOLES = new Code("15074-8", "Glucose [Moles/volume] in Blood");

    private static final Map<String, VitalType> TYPE_BY_LOINC = Map.of(
            BLOOD_PRESSURE_PANEL.code(), VitalType.BLOOD_PRESSURE,
            LOINC_HEART_RATE.code(), VitalType.HEART_RATE,
            LOINC_BODY_WEIGHT.code(), VitalType.BODY_WEIGHT,
            LOINC_BODY_TEMPERATURE.code(), VitalType.BODY_TEMPERATURE,
            OXYGEN_ARTERIAL.code(), VitalType.OXYGEN_SATURATION,
            OXYGEN_PULSE_OXIMETRY.code(), VitalType.OXYGEN_SATURATION,
            GLUCOSE_MASS.code(), VitalType.BLOOD_GLUCOSE,
            GLUCOSE_MOLES.code(), VitalType.BLOOD_GLUCOSE);

    private FhirCodes() {
    }

    public static List<Code> codesFor(VitalType type, String unit) {
        return switch (type) {
            case BLOOD_PRESSURE -> List.of(BLOOD_PRESSURE_PANEL);
            case HEART_RATE -> List.of(LOINC_HEART_RATE);
            case BODY_WEIGHT -> List.of(LOINC_BODY_WEIGHT);
            case BODY_TEMPERATURE -> List.of(LOINC_BODY_TEMPERATURE);
            case OXYGEN_SATURATION -> List.of(OXYGEN_ARTERIAL, OXYGEN_PULSE_OXIMETRY);
            case BLOOD_GLUCOSE -> List.of("mmol/L".equals(unit) ? GLUCOSE_MOLES : GLUCOSE_MASS);
        };
    }

    public static Optional<VitalType> typeFromLoinc(String code) {
        return Optional.ofNullable(TYPE_BY_LOINC.get(code));
    }

    /** The glucose unit that a glucose LOINC code is defined for, or null for other codes. */
    public static String glucoseUnitFor(String loincCode) {
        if (GLUCOSE_MASS.code().equals(loincCode)) {
            return "mg/dL";
        }
        if (GLUCOSE_MOLES.code().equals(loincCode)) {
            return "mmol/L";
        }
        return null;
    }

    public static String categoryFor(VitalType type) {
        return type == VitalType.BLOOD_GLUCOSE ? "laboratory" : "vital-signs";
    }

    public static String categoryDisplay(String category) {
        return "laboratory".equals(category) ? "Laboratory" : "Vital Signs";
    }

    public static String displayUnit(String ucum) {
        return switch (ucum) {
            case "mm[Hg]" -> "mmHg";
            case "/min" -> "beats/minute";
            case "Cel" -> "C";
            default -> ucum;
        };
    }
}