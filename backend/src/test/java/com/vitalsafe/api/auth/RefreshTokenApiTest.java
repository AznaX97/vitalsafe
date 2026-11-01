package com.vitalsafe.api.auth;

import com.vitalsafe.api.security.TokenHasher;
import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenApiTest extends IntegrationTestBase {

    @Autowired
    private RefreshTokenService refreshTokenService;

    private HttpResponse<String> refresh(String rawToken) throws Exception {
        return call("POST", "/api/v1/auth/refresh", null, Map.of("Cookie", "refresh_token=" + rawToken));
    }

    private int refreshStatus(String rawToken) throws Exception {
        return refresh(rawToken).statusCode();
    }

    private int tokenRowsFor(String rawToken) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                TokenHasher.sha256Hex(rawToken));
    }

    @Test
    void registrationSetsAHardenedRefreshCookieAndKeepsTheTokenOutOfTheBody() throws Exception {
        HttpResponse<String> response = registerRaw(uniqueEmail(), PASSWORD, true);

        String header = setCookieHeader(response, "refresh_token");
        assertThat(header)
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=");
        assertThat(response.body()).doesNotContain(cookieValue(response, "refresh_token"));
    }

    @Test
    void loginAlsoSetsARefreshCookie() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = loginRaw(user.email(), PASSWORD);

        assertThat(cookieValue(response, "refresh_token")).isNotBlank().isNotEqualTo(user.refreshToken());
    }

    @Test
    void refreshRotatesTheTokenAndIssuesAWorkingAccessToken() throws Exception {
        Session first = registerUser();

        HttpResponse<String> response = refresh(first.refreshToken());

        assertThat(response.statusCode()).isEqualTo(200);
        Session second = sessionFrom(response);
        assertThat(second.refreshToken()).isNotBlank().isNotEqualTo(first.refreshToken());
        assertThat(second.email()).isEqualTo(first.email());
        assertThat(getAs("/api/v1/me", second.accessToken()).statusCode()).isEqualTo(200);
    }

    @Test
    void aUsedTokenIsRejected() throws Exception {
        Session session = registerUser();

        assertThat(refreshStatus(session.refreshToken())).isEqualTo(200);
        assertThat(refreshStatus(session.refreshToken())).isEqualTo(401);
    }

    @Test
    void replayingAUsedTokenEndsEverySessionOfThatUser() throws Exception {
        Session firstDevice = registerUser();
        HttpResponse<String> secondLogin = loginRaw(firstDevice.email(), PASSWORD);
        Session secondDevice = sessionFrom(secondLogin);

        // The first device refreshes normally
        HttpResponse<String> rotated = refresh(firstDevice.refreshToken());
        assertThat(rotated.statusCode()).isEqualTo(200);
        Session firstDeviceNew = sessionFrom(rotated);

        // Someone replays the old token: rejected, and every session is revoked
        assertThat(refreshStatus(firstDevice.refreshToken())).isEqualTo(401);

        assertThat(refreshStatus(firstDeviceNew.refreshToken())).isEqualTo(401);
        assertThat(refreshStatus(secondDevice.refreshToken())).isEqualTo(401);
    }

    @Test
    void aMissingOrUnknownCookieIsRejectedAndTheCookieIsCleared() throws Exception {
        HttpResponse<String> missing = call("POST", "/api/v1/auth/refresh", null, Map.of());
        HttpResponse<String> unknown = refresh("this-token-was-never-issued");

        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(setCookieHeader(unknown, "refresh_token")).contains("Max-Age=0");
    }

    @Test
    void logoutRevokesTheTokenAndClearsTheCookie() throws Exception {
        Session session = registerUser();

        HttpResponse<String> response = call("POST", "/api/v1/auth/logout", null,
                Map.of("Cookie", "refresh_token=" + session.refreshToken()));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(setCookieHeader(response, "refresh_token")).contains("Max-Age=0");
        assertThat(refreshStatus(session.refreshToken())).isEqualTo(401);
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        Session session = registerUser();
        String raw = session.refreshToken();

        Integer rawCount = jdbc.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, raw);

        assertThat(rawCount).isZero();
        assertThat(tokenRowsFor(raw)).isEqualTo(1);
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        Session session = registerUser();
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
                TokenHasher.sha256Hex(session.refreshToken()));

        assertThat(refreshStatus(session.refreshToken())).isEqualTo(401);
    }

    @Test
    void aRefreshTokenIsNotAnAccessToken() throws Exception {
        Session session = registerUser();

        assertThat(getAs("/api/v1/me", session.refreshToken()).statusCode()).isEqualTo(401);
    }

    @Test
    void theCleanupJobRemovesExpiredTokensAndKeepsLiveOnes() throws Exception {
        Session expired = registerUser();
        Session live = registerUser();
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
                TokenHasher.sha256Hex(expired.refreshToken()));

        refreshTokenService.purgeExpired();

        assertThat(tokenRowsFor(expired.refreshToken())).isZero();
        assertThat(tokenRowsFor(live.refreshToken())).isEqualTo(1);
    }

    @Test
    void deletingAUserDeletesTheirTokens() throws Exception {
        Session session = registerUser();

        jdbc.update("delete from users where email = ?", session.email());

        assertThat(tokenRowsFor(session.refreshToken())).isZero();
    }
}