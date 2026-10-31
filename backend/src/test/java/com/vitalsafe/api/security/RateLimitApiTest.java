package com.vitalsafe.api.security;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.rate-limit.per-minute=5")
class RateLimitApiTest extends IntegrationTestBase {

    private HttpResponse<String> loginFrom(String ip) throws Exception {
        return call("POST", "/api/v1/auth/login",
                json("email", "nobody@example.com", "password", "Wrong-Passw0rd-123"),
                Map.of("X-Forwarded-For", ip));
    }

    private HttpResponse<String> registerFrom(String ip) throws Exception {
        return call("POST", "/api/v1/auth/register",
                json("email", uniqueEmail(), "password", PASSWORD, "consent", true),
                Map.of("X-Forwarded-For", ip));
    }

    @Test
    void theSixthLoginAttemptFromOneAddressIsBlocked() throws Exception {
        String attacker = uniqueIp();

        for (int i = 0; i < 5; i++) {
            assertThat(loginFrom(attacker).statusCode()).isEqualTo(401);
        }
        HttpResponse<String> blocked = loginFrom(attacker);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).contains("60");
        assertThat(blocked.body()).contains("Too many attempts");
    }

    @Test
    void anotherAddressIsNotAffectedByTheBlock() throws Exception {
        String attacker = uniqueIp();
        for (int i = 0; i < 6; i++) {
            loginFrom(attacker);
        }

        assertThat(loginFrom(uniqueIp()).statusCode()).isEqualTo(401);
    }

    @Test
    void theSixthRegistrationFromOneAddressIsBlocked() throws Exception {
        String ip = uniqueIp();
        int last = 0;

        for (int i = 0; i < 6; i++) {
            last = registerFrom(ip).statusCode();
        }

        assertThat(last).isEqualTo(429);
    }

    @Test
    void refreshingASessionIsNotRateLimited() throws Exception {
        String ip = uniqueIp();

        for (int i = 0; i < 10; i++) {
            HttpResponse<String> response = call("POST", "/api/v1/auth/refresh", null,
                    Map.of("X-Forwarded-For", ip, "Cookie", "refresh_token=unknown"));
            assertThat(response.statusCode()).isEqualTo(401);
        }
    }
}