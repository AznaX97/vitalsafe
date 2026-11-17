package com.vitalsafe.api.account;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountApiTest extends IntegrationTestBase {

    private static final String NEW_PASSWORD = "Brand-New-Passw0rd-2026";

    private HttpResponse<String> authed(String path, String body, Session session) throws Exception {
        return call("POST", path, body, Map.of("Authorization", "Bearer " + session.accessToken()));
    }

    private HttpResponse<String> change(Session session, String current, String next) throws Exception {
        return authed("/api/v1/account/password",
                "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}", session);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static String refreshCookieOf(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("refresh_token="))
                .map(value -> value.substring(0, value.indexOf(';')))
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> refreshWith(String cookie) throws Exception {
        return call("POST", "/api/v1/auth/refresh", null, Map.of("Cookie", cookie));
    }

    // ---------- changing the password ----------

    @Test
    void changingThePasswordEndsEverySessionAndClearsTheCookie() throws Exception {
        Session user = registerUser();
        String cookie = refreshCookieOf(login(user.email(), PASSWORD));
        assertThat(refreshWith(cookie).statusCode()).isEqualTo(200);
        String cookieAfterRefresh = refreshCookieOf(login(user.email(), PASSWORD));

        HttpResponse<String> response = change(user, PASSWORD, NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(204);
        String cleared = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("refresh_token=")).findFirst().orElseThrow();
        assertThat(cleared).contains("Max-Age=0").contains("Path=/api/v1/auth").contains("HttpOnly");
        assertThat(refreshWith(cookieAfterRefresh).statusCode()).isEqualTo(401);
    }

    @Test
    void afterTheChangeOnlyTheNewPasswordWorks() throws Exception {
        Session user = registerUser();

        change(user, PASSWORD, NEW_PASSWORD);

        assertThat(login(user.email(), PASSWORD).statusCode()).isEqualTo(401);
        assertThat(login(user.email(), NEW_PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void aWrongCurrentPasswordIsA400WithAFieldMessageAndChangesNothing() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = change(user, "Not-The-Current-Passw0rd", NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"currentPassword\"").contains("Current password is incorrect");
        assertThat(login(user.email(), PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void aWeakNewPasswordIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = change(user, PASSWORD, "short");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"newPassword\"").contains("at least 12");
    }

    @Test
    void aNewPasswordOverSeventyTwoBytesIsRefusedEvenWithFewCharacters() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = change(user, PASSWORD, "\u00e9".repeat(40));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("72 bytes");
    }

    @Test
    void theNewPasswordMustDiffer() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = change(user, PASSWORD, PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("must be different");
    }

    @Test
    void bothProblemsAreReportedTogether() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = change(user, "wrong-wrong-wrong-1", "short");

        assertThat(response.body()).contains("\"currentPassword\"").contains("\"newPassword\"");
    }

    @Test
    void anUnusedResetLinkStopsWorkingOnceThePasswordChanged() throws Exception {
        Session user = registerUser();
        UUID userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, user.email());
        jdbc.update("insert into password_reset_tokens (user_id, token_hash, expires_at) values (?, ?, now() + interval '10 minutes')",
                userId, "f".repeat(64));

        change(user, PASSWORD, NEW_PASSWORD);

        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where user_id = ? and used_at is null",
                Integer.class, userId)).isZero();
    }

    @Test
    void changingThePasswordNeedsALogin() throws Exception {
        HttpResponse<String> response = call("POST", "/api/v1/account/password",
                "{\"currentPassword\":\"x\",\"newPassword\":\"y\"}", Map.of());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void aMissingFieldIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = authed("/api/v1/account/password", "{\"currentPassword\":\"x\"}", user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("newPassword");
    }

    // ---------- deleting the account ----------

    @Test
    void aWrongPasswordDeletesNothing() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = authed("/api/v1/account/delete", "{\"password\":\"Not-The-Passw0rd-123\"}", user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Password is incorrect");
        assertThat(login(user.email(), PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void deletingTheAccountRemovesEverythingAndEndsTheSession() throws Exception {
        Session user = registerUser();
        UUID userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, user.email());
        String cookie = refreshCookieOf(login(user.email(), PASSWORD));
        String auth = "Bearer " + user.accessToken();
        call("POST", "/api/v1/vitals",
                "{\"type\":\"HEART_RATE\",\"value\":72,\"measuredAt\":\"2026-01-10T08:00:00Z\",\"note\":\"mine\"}",
                Map.of("Authorization", auth));
        call("PUT", "/api/v1/targets/HEART_RATE", "{\"lowValue\":50,\"highValue\":100}", Map.of("Authorization", auth));
        assertThat(jdbc.queryForObject("select count(*) from vital_readings where user_id = ?", Integer.class, userId)).isEqualTo(1);

        HttpResponse<String> response = authed("/api/v1/account/delete", "{\"password\":\"" + PASSWORD + "\"}", user);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.headers().allValues("Set-Cookie").stream().anyMatch(value -> value.contains("Max-Age=0"))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from vital_readings where user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from target_ranges where user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?", Integer.class, userId)).isZero();
        assertThat(login(user.email(), PASSWORD).statusCode()).isEqualTo(401);
        assertThat(refreshWith(cookie).statusCode()).isEqualTo(401);
    }

    @Test
    void deletingOneAccountLeavesEveryoneElseUntouched() throws Exception {
        Session leaving = registerUser();
        Session staying = registerUser();
        UUID stayingId = jdbc.queryForObject("select id from users where email = ?", UUID.class, staying.email());
        call("POST", "/api/v1/vitals", "{\"type\":\"HEART_RATE\",\"value\":70,\"measuredAt\":\"2026-01-10T08:00:00Z\"}",
                Map.of("Authorization", "Bearer " + staying.accessToken()));

        authed("/api/v1/account/delete", "{\"password\":\"" + PASSWORD + "\"}", leaving);

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, stayingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from vital_readings where user_id = ?", Integer.class, stayingId)).isEqualTo(1);
        assertThat(login(staying.email(), PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void deletingNeedsALoginAndAPassword() throws Exception {
        Session user = registerUser();

        assertThat(call("POST", "/api/v1/account/delete", "{\"password\":\"x\"}", Map.of()).statusCode()).isEqualTo(401);
        assertThat(authed("/api/v1/account/delete", "{}", user).statusCode()).isEqualTo(400);
    }

    // ---------- throttling ----------

    @Test
    void guessingTheCurrentPasswordIsThrottledPerAddress() throws Exception {
        Session user = registerUser();
        String address = "10." + (int) (Math.random() * 256) + "." + (int) (Math.random() * 256) + "." + (int) (Math.random() * 256);
        Map<String, String> headers = Map.of("Authorization", "Bearer " + user.accessToken(), "X-Forwarded-For", address);
        String body = "{\"currentPassword\":\"wrong-wrong-wrong-1\",\"newPassword\":\"" + NEW_PASSWORD + "\"}";

        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(call("POST", "/api/v1/account/password", body, headers).statusCode()).isEqualTo(400);
        }
        HttpResponse<String> eleventh = call("POST", "/api/v1/account/password", body, headers);

        assertThat(eleventh.statusCode()).isEqualTo(429);
        assertThat(eleventh.body()).contains("Too many attempts");
    }
}