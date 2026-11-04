package com.vitalsafe.api.metrics;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusClosedByDefaultTest extends IntegrationTestBase {

    @Test
    void withNoTokenConfiguredNothingOpensTheEndpoint() throws Exception {
        assertThat(get("/actuator/prometheus").statusCode()).isEqualTo(401);
        assertThat(call("GET", "/actuator/prometheus", null, Map.of("Authorization", "Bearer anything-at-all")).statusCode())
                .isEqualTo(401);
    }
}