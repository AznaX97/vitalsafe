package com.vitalsafe.api.metrics;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.metrics.token=0123456789abcdef0123456789abcdef0123456789abcdef",
        "management.defaults.metrics.export.enabled=true",
        "management.prometheus.metrics.export.enabled=true"
})
@AutoConfigureMetrics
class PrometheusEndpointTest extends IntegrationTestBase {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef";

    private HttpResponse<String> scrape(String token) throws Exception {
        return call("GET", "/actuator/prometheus", null, token == null ? Map.of() : Map.of("Authorization", "Bearer " + token));
    }

    @Test
    void noTokenAndAWrongTokenAreRefused() throws Exception {
        assertThat(scrape(null).statusCode()).isEqualTo(401);
        assertThat(scrape("not-the-token").statusCode()).isEqualTo(401);
    }

    @Test
    void aLoggedInUsersAccessTokenDoesNotOpenTheMetrics() throws Exception {
        Session user = registerUser();

        assertThat(scrape(user.accessToken()).statusCode()).isEqualTo(401);
    }

    @Test
    void theMetricsTokenOpensTheEndpointWithStandardJvmAndHttpMetrics() throws Exception {
        registerUser();

        HttpResponse<String> response = scrape(TOKEN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("text/plain"));
        assertThat(response.body()).contains("jvm_memory_used_bytes").contains("http_server_requests_seconds");
    }

    @Test
    void savingAReadingIsCountedWithoutAnyPersonalData() throws Exception {
        Session user = registerUser();
        HttpResponse<String> created = call("POST", "/api/v1/vitals",
                "{\"type\":\"HEART_RATE\",\"value\":72,\"measuredAt\":\"2026-01-10T08:00:00Z\",\"note\":\"very private\"}",
                Map.of("Authorization", "Bearer " + user.accessToken()));
        assertThat(created.statusCode()).isEqualTo(201);

        String body = scrape(TOKEN).body();

        assertThat(body).contains("vitalsafe_readings_saved_total").contains("type=\"HEART_RATE\"").contains("source=\"api\"");
        assertThat(body).doesNotContain(user.email()).doesNotContain("very private").doesNotContain(PASSWORD);
    }

    @Test
    void theGenericMetricsEndpointsAreNotExposed() throws Exception {
        assertThat(call("GET", "/actuator/metrics", null, Map.of("Authorization", "Bearer " + TOKEN)).statusCode())
                .isIn(401, 404);
        assertThat(call("GET", "/actuator/env", null, Map.of("Authorization", "Bearer " + TOKEN)).statusCode())
                .isIn(401, 404);
    }
}