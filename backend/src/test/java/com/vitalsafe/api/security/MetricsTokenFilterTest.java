package com.vitalsafe.api.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsTokenFilterTest {

    private static final String TOKEN = "t".repeat(40);

    private final MetricsTokenFilter filter = new MetricsTokenFilter(TOKEN);

    private record Outcome(int status, boolean reachedTheEndpoint) {}

    private static Outcome run(MetricsTokenFilter filter, String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(response.getStatus(), chain.getRequest() != null);
    }

    @Test
    void theRightTokenOpensTheEndpoint() throws Exception {
        Outcome outcome = run(filter, "/actuator/prometheus", "Bearer " + TOKEN);

        assertThat(outcome.reachedTheEndpoint()).isTrue();
        assertThat(outcome.status()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void aWrongOrMissingTokenIsRefused() throws Exception {
        for (String header : new String[]{null, "", "Bearer wrong", "Bearer " + TOKEN + "x", "Basic " + TOKEN, TOKEN}) {
            Outcome outcome = run(filter, "/actuator/prometheus", header);

            assertThat(outcome.reachedTheEndpoint()).as("header: " + header).isFalse();
            assertThat(outcome.status()).isEqualTo(401);
        }
    }

    @Test
    void variantsOfThePathAreAlsoGuarded() throws Exception {
        assertThat(run(filter, "/actuator/prometheus/", null).status()).isEqualTo(401);
        assertThat(run(filter, "/actuator/prometheus;x=1", null).status()).isEqualTo(401);
    }

    @Test
    void otherPathsPassThroughUntouched() throws Exception {
        Outcome outcome = run(filter, "/api/v1/me", null);

        assertThat(outcome.reachedTheEndpoint()).isTrue();
        assertThat(outcome.status()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void withNoTokenConfiguredTheEndpointStaysClosedEvenIfSomeoneSendsOne() throws Exception {
        MetricsTokenFilter closed = new MetricsTokenFilter("");

        assertThat(run(closed, "/actuator/prometheus", "Bearer " + TOKEN).status()).isEqualTo(401);
        assertThat(run(new MetricsTokenFilter(null), "/actuator/prometheus", "Bearer ").status()).isEqualTo(401);
    }

    @Test
    void aTooShortTokenIsRefusedAtStartup() {
        assertThatThrownBy(() -> new MetricsTokenFilter("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }
}