package com.vitalsafe.api.account;

import com.vitalsafe.api.account.mail.ResetMailer;
import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PasswordResetApiTest extends IntegrationTestBase {

    private static final String NEW_PASSWORD = "Reset-To-Passw0rd-2026";

    @MockitoBean
    ResetMailer mailer;

    // A fresh address per call keeps the per-address throttle out of the way
    private static String address() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(256);
    }

    private HttpResponse<String> forgot(String email, String forwardedFor) throws Exception {
        return call("POST", "/api/v1/auth/forgot-password", "{\"email\":\"" + email + "\"}", Map.of("X-Forwarded-For", forwardedFor));
    }

    private HttpResponse<String> resetPassword(String token, String password) throws Exception {
        return call("POST", "/api/v1/auth/reset-password",
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}", Map.of("X-Forwarded-For", address()));
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    // Asks for a reset and returns the token that the "email" carried
    private String requestToken(String email) throws Exception {
        clearInvocations(mailer);
        assertThat(forgot(email, address()).statusCode()).isEqualTo(202);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(3000)).sendResetLink(anyString(), link.capture(), anyInt());
        String value = link.getValue();
        assertThat(value).contains("/reset-password#token=");
        return value.substring(value.indexOf("#token=") + "#token=".length());
    }

    // ---------- asking for a link ----------

    @Test
    void anExistingAddressGetsAnEmailWithALinkIntoTheFrontend() throws Exception {
        Session user = registerUser();
        clearInvocations(mailer);

        assertThat(forgot(user.email(), address()).statusCode()).isEqualTo(202);

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(3000)).sendResetLink(to.capture(), link.capture(), anyInt());
        assertThat(to.getValue()).isEqualToIgnoringCase(user.email());
        assertThat(link.getValue()).startsWith("http://localhost:5173/reset-password#token=");
    }

    @Test
    void anUnknownAddressGetsTheSameAnswerAndNoEmail() throws Exception {
        Session user = registerUser();
        clearInvocations(mailer);

        HttpResponse<String> known = forgot(user.email(), address());
        HttpResponse<String> unknown = forgot("nobody-" + System.nanoTime() + "@example.com", address());

        assertThat(unknown.statusCode()).isEqualTo(known.statusCode());
        assertThat(unknown.body()).isEqualTo(known.body());
        verify(mailer, after(800).times(1)).sendResetLink(anyString(), anyString(), anyInt());
    }

    @Test
    void anUnknownAddressSendsNothingAtAll() throws Exception {
        clearInvocations(mailer);

        forgot("nobody-" + System.nanoTime() + "@example.com", address());

        verify(mailer, after(800).never()).sendResetLink(anyString(), anyString(), anyInt());
    }

    @Test
    void theAddressMayBeTypedInAnyCaseWithSpaces() throws Exception {
        Session user = registerUser();
        clearInvocations(mailer);

        forgot("  " + user.email().toUpperCase() + "  ", address());

        verify(mailer, timeout(3000)).sendResetLink(anyString(), anyString(), anyInt());
    }

    @Test
    void aMissingAddressIsRefused() throws Exception {
        HttpResponse<String> response = call("POST", "/api/v1/auth/forgot-password", "{}", Map.of("X-Forwarded-For", address()));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void aFailingMailServerStillGivesTheSameAnswer() throws Exception {
        Session user = registerUser();
        doThrow(new RuntimeException("mail server down")).when(mailer).sendResetLink(anyString(), anyString(), anyInt());

        HttpResponse<String> response = forgot(user.email(), address());

        assertThat(response.statusCode()).isEqualTo(202);
    }

    @Test
    void anAccountGetsAtMostThreeEmailsAnHour() throws Exception {
        Session user = registerUser();
        clearInvocations(mailer);

        for (int request = 1; request <= 4; request++) {
            assertThat(forgot(user.email(), address()).statusCode()).isEqualTo(202);
        }

        verify(mailer, after(1500).times(3)).sendResetLink(anyString(), anyString(), anyInt());
    }

    @Test
    void oneAddressMayAskOnlyFiveTimesAnHour() throws Exception {
        String sameAddress = address();
        for (int request = 1; request <= 5; request++) {
            assertThat(forgot("nobody-" + request + "-" + System.nanoTime() + "@example.com", sameAddress).statusCode()).isEqualTo(202);
        }

        HttpResponse<String> sixth = forgot("nobody-6@example.com", sameAddress);

        assertThat(sixth.statusCode()).isEqualTo(429);
    }

    // ---------- using the link ----------

    @Test
    void theEmailedLinkSetsANewPasswordOnce() throws Exception {
        Session user = registerUser();
        String token = requestToken(user.email());

        HttpResponse<String> response = resetPassword(token, NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(login(user.email(), PASSWORD).statusCode()).isEqualTo(401);
        assertThat(login(user.email(), NEW_PASSWORD).statusCode()).isEqualTo(200);
        HttpResponse<String> second = resetPassword(token, "Another-Passw0rd-2026");
        assertThat(second.statusCode()).isEqualTo(400);
        assertThat(second.body()).contains("invalid or has expired");
    }

    @Test
    void resettingEndsEveryExistingSession() throws Exception {
        Session user = registerUser();
        HttpResponse<String> firstLogin = login(user.email(), PASSWORD);
        String cookie = firstLogin.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("refresh_token=")).map(value -> value.substring(0, value.indexOf(';')))
                .findFirst().orElseThrow();
        String token = requestToken(user.email());

        resetPassword(token, NEW_PASSWORD);

        assertThat(call("POST", "/api/v1/auth/refresh", null, Map.of("Cookie", cookie)).statusCode()).isEqualTo(401);
    }

    @Test
    void whenTwoLinksWereRequestedUsingOneKillsTheOther() throws Exception {
        Session user = registerUser();
        String first = requestToken(user.email());
        String second = requestToken(user.email());

        assertThat(resetPassword(first, NEW_PASSWORD).statusCode()).isEqualTo(204);

        assertThat(resetPassword(second, "Another-Passw0rd-2026").statusCode()).isEqualTo(400);
    }

    @Test
    void aMadeUpTokenIsRefused() throws Exception {
        HttpResponse<String> response = resetPassword("this-is-not-a-real-token", NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("invalid or has expired");
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        Session user = registerUser();
        String token = requestToken(user.email());
        jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 minute' "
                + "where user_id = (select id from users where email = ?)", user.email());

        HttpResponse<String> response = resetPassword(token, NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(login(user.email(), PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void aWeakPasswordIsRefusedWithoutUsingUpTheToken() throws Exception {
        Session user = registerUser();
        String token = requestToken(user.email());

        HttpResponse<String> weak = resetPassword(token, "short");

        assertThat(weak.statusCode()).isEqualTo(400);
        assertThat(weak.body()).contains("\"newPassword\"");
        assertThat(resetPassword(token, NEW_PASSWORD).statusCode()).isEqualTo(204);
    }

    @Test
    void theTokenIsStoredOnlyAsAHash() throws Exception {
        Session user = registerUser();
        String token = requestToken(user.email());

        String stored = jdbc.queryForObject("select token_hash from password_reset_tokens "
                + "where user_id = (select id from users where email = ?) order by created_at desc limit 1", String.class, user.email());

        assertThat(stored).hasSize(64).doesNotContain(token).isEqualTo(PasswordResetService.hash(token));
    }

    @Test
    void theApiNeverRepeatsTheTokenBack() throws Exception {
        Session user = registerUser();
        String token = requestToken(user.email());

        HttpResponse<String> response = resetPassword(token, "short");

        assertThat(response.body()).doesNotContain(token);
    }

    @Test
    void guessingTokensIsThrottledPerAddress() throws Exception {
        String sameAddress = address();
        Map<String, String> headers = Map.of("X-Forwarded-For", sameAddress);
        String body = "{\"token\":\"guess\",\"newPassword\":\"" + NEW_PASSWORD + "\"}";

        for (int attempt = 1; attempt <= 10; attempt++) {
            assertThat(call("POST", "/api/v1/auth/reset-password", body, headers).statusCode()).isEqualTo(400);
        }

        assertThat(call("POST", "/api/v1/auth/reset-password", body, headers).statusCode()).isEqualTo(429);
        verify(mailer, never()).sendResetLink(anyString(), anyString(), anyInt());
        verify(mailer, times(0)).sendResetLink(anyString(), anyString(), anyInt());
    }
}