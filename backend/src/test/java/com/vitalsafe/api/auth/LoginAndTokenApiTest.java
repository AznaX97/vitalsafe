package com.vitalsafe.api.auth;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAndTokenApiTest extends IntegrationTestBase {

    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        return parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);
    }

    @Test
    void loginWithTheRightPasswordGivesAnAccessToken() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = loginRaw(user.email(), PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(field(response.body(), "token")).isNotBlank();
        assertThat(field(response.body(), "email")).isEqualTo(user.email());
    }

    @Test
    void loginIgnoresTheCaseOfTheEmail() throws Exception {
        Session user = registerUser();

        assertThat(loginRaw(user.email().toUpperCase(), PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSameAnswer() throws Exception {
        Session user = registerUser();

        HttpResponse<String> wrongPassword = loginRaw(user.email(), "Wrong-Passw0rd-123");
        HttpResponse<String> unknownEmail = loginRaw(uniqueEmail(), PASSWORD);

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
        assertThat(field(wrongPassword.body(), "error")).isEqualTo("Invalid email or password");
    }

    @Test
    void anOverlongPasswordIsRefusedAsInvalidCredentialsNotAnError() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = loginRaw(user.email(), "\uD83D\uDE00".repeat(20));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void blankLoginFieldsAreRefused() throws Exception {
        assertThat(loginRaw("", "").statusCode()).isEqualTo(400);
    }

    @Test
    void anAccessTokenOpensTheCurrentUserEndpoint() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = getAs("/api/v1/me", user.accessToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(field(response.body(), "email")).isEqualTo(user.email());
        assertThat(field(response.body(), "role")).isEqualTo("PATIENT");
        assertThat(response.body()).doesNotContain("password").doesNotContain("$2");
    }

    @Test
    void theCurrentUserEndpointNeedsAToken() throws Exception {
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void garbageAndTamperedTokensAreRejected() throws Exception {
        Session user = registerUser();

        assertThat(getAs("/api/v1/me", "not.a.token").statusCode()).isEqualTo(401);
        assertThat(getAs("/api/v1/me", tamperSignature(user.accessToken())).statusCode()).isEqualTo(401);
    }

    @Test
    void aValidTokenForADeletedAccountNoLongerWorks() throws Exception {
        Session user = registerUser();
        jdbc.update("delete from users where email = ?", user.email());

        assertThat(getAs("/api/v1/me", user.accessToken()).statusCode()).isEqualTo(401);
    }
}