package com.vitalsafe.api.fhir;

import com.vitalsafe.api.vitals.VitalReading;
import com.vitalsafe.api.vitals.VitalType;
import com.vitalsafe.api.vitals.dto.VitalRequest;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VitalFhirImporterTest {

    private final VitalFhirMapper mapper = new VitalFhirMapper();
    private final VitalFhirImporter importer = new VitalFhirImporter();

    private Observation observationOf(VitalReading reading) {
        return mapper.toObservation(reading, "Patient/" + reading.getUserId());
    }

    private static String rejectionOf(VitalFhirImporter importer, Observation observation) {
        try {
            importer.toRequest(observation);
        } catch (ResponseStatusException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            return e.getReason();
        }
        throw new AssertionError("Expected the Observation to be rejected");
    }

    @Test
    void everyKindOfReadingSurvivesAnExportAndImportRoundTrip() {
        List<VitalReading> readings = List.of(
                VitalFhirMapperTest.reading(VitalType.BLOOD_PRESSURE, "mm[Hg]", "120", "80", "after a walk"),
                VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null),
                VitalFhirMapperTest.reading(VitalType.BODY_WEIGHT, "kg", "72.5", null, null),
                VitalFhirMapperTest.reading(VitalType.BODY_TEMPERATURE, "Cel", "36.6", null, null),
                VitalFhirMapperTest.reading(VitalType.OXYGEN_SATURATION, "%", "98", null, null),
                VitalFhirMapperTest.reading(VitalType.BLOOD_GLUCOSE, "mg/dL", "100", null, null),
                VitalFhirMapperTest.reading(VitalType.BLOOD_GLUCOSE, "mmol/L", "5.5", null, null));

        for (VitalReading reading : readings) {
            VitalRequest request = importer.toRequest(observationOf(reading));

            assertThat(request.type()).isEqualTo(reading.getType());
            assertThat(request.value()).isEqualByComparingTo(reading.getValuePrimary());
            if (reading.getValueSecondary() == null) {
                assertThat(request.secondaryValue()).isNull();
            } else {
                assertThat(request.secondaryValue()).isEqualByComparingTo(reading.getValueSecondary());
            }
            assertThat(request.unit()).isEqualTo(reading.getUnit());
            assertThat(request.measuredAt()).isEqualTo(Instant.parse("2026-01-15T08:00:00Z"));
            assertThat(request.note()).isEqualTo(reading.getNote());
        }
    }

    @Test
    void anObservationThatIsNotFinalIsRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.setStatus(Observation.ObservationStatus.ENTEREDINERROR);

        assertThat(rejectionOf(importer, observation)).contains("status");
    }

    @Test
    void anUnknownCodeIsRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.setCode(new CodeableConcept().addCoding(new Coding("http://loinc.org", "99999-9", "Something else")));

        assertThat(rejectionOf(importer, observation)).contains("supported LOINC");
    }

    @Test
    void aCodeFromAnotherSystemIsNotTrusted() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.setCode(new CodeableConcept().addCoding(new Coding("http://example.org/codes", "8867-4", "Heart rate")));

        assertThat(rejectionOf(importer, observation)).contains("supported LOINC");
    }

    @Test
    void codesForDifferentKindsOfReadingAreRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.getCode().addCoding(new Coding("http://loinc.org", "29463-7", "Body weight"));

        assertThat(rejectionOf(importer, observation)).contains("supported LOINC");
    }

    @Test
    void unitsThatAreNotUcumAreRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.getValueQuantity().setSystem("http://example.org/units");

        assertThat(rejectionOf(importer, observation)).contains("UCUM");
    }

    @Test
    void aMissingValueIsRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.setValue(null);

        assertThat(rejectionOf(importer, observation)).contains("Quantity");
    }

    @Test
    void aComparatorSuchAsLessThanIsRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.getValueQuantity().setComparator(Quantity.QuantityComparator.LESS_THAN);

        assertThat(rejectionOf(importer, observation)).contains("comparators");
    }

    @Test
    void aDateWithoutATimeIsRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.setEffective(new DateTimeType("2026-01-15"));

        assertThat(rejectionOf(importer, observation)).contains("seconds and a time zone");
    }

    @Test
    void aTimeWithoutATimeZoneIsRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.setEffective(new DateTimeType("2026-01-15T08:00:00"));

        assertThat(rejectionOf(importer, observation)).contains("seconds and a time zone");
    }

    @Test
    void aTimeWithAnOffsetIsConvertedToTheSameInstant() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, null));
        observation.setEffective(new DateTimeType("2026-01-15T13:30:00+05:30"));

        assertThat(importer.toRequest(observation).measuredAt()).isEqualTo(Instant.parse("2026-01-15T08:00:00Z"));
    }

    @Test
    void aBloodPressureWithoutADiastolicComponentIsRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.BLOOD_PRESSURE, "mm[Hg]", "120", "80", null));
        observation.getComponent().remove(1);

        assertThat(rejectionOf(importer, observation)).contains("systolic and diastolic");
    }

    @Test
    void aGlucoseCodeThatDoesNotMatchItsUnitIsRefused() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.BLOOD_GLUCOSE, "mg/dL", "100", null, null));
        observation.getValueQuantity().setCode("mmol/L");

        assertThat(rejectionOf(importer, observation)).contains("do not match");
    }

    @Test
    void theErrorMessagesNeverRepeatTheInput() {
        Observation observation = observationOf(VitalFhirMapperTest.reading(VitalType.HEART_RATE, "/min", "72", null, "secret-note-text"));
        observation.getValueQuantity().setSystem("http://secret.example.org/units");

        assertThat(rejectionOf(importer, observation)).doesNotContain("secret");
    }
}