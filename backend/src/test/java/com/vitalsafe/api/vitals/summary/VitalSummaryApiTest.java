package com.vitalsafe.api.vitals.summary;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static com.vitalsafe.api.support.JsonText.number;
import static com.vitalsafe.api.support.JsonText.strings;
import static org.assertj.core.api.Assertions.assertThat;

class VitalSummaryApiTest extends IntegrationTestBase {

    private static final String JANUARY = "from=2026-01-01T00:00:00Z&to=2026-03-01T00:00:00Z";

    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        return call(method, path, body, Map.of("Authorization", "Bearer " + session.accessToken()));
    }

    private void log(Session session, String type, String value, String secondary, String unit, String at) throws Exception {
        String body = "{\"type\":\"" + type + "\",\"value\":" + value
                + (secondary == null ? "" : ",\"secondaryValue\":" + secondary)
                + (unit == null ? "" : ",\"unit\":\"" + unit + "\"")
                + ",\"measuredAt\":\"" + at + "\"}";
        HttpResponse<String> response = send("POST", "/api/v1/vitals", body, session);
        assertThat(response.statusCode()).as("logging a reading: " + response.body()).isEqualTo(201);
    }

    private void logHeartRate(Session session, String value, String at) throws Exception {
        log(session, "HEART_RATE", value, null, null, at);
    }

    private HttpResponse<String> summary(Session session, String query) throws Exception {
        return send("GET", "/api/v1/vitals/summary?" + query, null, session);
    }

    // ---------- access and parameters ----------

    @Test
    void summariesNeedALogin() throws Exception {
        assertThat(get("/api/v1/vitals/summary?type=HEART_RATE").statusCode()).isEqualTo(401);
    }

    @Test
    void theTypeIsRequired() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = summary(user, JANUARY);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"type\"").contains("required");
    }

    @Test
    void badParametersAreRefusedCleanly() throws Exception {
        Session user = registerUser();

        assertThat(summary(user, "type=NOPE").statusCode()).isEqualTo(400);
        assertThat(summary(user, "type=HEART_RATE&bucket=YEAR").statusCode()).isEqualTo(400);
        assertThat(summary(user, "type=HEART_RATE&zone=Mars/Olympus").statusCode()).isEqualTo(400);
        assertThat(summary(user, "type=HEART_RATE&from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z").statusCode())
                .isEqualTo(400);
        assertThat(summary(user, "type=HEART_RATE&from=yesterday").statusCode()).isEqualTo(400);
    }

    // ---------- statistics ----------

    @Test
    void anEmptyRangeIsASuccessfulEmptySummary() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = summary(user, "type=HEART_RATE&" + JANUARY);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(number(response.body(), "count")).isEqualTo("0");
        assertThat(response.body()).contains("\"primary\":null").contains("\"buckets\":[]");
    }

    @Test
    void calculatesMinMaxAverageLatestAndDailyBuckets() throws Exception {
        Session user = registerUser();
        logHeartRate(user, "60", "2026-01-12T08:00:00Z");
        logHeartRate(user, "70", "2026-01-13T08:00:00Z");
        logHeartRate(user, "80", "2026-01-14T08:00:00Z");

        HttpResponse<String> response = summary(user, "type=HEART_RATE&" + JANUARY);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(field(response.body(), "unit")).isEqualTo("/min");
        assertThat(number(response.body(), "count")).isEqualTo("3");
        assertThat(response.body())
                .contains("\"primary\":{\"min\":60,\"max\":80,\"average\":70.0}")
                .contains("\"secondary\":null")
                .contains("\"latest\":{\"measuredAt\":\"2026-01-14T08:00:00Z\",\"value\":80,\"secondaryValue\":null}")
                .contains("\"target\":null");
        assertThat(strings(response.body(), "start")).containsExactly("2026-01-12", "2026-01-13", "2026-01-14");
        assertThat(field(response.body(), "disclaimer")).contains("Not medical advice");
    }

    @Test
    void bloodPressureHasStatisticsForBothParts() throws Exception {
        Session user = registerUser();
        log(user, "BLOOD_PRESSURE", "120", "80", null, "2026-01-12T08:00:00Z");
        log(user, "BLOOD_PRESSURE", "140", "90", null, "2026-01-13T08:00:00Z");

        HttpResponse<String> response = summary(user, "type=BLOOD_PRESSURE&" + JANUARY);

        assertThat(response.body())
                .contains("\"primary\":{\"min\":120,\"max\":140,\"average\":130.0}")
                .contains("\"secondary\":{\"min\":80,\"max\":90,\"average\":85.0}");
    }

    @Test
    void theTimeZoneDecidesTheDayBuckets() throws Exception {
        Session user = registerUser();
        logHeartRate(user, "60", "2026-01-14T10:00:00Z");
        logHeartRate(user, "70", "2026-01-14T20:00:00Z");

        HttpResponse<String> utc = summary(user, "type=HEART_RATE&" + JANUARY);
        HttpResponse<String> colombo = summary(user, "type=HEART_RATE&zone=Asia/Colombo&" + JANUARY);

        assertThat(strings(utc.body(), "start")).containsExactly("2026-01-14");
        assertThat(strings(colombo.body(), "start")).containsExactly("2026-01-14", "2026-01-15");
        assertThat(field(colombo.body(), "zone")).isEqualTo("Asia/Colombo");
    }

    @Test
    void weeklyBucketsStartOnMonday() throws Exception {
        Session user = registerUser();
        logHeartRate(user, "60", "2026-01-12T08:00:00Z");
        logHeartRate(user, "70", "2026-01-14T08:00:00Z");
        logHeartRate(user, "80", "2026-01-19T08:00:00Z");

        HttpResponse<String> response = summary(user, "type=HEART_RATE&bucket=WEEK&" + JANUARY);

        assertThat(strings(response.body(), "start")).containsExactly("2026-01-12", "2026-01-19");
        assertThat(response.body()).contains("\"start\":\"2026-01-12\",\"count\":2");
    }

    @Test
    void monthlyBucketsStartOnTheFirst() throws Exception {
        Session user = registerUser();
        logHeartRate(user, "60", "2026-01-31T08:00:00Z");
        logHeartRate(user, "70", "2026-02-01T08:00:00Z");

        HttpResponse<String> response = summary(user, "type=HEART_RATE&bucket=MONTH&" + JANUARY);

        assertThat(strings(response.body(), "start")).containsExactly("2026-01-01", "2026-02-01");
    }

    @Test
    void theDefaultRangeIsTheLastThirtyDays() throws Exception {
        Session user = registerUser();
        String recent = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
        logHeartRate(user, "72", recent);
        logHeartRate(user, "90", "2026-01-12T08:00:00Z");   // far outside the default range

        HttpResponse<String> response = summary(user, "type=HEART_RATE");

        assertThat(number(response.body(), "count")).isEqualTo("1");
    }

    @Test
    void onlyTheCallersOwnReadingsAreSummarised() throws Exception {
        Session first = registerUser();
        Session second = registerUser();
        logHeartRate(first, "60", "2026-01-12T08:00:00Z");
        logHeartRate(first, "70", "2026-01-13T08:00:00Z");
        logHeartRate(second, "99", "2026-01-12T08:00:00Z");

        assertThat(number(summary(first, "type=HEART_RATE&" + JANUARY).body(), "count")).isEqualTo("2");
        HttpResponse<String> theirs = summary(second, "type=HEART_RATE&" + JANUARY);
        assertThat(number(theirs.body(), "count")).isEqualTo("1");
        assertThat(theirs.body()).contains("\"primary\":{\"min\":99,\"max\":99,\"average\":99.0}");
    }

    // ---------- units ----------

    @Test
    void mixedUnitsAreRefusedUntilOneIsChosen() throws Exception {
        Session user = registerUser();
        log(user, "BLOOD_GLUCOSE", "100", null, "mg/dL", "2026-01-12T08:00:00Z");
        log(user, "BLOOD_GLUCOSE", "5.5", null, "mmol/L", "2026-01-13T08:00:00Z");

        HttpResponse<String> mixed = summary(user, "type=BLOOD_GLUCOSE&" + JANUARY);
        HttpResponse<String> chosen = summary(user, "type=BLOOD_GLUCOSE&unit=mg/dL&" + JANUARY);

        assertThat(mixed.statusCode()).isEqualTo(400);
        assertThat(mixed.body()).contains("\"unit\"").contains("mg/dL").contains("mmol/L");
        assertThat(chosen.statusCode()).isEqualTo(200);
        assertThat(number(chosen.body(), "count")).isEqualTo("1");
        assertThat(field(chosen.body(), "unit")).isEqualTo("mg/dL");
    }

    // ---------- the range the user set ----------

    @Test
    void readingsAreCountedAgainstTheUsersOwnRange() throws Exception {
        Session user = registerUser();
        HttpResponse<String> set = send("PUT", "/api/v1/targets/HEART_RATE", "{\"lowValue\":50,\"highValue\":100}", user);
        assertThat(set.statusCode()).isEqualTo(200);
        logHeartRate(user, "40", "2026-01-12T08:00:00Z");
        logHeartRate(user, "70", "2026-01-13T08:00:00Z");
        logHeartRate(user, "120", "2026-01-14T08:00:00Z");

        HttpResponse<String> response = summary(user, "type=HEART_RATE&" + JANUARY);

        assertThat(response.body()).contains("\"target\":{\"lowValue\":50,\"highValue\":100,"
                + "\"lowSecondaryValue\":null,\"highSecondaryValue\":null,"
                + "\"within\":1,\"above\":1,\"below\":1,\"mixed\":0}");
    }

    @Test
    void anotherUsersRangeIsNeverUsed() throws Exception {
        Session owner = registerUser();
        Session other = registerUser();
        send("PUT", "/api/v1/targets/HEART_RATE", "{\"lowValue\":50,\"highValue\":100}", owner);
        logHeartRate(other, "70", "2026-01-12T08:00:00Z");

        HttpResponse<String> response = summary(other, "type=HEART_RATE&" + JANUARY);

        assertThat(response.body()).contains("\"target\":null");
    }

    @Test
    void aRangeInAnotherUnitIsNotApplied() throws Exception {
        Session user = registerUser();
        send("PUT", "/api/v1/targets/BLOOD_GLUCOSE", "{\"unit\":\"mmol/L\",\"lowValue\":4,\"highValue\":8}", user);
        log(user, "BLOOD_GLUCOSE", "100", null, "mg/dL", "2026-01-12T08:00:00Z");

        HttpResponse<String> response = summary(user, "type=BLOOD_GLUCOSE&" + JANUARY);

        assertThat(response.body()).contains("\"target\":null");
    }
}