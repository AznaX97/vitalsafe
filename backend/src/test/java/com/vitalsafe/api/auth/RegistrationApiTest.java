package com.vitalsafe.api.auth;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationApiTest extends IntegrationTestBase {

    private int usersWithEmail(String email) {
        return jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email);
    }

    @Test
    void registersAUserAndReturnsAnAccessToken() throws Exception {
        String email = "Mixed." + UUID.randomUUID() + "@Example.com";

        HttpResponse<String> response = registerRaw(email, PASSWORD, true);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(field(response.body(), "email")).isEqualTo(email.toLowerCase());
        assertThat(field(response.body(), "token")).matches("[\\w-]+\\.[\\w-]+\\.[\\w-]+");
        assertThat(response.body()).doesNotContain(PASSWORD).doesNotContain("$2");
    }

    @Test
    void storesOnlyABcryptHashOfThePassword() throws Exception {
        String email = uniqueEmail();
        registerRaw(email, PASSWORD, true);

        String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);

        assertThat(hash).startsWith("$2").hasSize(60).doesNotContain(PASSWORD);
    }

    @Test
    void recordsConsentWithTimeAndNoticeVersionAndMakesThemAPatient() throws Exception {
        String email = uniqueEmail();
        registerRaw(email, PASSWORD, true);

        Map<String, Object> row = jdbc.queryForMap(
                "select role, consent_version, consented_at > now() - interval '1 minute' as recent "
                        + "from users where email = ?", email);

        assertThat(row.get("role")).isEqualTo("PATIENT");
        assertThat((String) row.get("consent_version")).isNotBlank();
        assertThat(row.get("recent")).isEqualTo(true);
    }

    @Test
    void registrationWithoutConsentIsRefusedAndNoAccountIsCreated() throws Exception {
        String email = uniqueEmail();

        HttpResponse<String> response = post("/api/v1/auth/register", json("email", email, "password", PASSWORD));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("consent");
        assertThat(usersWithEmail(email)).isZero();
    }

    @Test
    void registrationWithConsentSetToFalseIsRefused() throws Exception {
        String email = uniqueEmail();

        HttpResponse<String> response = registerRaw(email, PASSWORD, false);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("privacy notice");
        assertThat(usersWithEmail(email)).isZero();
    }

    @Test
    void aRoleInTheRequestIsIgnored() throws Exception {
        String email = uniqueEmail();
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"consent\":true,\"role\":\"ADMIN\"}";

        HttpResponse<String> response = post("/api/v1/auth/register", body);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select role from users where email = ?", String.class, email))
                .isEqualTo("PATIENT");
    }

    @Test
    void anEmailThatIsAlreadyRegisteredIsRefusedEvenInAnotherCase() throws Exception {
        String email = uniqueEmail();
        assertThat(registerRaw(email, PASSWORD, true).statusCode()).isEqualTo(201);

        HttpResponse<String> second = registerRaw(email.toUpperCase(), PASSWORD, true);

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(field(second.body(), "error")).isEqualTo("Email already registered");
        assertThat(usersWithEmail(email)).isEqualTo(1);
    }

    @Test
    void aShortPasswordIsRefused() throws Exception {
        String email = uniqueEmail();

        HttpResponse<String> response = registerRaw(email, "Short-1", true);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("password");
        assertThat(usersWithEmail(email)).isZero();
    }

    @Test
    void aPasswordOver72BytesIsRefusedEvenWhenItHasFewCharacters() throws Exception {
        // 20 emoji = 40 characters but 80 bytes, and BCrypt would silently ignore the tail
        String emojiPassword = "\uD83D\uDE00".repeat(20);

        HttpResponse<String> response = registerRaw(uniqueEmail(), emojiPassword, true);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).contains("too long");
    }

    @Test
    void anInvalidEmailIsRefused() throws Exception {
        HttpResponse<String> response = registerRaw("not-an-email", PASSWORD, true);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("email");
    }

    @Test
    void malformedJsonGetsACleanError() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/register", "{ this is not json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).isEqualTo("Malformed request body");
    }
}