package com.vitalsafe.api.vitals;

import com.vitalsafe.api.support.IntegrationTestBase;
import com.vitalsafe.api.user.User;
import com.vitalsafe.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VitalReadingRepositoryTest extends IntegrationTestBase {

    private static final Instant EARLIEST = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant LATEST = Instant.parse("2100-01-01T00:00:00Z");
    private static final Instant BASE = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired
    private VitalReadingRepository readings;

    @Autowired
    private UserRepository users;

    private User newUser() {
        User user = new User();
        user.setEmail("vitals-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$12$" + "x".repeat(53));
        user.setConsentedAt(Instant.now());
        user.setConsentVersion("2026-11-01");
        return users.saveAndFlush(user);
    }

    private VitalReading bloodPressure(UUID userId, Instant at, String systolic, String diastolic, String note) {
        VitalReading reading = new VitalReading();
        reading.setUserId(userId);
        reading.setType(VitalType.BLOOD_PRESSURE);
        reading.setUnit("mm[Hg]");
        reading.setMeasuredAt(at);
        reading.setValuePrimary(new BigDecimal(systolic));
        reading.setValueSecondary(new BigDecimal(diastolic));
        reading.setNote(note);
        return reading;
    }

    private VitalReading heartRate(UUID userId, Instant at, String bpm) {
        VitalReading reading = new VitalReading();
        reading.setUserId(userId);
        reading.setType(VitalType.HEART_RATE);
        reading.setUnit("/min");
        reading.setMeasuredAt(at);
        reading.setValuePrimary(new BigDecimal(bpm));
        return reading;
    }

    @Test
    void savesAReadingAndReadsItBackDecrypted() {
        User owner = newUser();
        VitalReading saved = readings.saveAndFlush(bloodPressure(owner.getId(), BASE, "120", "80", "after a walk"));

        // A fresh load goes to the database and through the decrypting converters
        VitalReading loaded = readings.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getType()).isEqualTo(VitalType.BLOOD_PRESSURE);
        assertThat(loaded.getUnit()).isEqualTo("mm[Hg]");
        assertThat(loaded.getMeasuredAt()).isEqualTo(BASE);
        assertThat(loaded.getValuePrimary()).isEqualByComparingTo("120");
        assertThat(loaded.getValueSecondary()).isEqualByComparingTo("80");
        assertThat(loaded.getNote()).isEqualTo("after a walk");
    }

    @Test
    void valuesAndNoteAreEncryptedInTheDatabase() {
        User owner = newUser();
        VitalReading saved = readings.saveAndFlush(bloodPressure(owner.getId(), BASE, "120", "80", "private note text"));

        Map<String, Object> row = jdbc.queryForMap(
                "select value_primary, value_secondary, note from vital_readings where id = ?", saved.getId());

        assertThat((String) row.get("value_primary")).startsWith("v1.").isNotEqualTo("120");
        assertThat((String) row.get("value_secondary")).startsWith("v1.").isNotEqualTo("80");
        assertThat((String) row.get("note")).startsWith("v1.").doesNotContain("private note");
    }

    @Test
    void theSameValueIsStoredAsDifferentCiphertext() {
        User owner = newUser();
        VitalReading first = readings.saveAndFlush(heartRate(owner.getId(), BASE, "72"));
        VitalReading second = readings.saveAndFlush(heartRate(owner.getId(), BASE.plusSeconds(60), "72"));

        String firstRaw = jdbc.queryForObject("select value_primary from vital_readings where id = ?",
                String.class, first.getId());
        String secondRaw = jdbc.queryForObject("select value_primary from vital_readings where id = ?",
                String.class, second.getId());

        assertThat(firstRaw).isNotEqualTo(secondRaw);
    }

    @Test
    void typeUnitAndTimeStayInPlainTextByDesign() {
        User owner = newUser();
        VitalReading saved = readings.saveAndFlush(heartRate(owner.getId(), BASE, "72"));

        Map<String, Object> row = jdbc.queryForMap(
                "select type, unit from vital_readings where id = ?", saved.getId());

        assertThat(row.get("type")).isEqualTo("HEART_RATE");
        assertThat(row.get("unit")).isEqualTo("/min");
    }

    @Test
    void anAbsentSecondValueAndNoteStayNullInTheDatabase() {
        User owner = newUser();
        VitalReading saved = readings.saveAndFlush(heartRate(owner.getId(), BASE, "72"));

        Map<String, Object> row = jdbc.queryForMap(
                "select value_secondary, note from vital_readings where id = ?", saved.getId());

        assertThat(row.get("value_secondary")).isNull();
        assertThat(row.get("note")).isNull();
    }

    @Test
    void aReadingIsOnlyFoundForItsOwner() {
        User owner = newUser();
        User other = newUser();
        VitalReading saved = readings.saveAndFlush(heartRate(owner.getId(), BASE, "72"));

        assertThat(readings.findByIdAndUserId(saved.getId(), owner.getId())).isPresent();
        assertThat(readings.findByIdAndUserId(saved.getId(), other.getId())).isEmpty();
    }

    @Test
    void listsNewestFirstWithStablePagingAndOnlyTheOwnersReadings() {
        User owner = newUser();
        User other = newUser();
        for (int day = 0; day < 5; day++) {
            readings.saveAndFlush(heartRate(owner.getId(), BASE.plus(day, ChronoUnit.DAYS), "7" + day));
        }
        readings.saveAndFlush(heartRate(other.getId(), BASE, "99"));

        Page<VitalReading> first = readings.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                owner.getId(), EARLIEST, LATEST, PageRequest.of(0, 2));
        Page<VitalReading> second = readings.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                owner.getId(), EARLIEST, LATEST, PageRequest.of(1, 2));
        Page<VitalReading> third = readings.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                owner.getId(), EARLIEST, LATEST, PageRequest.of(2, 2));

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getContent()).extracting(VitalReading::getMeasuredAt)
                .containsExactly(BASE.plus(4, ChronoUnit.DAYS), BASE.plus(3, ChronoUnit.DAYS));
        assertThat(second.getContent()).extracting(VitalReading::getMeasuredAt)
                .containsExactly(BASE.plus(2, ChronoUnit.DAYS), BASE.plus(1, ChronoUnit.DAYS));
        assertThat(third.getContent()).extracting(VitalReading::getMeasuredAt).containsExactly(BASE);
    }

    @Test
    void filtersByTypeAndByDateRangeInclusively() {
        User owner = newUser();
        readings.saveAndFlush(bloodPressure(owner.getId(), BASE, "120", "80", null));
        readings.saveAndFlush(heartRate(owner.getId(), BASE.plus(1, ChronoUnit.DAYS), "70"));
        readings.saveAndFlush(heartRate(owner.getId(), BASE.plus(2, ChronoUnit.DAYS), "71"));
        readings.saveAndFlush(heartRate(owner.getId(), BASE.plus(3, ChronoUnit.DAYS), "72"));

        Page<VitalReading> onlyHeartRate = readings.findByUserIdAndTypeAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                owner.getId(), VitalType.HEART_RATE, EARLIEST, LATEST, PageRequest.of(0, 10));
        Page<VitalReading> middle = readings.findByUserIdAndTypeAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
                owner.getId(), VitalType.HEART_RATE, BASE.plus(1, ChronoUnit.DAYS), BASE.plus(2, ChronoUnit.DAYS),
                PageRequest.of(0, 10));

        assertThat(onlyHeartRate.getTotalElements()).isEqualTo(3);
        assertThat(middle.getTotalElements()).isEqualTo(2);
    }

    @Test
    void deletingAUserDeletesTheirReadings() {
        User owner = newUser();
        VitalReading saved = readings.saveAndFlush(heartRate(owner.getId(), BASE, "72"));

        jdbc.update("delete from users where id = ?", owner.getId());

        assertThat(jdbc.queryForObject("select count(*) from vital_readings where id = ?",
                Integer.class, saved.getId())).isZero();
    }

    @Test
    void theDatabaseRejectsAnUnknownType() {
        User owner = newUser();

        assertThatThrownBy(() -> jdbc.update(
                "insert into vital_readings (user_id, type, unit, measured_at, value_primary) "
                        + "values (?, 'MOOD', 'x', now(), 'x')", owner.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywayAppliedTheThirdMigration() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '3' and success", Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}