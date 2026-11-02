package com.vitalsafe.api.fhir;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.vitalsafe.api.vitals.VitalType;
import com.vitalsafe.api.vitals.dto.VitalRequest;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Type;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a FHIR Observation from outside into a reading request. It trusts nothing in it: the subject and
 * id are ignored, units must be UCUM, and the result still goes through the normal reading rules.
 * Error messages are fixed text and never repeat the input.
 */
@Component
public class VitalFhirImporter {

    private static final Pattern HAS_ZONE = Pattern.compile(".*(Z|[+-][0-9]{2}:[0-9]{2})$");

    private record Detected(VitalType type, String loincCode) {}

    public VitalRequest toRequest(Observation observation) {
        checkStatus(observation);
        Detected detected = detect(observation.getCode());
        Instant measuredAt = effective(observation);
        String note = observation.getNote().stream()
                .map(Annotation::getText)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (detected.type() == VitalType.BLOOD_PRESSURE) {
            Quantity systolic = component(observation, FhirCodes.SYSTOLIC.code());
            Quantity diastolic = component(observation, FhirCodes.DIASTOLIC.code());
            if (!Objects.equals(systolic.getCode(), diastolic.getCode())) {
                throw bad("Systolic and diastolic values must use the same unit");
            }
            return new VitalRequest(detected.type(), systolic.getValue(), diastolic.getValue(),
                    systolic.getCode(), measuredAt, note);
        }

        Quantity quantity = quantityOf(observation.getValue());
        if (detected.type() == VitalType.BLOOD_GLUCOSE
                && !Objects.equals(FhirCodes.glucoseUnitFor(detected.loincCode()), quantity.getCode())) {
            throw bad("Glucose code and unit do not match");
        }
        return new VitalRequest(detected.type(), quantity.getValue(), null, quantity.getCode(), measuredAt, note);
    }

    private static void checkStatus(Observation observation) {
        Observation.ObservationStatus status = observation.getStatus();
        boolean usable = status == Observation.ObservationStatus.FINAL
                || status == Observation.ObservationStatus.AMENDED
                || status == Observation.ObservationStatus.CORRECTED;
        if (!usable) {
            throw bad("Only final, amended or corrected observations can be imported (check Observation.status)");
        }
    }

    private static Detected detect(CodeableConcept code) {
        Set<VitalType> types = new HashSet<>();
        String matchedCode = null;
        for (Coding coding : code.getCoding()) {
            if (FhirCodes.LOINC.equals(coding.getSystem()) && coding.getCode() != null) {
                var type = FhirCodes.typeFromLoinc(coding.getCode());
                if (type.isPresent()) {
                    types.add(type.get());
                    matchedCode = coding.getCode();
                }
            }
        }
        if (types.size() != 1) {
            throw bad("Observation.code is not a supported LOINC code");
        }
        return new Detected(types.iterator().next(), matchedCode);
    }

    private static Instant effective(Observation observation) {
        if (!(observation.getEffective() instanceof DateTimeType effective) || effective.getValue() == null) {
            throw bad("Observation.effectiveDateTime is required");
        }
        boolean precise = effective.getPrecision().ordinal() >= TemporalPrecisionEnum.SECOND.ordinal();
        if (!precise || !HAS_ZONE.matcher(effective.getValueAsString()).matches()) {
            throw bad("Observation.effectiveDateTime must include seconds and a time zone");
        }
        return effective.getValue().toInstant();
    }

    private static Quantity component(Observation observation, String loincCode) {
        for (Observation.ObservationComponentComponent component : observation.getComponent()) {
            for (Coding coding : component.getCode().getCoding()) {
                if (FhirCodes.LOINC.equals(coding.getSystem()) && loincCode.equals(coding.getCode())) {
                    if (component.getValue() instanceof Quantity) {
                        return quantityOf(component.getValue());
                    }
                }
            }
        }
        throw bad("Blood pressure needs systolic and diastolic components with numeric values");
    }

    private static Quantity quantityOf(Type value) {
        if (!(value instanceof Quantity quantity)) {
            throw bad("Observation.value must be a Quantity");
        }
        if (quantity.getValue() == null || quantity.getCode() == null) {
            throw bad("A Quantity needs a value and a unit code");
        }
        if (!FhirCodes.UCUM.equals(quantity.getSystem())) {
            throw bad("Quantities must use UCUM units (system " + FhirCodes.UCUM + ")");
        }
        if (quantity.getComparator() != null) {
            throw bad("Quantity comparators such as < or > are not supported");
        }
        return quantity;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}