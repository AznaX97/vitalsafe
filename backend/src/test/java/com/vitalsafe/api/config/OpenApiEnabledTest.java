package com.vitalsafe.api.config;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiEnabledTest extends IntegrationTestBase {

    @Test
    void theApiDescriptionListsTheEndpointsAndTheBearerScheme() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("VitalSafe API")
                .contains("/api/v1/vitals")
                .contains("/api/v1/vitals/import")
                .contains("/api/v1/targets/{type}")
                .contains("/api/v1/auth/login")
                .contains("bearerAuth");
    }

    @Test
    void theDescriptionNeverExposesActuatorOrInternalEndpoints() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.body()).doesNotContain("/actuator");
    }

    @Test
    void theDocsPageGetsItsOwnPolicyThatAllowsItsOwnScripts() throws Exception {
        HttpResponse<String> response = get("/swagger-ui/index.html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(value -> assertThat(value).contains("script-src 'self'").contains("frame-ancestors 'none'"));
    }

    @Test
    void theApiItselfStillNeedsALoginWhenTheDocsAreOn() throws Exception {
        assertThat(get("/api/v1/vitals").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void otherPathsKeepTheStrictPolicy() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(value -> assertThat(value).contains("default-src 'none'").doesNotContain("script-src"));
    }
}