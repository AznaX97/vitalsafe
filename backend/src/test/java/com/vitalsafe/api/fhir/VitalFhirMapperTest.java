package com.vitalsafe.api.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.vitalsafe.api.vitals.VitalReading;
import com.vitalsafe.api.vitals.VitalType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VitalFhirMapperTest {

    private static final Instant AT = Instant.parse("2026-01-15T08:00:00Z");

    private final VitalFhirMapper mapper = new VitalFhirMapper();

    static VitalReading reading(VitalType type, String unit, String value, String secondary, String note) {
        VitalReading reading = new VitalReading();
        reading.setId(UUID.randomUUID());
        reading.setUserId(UUID.randomUUID());
        reading.setType(type);
        reading.setUnit(unit);
        reading.setMeasuredAt(AT);
        reading.setValuePrimary(new BigDecimal(value));
        reading.setValueSecondary(secondary == null ? null : new BigDecimal(secondary));
        reading.setNote(note);
        return reading;
    }

    private Observation observationOf(VitalReading reading) {
        return mapper.toObservation(reading, "Patient/" + reading.getUserId());
    }

    @Test
    void aHeartRateBecomesAFinalVitalSignsObservation() {
        VitalReading reading = reading(VitalType.HEART_RATE, "/min", "72", null, null);

        Observation observation = observationOf(reading);

        assertThat(observation.getId()).endsWith(reading.getId().toString());
        assertThat(observation.getStatus()).isEqualTo(Observation.ObservationStatus.FINAL);
        assertThat(observation.getCategoryFirstRep().getCodingFirstRep().getCode()).isEqualTo("vital-signs");
        assertThat(observation.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://loinc.org");
        assertThat(observation.getCode().getCodingFirstRep().getCode()).isEqualTo("8867-4");
        assertThat(observation.getValueQuantity().getValue()).isEqualByComparingTo("72");
        assertThat(observation.getValueQuantity().getSystem()).isEqualTo("http://unitsofmeasure.org");
        assertThat(observation.getValueQuantity().getCode()).isEqualTo("/min");
        assertThat(observation.getValueQuantity().getUnit()).isEqualTo("beats/minute");
        assertThat(observation.getSubject().getReference()).isEqualTo("Patient/" + reading.getUserId());
    }

    @Test
    void theTimeIsWrittenInUtcWithAZ() {
        Observation observation = observationOf(reading(VitalType.HEART_RATE, "/min", "72", null, null));

        assertThat(observation.getEffectiveDateTimeType().getValueAsString())
                .startsWith("2026-01-15T08:00:00")
                .endsWith("Z");
    }

    @Test
    void aBloodPressureIsAPanelWithSystolicAndDiastolicComponents() {
        Observation observation = observationOf(reading(VitalType.BLOOD_PRESSURE, "mm[Hg]", "120", "80", null));

        assertThat(observation.getCode().getCodingFirstRep().getCode()).isEqualTo("85354-9");
        assertThat(observation.hasValue()).isFalse();
        assertThat(observation.getComponent()).hasSize(2);
        assertThat(observation.getComponent().get(0).getCode().getCodingFirstRep().getCode()).isEqualTo("8480-6");
        assertThat(observation.getComponent().get(0).getValueQuantity().getValue()).isEqualByComparingTo("120");
        assertThat(observation.getComponent().get(1).getCode().getCodingFirstRep().getCode()).isEqualTo("8462-4");
        assertThat(observation.getComponent().get(1).getValueQuantity().getValue()).isEqualByComparingTo("80");
        assertThat(observation.getComponent().get(1).getValueQuantity().getCode()).isEqualTo("mm[Hg]");
    }

    @Test
    void oxygenSaturationCarriesBothCommonLoincCodes() {
        Observation observation = observationOf(reading(VitalType.OXYGEN_SATURATION, "%", "98", null, null));

        assertThat(observation.getCode().getCoding()).extracting(c -> c.getCode())
                .containsExactly("2708-6", "59408-5");
        assertThat(observation.getValueQuantity().getCode()).isEqualTo("%");
    }

    @Test
    void glucoseUsesTheCodeThatMatchesItsUnitAndIsALaboratoryObservation() {
        Observation mass = observationOf(reading(VitalType.BLOOD_GLUCOSE, "mg/dL", "100", null, null));
        Observation moles = observationOf(reading(VitalType.BLOOD_GLUCOSE, "mmol/L", "5.5", null, null));

        assertThat(mass.getCode().getCodingFirstRep().getCode()).isEqualTo("2339-0");
        assertThat(moles.getCode().getCodingFirstRep().getCode()).isEqualTo("15074-8");
        assertThat(mass.getCategoryFirstRep().getCodingFirstRep().getCode()).isEqualTo("laboratory");
    }

    @Test
    void aNoteBecomesAnAnnotation() {
        Observation withNote = observationOf(reading(VitalType.HEART_RATE, "/min", "72", null, "after a walk"));
        Observation withoutNote = observationOf(reading(VitalType.HEART_RATE, "/min", "72", null, null));

        assertThat(withNote.getNoteFirstRep().getText()).isEqualTo("after a walk");
        assertThat(withoutNote.hasNote()).isFalse();
    }

    @Test
    void theEncodedJsonCarriesTheStandardCodes() {
        Observation observation = observationOf(reading(VitalType.HEART_RATE, "/min", "72", null, null));

        String json = FhirContext.forR4Cached().newJsonParser().encodeResourceToString(observation);

        assertThat(json).contains("\"resourceType\":\"Observation\"")
                .contains("\"system\":\"http://loinc.org\"")
                .contains("\"code\":\"8867-4\"")
                .contains("\"system\":\"http://unitsofmeasure.org\"")
                .contains("\"code\":\"/min\"");
    }

    @Test
    void aBundleHoldsAPatientWithOnlyAnIdAndTheObservations() {
        UUID userId = UUID.randomUUID();
        VitalReading first = reading(VitalType.HEART_RATE, "/min", "72", null, null);
        VitalReading second = reading(VitalType.BODY_WEIGHT, "kg", "70", null, null);

        Bundle bundle = mapper.toBundle(userId, List.of(first, second));

        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
        assertThat(bundle.getEntry()).hasSize(3);
        assertThat(bundle.getEntry().get(0).getResource()).isInstanceOf(Patient.class);
        Patient patient = (Patient) bundle.getEntry().get(0).getResource();
        assertThat(patient.getIdElement().getIdPart()).isEqualTo(userId.toString());
        assertThat(patient.hasName()).isFalse();
        assertThat(patient.hasTelecom()).isFalse();
        Observation observation = (Observation) bundle.getEntry().get(1).getResource();
        assertThat(observation.getSubject().getReference()).isEqualTo("urn:uuid:" + userId);
        assertThat(bundle.getEntry().get(1).getFullUrl()).isEqualTo("urn:uuid:" + first.getId());
    }
}