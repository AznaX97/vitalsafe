package com.vitalsafe.api.user;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends IntegrationTestBase {

    @Autowired
    private UserRepository users;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$12$" + "x".repeat(53));   // 60 characters, like a BCrypt hash
        user.setConsentedAt(Instant.now());
        user.setConsentVersion("2026-11-01");
        return user;
    }

    @Test
    void savesAUserAndFindsItByEmail() {
        String email = uniqueEmail();

        User saved = users.saveAndFlush(newUser(email));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.PATIENT);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(users.findByEmail(email)).get().extracting(User::getId).isEqualTo(saved.getId());
        assertThat(users.existsByEmail(email)).isTrue();
        assertThat(users.existsByEmail(uniqueEmail())).isFalse();
    }

    @Test
    void duplicateEmailIsRejected() {
        String email = uniqueEmail();
        users.saveAndFlush(newUser(email));

        assertThatThrownBy(() -> users.saveAndFlush(newUser(email)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsEmailsThatAreNotLowercase() {
        User user = newUser("Mixed.Case-" + UUID.randomUUID() + "@Example.com");

        assertThatThrownBy(() -> users.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAnUnknownRole() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (email, password_hash, role, consented_at, consent_version) "
                        + "values (?, 'hash', 'HACKER', now(), 'v1')", uniqueEmail()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRequiresAConsentRecord() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (email, password_hash) values (?, 'hash')", uniqueEmail()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywayAppliedTheFirstMigration() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}