package com.vitalsafe.api.auth;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.refresh.cookie-secure=true")
class RefreshCookieSecureTest extends IntegrationTestBase {

    @Test
    void theCookieCarriesTheSecureFlagWhenConfiguredForProduction() throws Exception {
        HttpResponse<String> response = registerRaw(uniqueEmail(), PASSWORD, true);

        assertThat(setCookieHeader(response, "refresh_token")).contains("Secure").contains("HttpOnly");
    }
}