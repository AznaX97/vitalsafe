package com.vitalsafe.api;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSmokeTest extends IntegrationTestBase {

    @Test
    void theApplicationStartsAndTheDatabaseIsReachable() {
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void healthEndpointIsPublicAndReportsUpWithoutDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        // Details such as the database name must not leak to anonymous callers
        assertThat(response.body()).doesNotContain("postgres").doesNotContain("components");
    }

    @Test
    void everythingElseRequiresAuthentication() throws Exception {
        assertThat(get("/api/v1/vitals").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/anything-at-all").statusCode()).isEqualTo(401);
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isIn(401, 404);
        assertThat(get("/actuator/beans").statusCode()).isIn(401, 404);
        assertThat(get("/actuator/heapdump").statusCode()).isIn(401, 404);
    }

    @Test
    void securityHeadersAreSet() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(value -> assertThat(value).contains("default-src 'none'"));
    }
}