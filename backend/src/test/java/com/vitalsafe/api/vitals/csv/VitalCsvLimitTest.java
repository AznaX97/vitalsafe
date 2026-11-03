package com.vitalsafe.api.vitals.csv;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.csv.max-export-rows=2")
class VitalCsvLimitTest extends IntegrationTestBase {

    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        return call(method, path, body, Map.of("Authorization", "Bearer " + session.accessToken()));
    }

    private void log(Session session, int dayOfJanuary) throws Exception {
        String body = String.format("{\"type\":\"HEART_RATE\",\"value\":72,\"measuredAt\":\"2026-01-%02dT08:00:00Z\"}", dayOfJanuary);
        assertThat(send("POST", "/api/v1/vitals", body, session).statusCode()).isEqualTo(201);
    }

    @Test
    void anExportWithinTheCapWorks() throws Exception {
        Session user = registerUser();
        log(user, 10);
        log(user, 11);

        assertThat(send("GET", "/api/v1/vitals/export", null, user).statusCode()).isEqualTo(200);
    }

    @Test
    void anExportOverTheCapIsRefusedWithAHint() throws Exception {
        Session user = registerUser();
        log(user, 10);
        log(user, 11);
        log(user, 12);

        HttpResponse<String> response = send("GET", "/api/v1/vitals/export", null, user);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("from and to");
    }
}