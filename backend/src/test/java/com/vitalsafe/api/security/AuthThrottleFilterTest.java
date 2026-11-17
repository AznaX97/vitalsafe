package com.vitalsafe.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthThrottleFilterTest {

    private static final String FORGOT = "/api/v1/auth/forgot-password";

    private final AuthThrottleFilter filter = new AuthThrottleFilter(List.of(
            new AuthThrottleFilter.Rule(FORGOT, 2),
            new AuthThrottleFilter.Rule("/api/v1/account/password", 1)));

    private record Outcome(int status, boolean reachedTheEndpoint, String body) {}

    private Outcome call(String method, String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(response.getStatus(), chain.getRequest() != null, response.getContentAsString());
    }

    @Test
    void theCallsWithinTheLimitPassAndTheNextOneIsRefused() throws Exception {
        assertThat(call("POST", FORGOT, "10.0.0.1").reachedTheEndpoint()).isTrue();
        assertThat(call("POST", FORGOT, "10.0.0.1").reachedTheEndpoint()).isTrue();

        Outcome third = call("POST", FORGOT, "10.0.0.1");

        assertThat(third.status()).isEqualTo(429);
        assertThat(third.reachedTheEndpoint()).isFalse();
        assertThat(third.body()).contains("Too many attempts");
    }

    @Test
    void eachAddressHasItsOwnCount() throws Exception {
        call("POST", FORGOT, "10.0.0.2");
        call("POST", FORGOT, "10.0.0.2");
        call("POST", FORGOT, "10.0.0.2");

        assertThat(call("POST", FORGOT, "10.0.0.3").reachedTheEndpoint()).isTrue();
    }

    @Test
    void eachEndpointHasItsOwnCount() throws Exception {
        call("POST", FORGOT, "10.0.0.4");
        call("POST", FORGOT, "10.0.0.4");
        call("POST", FORGOT, "10.0.0.4");

        assertThat(call("POST", "/api/v1/account/password", "10.0.0.4").reachedTheEndpoint()).isTrue();
        assertThat(call("POST", "/api/v1/account/password", "10.0.0.4").status()).isEqualTo(429);
    }

    @Test
    void otherPathsAndMethodsAreNeverCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("POST", "/api/v1/vitals", "10.0.0.5").reachedTheEndpoint()).isTrue();
            assertThat(call("GET", FORGOT, "10.0.0.5").reachedTheEndpoint()).isTrue();
        }
    }

    @Test
    void variantsOfThePathAreCountedToo() throws Exception {
        call("POST", FORGOT + "/", "10.0.0.6");
        call("POST", FORGOT + ";x=1", "10.0.0.6");

        assertThat(call("POST", FORGOT, "10.0.0.6").status()).isEqualTo(429);
    }

    @Test
    void theDefaultsCanBeScaledUp() throws Exception {
        AuthThrottleFilter scaled = AuthThrottleFilter.withDefaults(100);
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", FORGOT);
            request.setRequestURI(FORGOT);
            request.setRemoteAddr("10.0.0.7");
            MockHttpServletResponse response = new MockHttpServletResponse();
            scaled.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}