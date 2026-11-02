package com.vitalsafe.api.vitals.summary;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.summary.max-readings=3")
class VitalSummaryLimitTest extends IntegrationTestBase {

    private static final String QUERY = "type=HEART_RATE&from=2026-01-01T00:00:00Z&to=2026-03-01T00:00:00Z";

    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        return call(method, path, body, Map.of("Authorization", "Bearer " + session.accessToken()));
    }

    private void logReadings(Session session, int howMany) throws Exception {
        for (int day = 1; day <= howMany; day++) {
            String body = String.format("{\"type\":\"HEART_RATE\",\"value\":70,\"measuredAt\":\"2026-01-%02dT08:00:00Z\"}", day);
            assertThat(send("POST", "/api/v1/vitals", body, session).statusCode()).isEqualTo(201);
        }
    }

    @Test
    void aRangeWithinTheCapIsSummarised() throws Exception {
        Session user = registerUser();
        logReadings(user, 3);

        assertThat(send("GET", "/api/v1/vitals/summary?" + QUERY, null, user).statusCode()).isEqualTo(200);
    }

    @Test
    void aRangeOverTheCapIsRefused() throws Exception {
        Session user = registerUser();
        logReadings(user, 4);

        HttpResponse<String> response = send("GET", "/api/v1/vitals/summary?" + QUERY, null, user);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("shorter range");
    }
}