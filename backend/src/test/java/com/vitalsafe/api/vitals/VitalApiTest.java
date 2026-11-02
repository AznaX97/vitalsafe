package com.vitalsafe.api.vitals;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static com.vitalsafe.api.support.JsonText.number;
import static com.vitalsafe.api.support.JsonText.strings;
import static org.assertj.core.api.Assertions.assertThat;

class VitalApiTest extends IntegrationTestBase {

    private static String day(int dayOfJanuary) {
        return String.format("2026-01-%02dT08:00:00Z", dayOfJanuary);
    }

    private static String reading(String type, String value, String secondary, String unit, String at, String note) {
        StringBuilder body = new StringBuilder("{\"type\":\"").append(type).append("\"");
        if (value != null) {
            body.append(",\"value\":").append(value);
        }
        if (secondary != null) {
            body.append(",\"secondaryValue\":").append(secondary);
        }
        if (unit != null) {
            body.append(",\"unit\":\"").append(unit).append("\"");
        }
        if (at != null) {
            body.append(",\"measuredAt\":\"").append(at).append("\"");
        }
        if (note != null) {
            body.append(",\"note\":\"").append(note).append("\"");
        }
        return body.append("}").toString();
    }

    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        return call(method, path, body, Map.of("Authorization", "Bearer " + session.accessToken()));
    }

    private String create(Session session, String body) throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/vitals", body, session);
        assertThat(response.statusCode()).as("creating a reading: " + response.body()).isEqualTo(201);
        return field(response.body(), "id");
    }

    private String createHeartRate(Session session, int dayOfJanuary) throws Exception {
        return create(session, reading("HEART_RATE", "72", null, null, day(dayOfJanuary), null));
    }

    // ---------- access ----------

    @Test
    void readingsNeedALogin() throws Exception {
        assertThat(get("/api/v1/vitals").statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/vitals", reading("HEART_RATE", "72", null, null, day(15), null)).statusCode())
                .isEqualTo(401);
    }

    // ---------- create ----------

    @Test
    void createsABloodPressureReading() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals",
                reading("BLOOD_PRESSURE", "120", "80", null, day(15), "after a walk"), user);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(field(response.body(), "type")).isEqualTo("BLOOD_PRESSURE");
        assertThat(number(response.body(), "value")).isEqualTo("120");
        assertThat(number(response.body(), "secondaryValue")).isEqualTo("80");
        assertThat(field(response.body(), "unit")).isEqualTo("mm[Hg]");
        assertThat(field(response.body(), "measuredAt")).isEqualTo(day(15));
        assertThat(field(response.body(), "note")).isEqualTo("after a walk");
        assertThat(response.body()).doesNotContain("userId").doesNotContain("user_id");
    }

    @Test
    void everyOtherTypeUsesItsDefaultUnitAndTidiesTrailingZeros() throws Exception {
        Session user = registerUser();

        HttpResponse<String> heartRate = send("POST", "/api/v1/vitals", reading("HEART_RATE", "72", null, null, day(15), null), user);
        HttpResponse<String> weight = send("POST", "/api/v1/vitals", reading("BODY_WEIGHT", "72.50", null, null, day(15), null), user);
        HttpResponse<String> temperature = send("POST", "/api/v1/vitals", reading("BODY_TEMPERATURE", "36.60", null, null, day(15), null), user);
        HttpResponse<String> oxygen = send("POST", "/api/v1/vitals", reading("OXYGEN_SATURATION", "98", null, null, day(15), null), user);
        HttpResponse<String> glucose = send("POST", "/api/v1/vitals", reading("BLOOD_GLUCOSE", "5.5", null, "mmol/L", day(15), null), user);

        assertThat(field(heartRate.body(), "unit")).isEqualTo("/min");
        assertThat(field(weight.body(), "unit")).isEqualTo("kg");
        assertThat(number(weight.body(), "value")).isEqualTo("72.5");
        assertThat(field(temperature.body(), "unit")).isEqualTo("Cel");
        assertThat(number(temperature.body(), "value")).isEqualTo("36.6");
        assertThat(field(oxygen.body(), "unit")).isEqualTo("%");
        assertThat(field(glucose.body(), "unit")).isEqualTo("mmol/L");
        assertThat(glucose.statusCode()).isEqualTo(201);
    }

    @Test
    void glucoseWithoutAUnitIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals",
                reading("BLOOD_GLUCOSE", "100", null, null, day(15), null), user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"unit\"").contains("required");
    }

    @Test
    void anImpossibleValueIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals",
                reading("HEART_RATE", "5", null, null, day(15), null), user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).isEqualTo("Validation failed");
        assertThat(response.body()).contains("\"value\"").contains("between 20 and 250");
    }

    @Test
    void severalProblemsAreReportedTogether() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals",
                reading("BLOOD_PRESSURE", "400", null, null, "2999-01-01T00:00:00Z", null), user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"value\"").contains("\"secondaryValue\"").contains("\"measuredAt\"");
    }

    @Test
    void aMissingTypeIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals",
                "{\"value\":72,\"measuredAt\":\"" + day(15) + "\"}", user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"type\"");
    }

    @Test
    void anUnknownTypeGetsACleanError() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals", reading("MOOD", "5", null, null, day(15), null), user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).isEqualTo("Malformed request body");
    }

    @Test
    void aMissingTimeIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals", reading("HEART_RATE", "72", null, null, null, null), user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"measuredAt\"");
    }

    @Test
    void aTimeInTheFutureIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals",
                reading("HEART_RATE", "72", null, null, "2999-01-01T00:00:00Z", null), user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("cannot be in the future");
    }

    @Test
    void aTooLongNoteIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("POST", "/api/v1/vitals",
                reading("HEART_RATE", "72", null, null, day(15), "x".repeat(1001)), user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"note\"");
    }

    @Test
    void aBlankNoteIsStoredAsNothing() throws Exception {
        Session user = registerUser();

        String id = create(user, reading("HEART_RATE", "72", null, null, day(15), "   "));

        assertThat(jdbc.queryForObject("select note from vital_readings where id = ?", String.class,
                UUID.fromString(id))).isNull();
    }

    @Test
    void valuesAndNotesAreEncryptedAtRestButReadableThroughTheApi() throws Exception {
        Session user = registerUser();
        String id = create(user, reading("BLOOD_PRESSURE", "121", "79", null, day(15), "private note text"));

        Map<String, Object> row = jdbc.queryForMap(
                "select value_primary, value_secondary, note from vital_readings where id = ?", UUID.fromString(id));
        assertThat((String) row.get("value_primary")).startsWith("v1.");
        assertThat((String) row.get("value_secondary")).startsWith("v1.");
        assertThat((String) row.get("note")).startsWith("v1.").doesNotContain("private");

        HttpResponse<String> response = send("GET", "/api/v1/vitals/" + id, null, user);
        assertThat(number(response.body(), "value")).isEqualTo("121");
        assertThat(field(response.body(), "note")).isEqualTo("private note text");
    }

    @Test
    void theCallerCannotChooseWhoOwnsAReading() throws Exception {
        Session caller = registerUser();
        Session other = registerUser();
        String otherId = field(getAs("/api/v1/me", other.accessToken()).body(), "id");

        HttpResponse<String> response = send("POST", "/api/v1/vitals",
                "{\"userId\":\"" + otherId + "\",\"type\":\"HEART_RATE\",\"value\":70,\"measuredAt\":\"" + day(15) + "\"}",
                caller);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(number(send("GET", "/api/v1/vitals", null, caller).body(), "totalItems")).isEqualTo("1");
        assertThat(number(send("GET", "/api/v1/vitals", null, other).body(), "totalItems")).isEqualTo("0");
    }

    // ---------- read, update, delete ----------

    @Test
    void theOwnerCanReadAReading() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15);

        HttpResponse<String> response = send("GET", "/api/v1/vitals/" + id, null, user);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(field(response.body(), "id")).isEqualTo(id);
    }

    @Test
    void anotherUserGetsNotFoundForRead() throws Exception {
        Session owner = registerUser();
        Session intruder = registerUser();
        String id = createHeartRate(owner, 15);

        assertThat(send("GET", "/api/v1/vitals/" + id, null, intruder).statusCode()).isEqualTo(404);
    }

    @Test
    void anotherUserCannotUpdateAReading() throws Exception {
        Session owner = registerUser();
        Session intruder = registerUser();
        String id = createHeartRate(owner, 15);

        HttpResponse<String> attempt = send("PUT", "/api/v1/vitals/" + id,
                reading("HEART_RATE", "99", null, null, day(15), "hacked"), intruder);

        assertThat(attempt.statusCode()).isEqualTo(404);
        HttpResponse<String> unchanged = send("GET", "/api/v1/vitals/" + id, null, owner);
        assertThat(number(unchanged.body(), "value")).isEqualTo("72");
    }

    @Test
    void anotherUserCannotDeleteAReading() throws Exception {
        Session owner = registerUser();
        Session intruder = registerUser();
        String id = createHeartRate(owner, 15);

        assertThat(send("DELETE", "/api/v1/vitals/" + id, null, intruder).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/v1/vitals/" + id, null, owner).statusCode()).isEqualTo(200);
    }

    @Test
    void theOwnerCanUpdateAReading() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15);

        HttpResponse<String> response = send("PUT", "/api/v1/vitals/" + id,
                reading("HEART_RATE", "75", null, null, day(14), "resting"), user);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(number(response.body(), "value")).isEqualTo("75");
        assertThat(field(response.body(), "measuredAt")).isEqualTo(day(14));
        assertThat(field(response.body(), "note")).isEqualTo("resting");
        assertThat(number(send("GET", "/api/v1/vitals/" + id, null, user).body(), "value")).isEqualTo("75");
    }

    @Test
    void updatesAreChecked() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15);

        HttpResponse<String> response = send("PUT", "/api/v1/vitals/" + id,
                reading("HEART_RATE", "5", null, null, day(15), null), user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(number(send("GET", "/api/v1/vitals/" + id, null, user).body(), "value")).isEqualTo("72");
    }

    @Test
    void aDeletedReadingIsGone() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15);

        assertThat(send("DELETE", "/api/v1/vitals/" + id, null, user).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/v1/vitals/" + id, null, user).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/v1/vitals/" + id, null, user).statusCode()).isEqualTo(404);
    }

    @Test
    void aMalformedIdGetsACleanError() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("GET", "/api/v1/vitals/not-a-uuid", null, user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid value");
    }

    // ---------- list ----------

    @Test
    void listsNewestFirstWithPaging() throws Exception {
        Session user = registerUser();
        for (int day = 11; day <= 15; day++) {
            createHeartRate(user, day);
        }

        HttpResponse<String> first = send("GET", "/api/v1/vitals?page=0&size=2", null, user);
        HttpResponse<String> second = send("GET", "/api/v1/vitals?page=1&size=2", null, user);
        HttpResponse<String> third = send("GET", "/api/v1/vitals?page=2&size=2", null, user);

        assertThat(number(first.body(), "totalItems")).isEqualTo("5");
        assertThat(number(first.body(), "totalPages")).isEqualTo("3");
        assertThat(strings(first.body(), "measuredAt")).containsExactly(day(15), day(14));
        assertThat(strings(second.body(), "measuredAt")).containsExactly(day(13), day(12));
        assertThat(strings(third.body(), "measuredAt")).containsExactly(day(11));
    }

    @Test
    void listsOnlyTheCallersOwnReadings() throws Exception {
        Session first = registerUser();
        Session second = registerUser();
        createHeartRate(first, 14);
        createHeartRate(first, 15);
        createHeartRate(second, 15);

        assertThat(number(send("GET", "/api/v1/vitals", null, first).body(), "totalItems")).isEqualTo("2");
        assertThat(number(send("GET", "/api/v1/vitals", null, second).body(), "totalItems")).isEqualTo("1");
    }

    @Test
    void filtersByTypeAndByTimeRange() throws Exception {
        Session user = registerUser();
        create(user, reading("BLOOD_PRESSURE", "120", "80", null, day(11), null));
        createHeartRate(user, 12);
        createHeartRate(user, 13);
        createHeartRate(user, 14);

        HttpResponse<String> byType = send("GET", "/api/v1/vitals?type=HEART_RATE", null, user);
        HttpResponse<String> byRange = send("GET",
                "/api/v1/vitals?type=HEART_RATE&from=2026-01-13T00:00:00Z&to=2026-01-14T23:59:59Z", null, user);

        assertThat(number(byType.body(), "totalItems")).isEqualTo("3");
        assertThat(number(byRange.body(), "totalItems")).isEqualTo("2");
    }

    @Test
    void badListParametersAreRefusedCleanly() throws Exception {
        Session user = registerUser();

        assertThat(send("GET", "/api/v1/vitals?size=1000", null, user).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/v1/vitals?size=0", null, user).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/v1/vitals?page=-1", null, user).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/v1/vitals?from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z", null, user)
                .statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/v1/vitals?type=NOPE", null, user).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/v1/vitals?from=yesterday", null, user).statusCode()).isEqualTo(400);
    }
}