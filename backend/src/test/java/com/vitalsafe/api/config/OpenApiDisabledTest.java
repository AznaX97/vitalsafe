package com.vitalsafe.api.config;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDisabledTest extends IntegrationTestBase {

    @Test
    void theDocsAreOffByDefault() throws Exception {
        assertThat(get("/v3/api-docs").statusCode()).isIn(401, 404);
        assertThat(get("/swagger-ui/index.html").statusCode()).isIn(401, 404);
        assertThat(get("/swagger-ui.html").statusCode()).isIn(401, 404);
    }
}