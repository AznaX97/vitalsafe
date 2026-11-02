package com.vitalsafe.api.fhir;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.vitalsafe.api.vitals.VitalReading;
import com.vitalsafe.api.vitals.VitalType;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/** Turns stored readings into FHIR R4 resources. */
@Component
public class VitalFhirMapper {

    public Observation toObservation(VitalReading reading, String subjectReference) {
        Observation observation = new Observation();
        observation.setId(reading.getId().toString());
        observation.setStatus(Observation.ObservationStatus.FINAL);

        String category = FhirCodes.categoryFor(reading.getType());
        observation.addCategory(new CodeableConcept().addCoding(
                new Coding(FhirCodes.CATEGORY_SYSTEM, category, FhirCodes.categoryDisplay(category))));

        observation.setCode(codeable(FhirCodes.codesFor(reading.getType(), reading.getUnit())));
        observation.setSubject(new Reference(subjectReference));

        // Whole seconds, in UTC and written with a trailing Z
        DateTimeType effective = new DateTimeType(
                Date.from(reading.getMeasuredAt()), TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC"));
        effective.setTimeZoneZulu(true);
        observation.setEffective(effective);

        if (reading.getType() == VitalType.BLOOD_PRESSURE) {
            observation.addComponent()
                    .setCode(codeable(List.of(FhirCodes.SYSTOLIC)))
                    .setValue(quantity(reading.getValuePrimary(), reading.getUnit()));
            observation.addComponent()
                    .setCode(codeable(List.of(FhirCodes.DIASTOLIC)))
                    .setValue(quantity(reading.getValueSecondary(), reading.getUnit()));
        } else {
            observation.setValue(quantity(reading.getValuePrimary(), reading.getUnit()));
        }

        if (reading.getNote() != null) {
            observation.addNote(new Annotation().setText(reading.getNote()));
        }
        return observation;
    }

    /** A collection of the user's readings. The Patient carries only an id: no name, email or date of birth. */
    public Bundle toBundle(UUID userId, List<VitalReading> readings) {
        String patientUrl = "urn:uuid:" + userId;

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.setTimestamp(new Date());

        Patient patient = new Patient();
        patient.setId(userId.toString());
        bundle.addEntry().setFullUrl(patientUrl).setResource(patient);

        for (VitalReading reading : readings) {
            bundle.addEntry()
                    .setFullUrl("urn:uuid:" + reading.getId())
                    .setResource(toObservation(reading, patientUrl));
        }
        return bundle;
    }

    private static CodeableConcept codeable(List<FhirCodes.Code> codes) {
        CodeableConcept concept = new CodeableConcept();
        for (FhirCodes.Code code : codes) {
            concept.addCoding(new Coding(FhirCodes.LOINC, code.code(), code.display()));
        }
        return concept;
    }

    private static Quantity quantity(BigDecimal value, String ucum) {
        return new Quantity()
                .setValue(value)
                .setUnit(FhirCodes.displayUnit(ucum))
                .setSystem(FhirCodes.UCUM)
                .setCode(ucum);
    }
}