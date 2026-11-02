package com.vitalsafe.api.fhir;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static com.vitalsafe.api.support.JsonText.count;
import static com.vitalsafe.api.support.JsonText.number;
import static org.assertj.core.api.Assertions.assertThat;

class FhirApiTest extends IntegrationTestBase {

    private static String day(int dayOfJanuary) {
        return String.format("2026-01-%02dT08:00:00Z", dayOfJanuary);
    }

    private HttpResponse<String> send(String method, String path, String body, Session session,
                                      Map<String, String> extraHeaders) throws Exception {
        java.util.Map<String, String> headers = new java.util.HashMap<>(extraHeaders);
        headers.put("Authorization", "Bearer " + session.accessToken());
        return call(method, path, body, headers);
    }

    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        return send(method, path, body, session, Map.of());
    }

    private String createReading(Session session, String json) throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/vitals", json, session);
        assertThat(response.statusCode()).as("creating a reading: " + response.body()).isEqualTo(201);
        return field(response.body(), "id");
    }

    private String createHeartRate(Session session, int dayOfJanuary, String note) throws Exception {
        String noteJson = note == null ? "" : ",\"note\":\"" + note + "\"";
        return createReading(session,
                "{\"type\":\"HEART_RATE\",\"value\":72,\"measuredAt\":\"" + day(dayOfJanuary) + "\"" + noteJson + "}");
    }

    private String userIdOf(Session session) throws Exception {
        return field(getAs("/api/v1/me", session.accessToken()).body(), "id");
    }

    private HttpResponse<String> importJson(Session session, String body) throws Exception {
        return send("POST", "/api/v1/vitals/fhir", body, session, Map.of("Content-Type", "application/fhir+json"));
    }

    // ---------- one reading as an Observation ----------

    @Test
    void aReadingIsReturnedAsAFhirObservation() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15, null);

        HttpResponse<String> response = send("GET", "/api/v1/vitals/" + id + "/fhir", null, user);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/fhir+json"));
        assertThat(response.body()).contains("\"resourceType\":\"Observation\"")
                .contains("\"code\":\"8867-4\"")
                .contains("\"code\":\"/min\"")
                .contains("\"reference\":\"Patient/" + userIdOf(user) + "\"");
    }

    @Test
    void anotherUserCannotFetchSomeoneElsesObservation() throws Exception {
        Session owner = registerUser();
        Session intruder = registerUser();
        String id = createHeartRate(owner, 15, null);

        assertThat(send("GET", "/api/v1/vitals/" + id + "/fhir", null, intruder).statusCode()).isEqualTo(404);
    }

    @Test
    void fhirEndpointsNeedALogin() throws Exception {
        assertThat(get("/api/v1/fhir/export").statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/vitals/fhir", "{}").statusCode()).isEqualTo(401);
    }

    // ---------- export ----------

    @Test
    void theExportIsABundleOfOnlyTheCallersReadings() throws Exception {
        Session user = registerUser();
        Session other = registerUser();
        createHeartRate(user, 13, "secret-note");
        createHeartRate(user, 14, null);
        createReading(user, "{\"type\":\"BODY_WEIGHT\",\"value\":70,\"measuredAt\":\"" + day(15) + "\"}");
        createHeartRate(other, 15, "not-yours");

        HttpResponse<String> response = send("GET", "/api/v1/fhir/export", null, user);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/fhir+json"));
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(value -> assertThat(value).contains("vitalsafe-fhir-export.json"));
        assertThat(response.body()).contains("\"resourceType\":\"Bundle\"").contains("\"type\":\"collection\"");
        assertThat(count(response.body(), "\"resourceType\":\"Observation\"")).isEqualTo(3);
        assertThat(count(response.body(), "\"resourceType\":\"Patient\"")).isEqualTo(1);
        assertThat(response.body()).contains("secret-note").doesNotContain("not-yours").doesNotContain(user.email());
    }

    @Test
    void theExportCanBeLimitedToATimeRange() throws Exception {
        Session user = registerUser();
        createHeartRate(user, 12, null);
        createHeartRate(user, 14, null);
        createHeartRate(user, 15, null);

        HttpResponse<String> response = send("GET",
                "/api/v1/fhir/export?from=2026-01-13T00:00:00Z&to=2026-01-31T00:00:00Z", null, user);

        assertThat(count(response.body(), "\"resourceType\":\"Observation\"")).isEqualTo(2);
    }

    @Test
    void anEmptyExportIsAValidBundleWithOnlyThePatient() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("GET", "/api/v1/fhir/export", null, user);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(count(response.body(), "\"resourceType\":\"Observation\"")).isZero();
        assertThat(count(response.body(), "\"resourceType\":\"Patient\"")).isEqualTo(1);
    }

    @Test
    void anExportRangeThatIsBackwardsIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("GET",
                "/api/v1/fhir/export?from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z", null, user);

        assertThat(response.statusCode()).isEqualTo(400);
    }

    // ---------- import ----------

    @Test
    void anExportedObservationCanBeImportedAgain() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15, null);
        String observation = send("GET", "/api/v1/vitals/" + id + "/fhir", null, user).body();

        HttpResponse<String> imported = importJson(user, observation);

        assertThat(imported.statusCode()).isEqualTo(201);
        assertThat(field(imported.body(), "type")).isEqualTo("HEART_RATE");
        assertThat(number(imported.body(), "value")).isEqualTo("72");
        assertThat(field(imported.body(), "id")).isNotEqualTo(id);
        HttpResponse<String> list = send("GET", "/api/v1/vitals", null, user);
        assertThat(number(list.body(), "totalItems")).isEqualTo("2");
    }

    @Test
    void aBloodPressureWithANoteSurvivesTheRoundTrip() throws Exception {
        Session user = registerUser();
        String id = createReading(user, "{\"type\":\"BLOOD_PRESSURE\",\"value\":118,\"secondaryValue\":76,"
                + "\"measuredAt\":\"" + day(15) + "\",\"note\":\"morning\"}");
        String observation = send("GET", "/api/v1/vitals/" + id + "/fhir", null, user).body();

        HttpResponse<String> imported = importJson(user, observation);

        assertThat(imported.statusCode()).isEqualTo(201);
        assertThat(number(imported.body(), "value")).isEqualTo("118");
        assertThat(number(imported.body(), "secondaryValue")).isEqualTo("76");
        assertThat(field(imported.body(), "note")).isEqualTo("morning");
    }

    @Test
    void theSubjectInsideTheFileIsIgnoredAndTheReadingBelongsToTheCaller() throws Exception {
        Session caller = registerUser();
        Session other = registerUser();
        String id = createHeartRate(caller, 15, null);
        String observation = send("GET", "/api/v1/vitals/" + id + "/fhir", null, caller).body();
        String spoofed = observation.replace("Patient/" + userIdOf(caller), "Patient/" + userIdOf(other));
        assertThat(spoofed).isNotEqualTo(observation);

        HttpResponse<String> imported = importJson(caller, spoofed);

        assertThat(imported.statusCode()).isEqualTo(201);
        assertThat(number(send("GET", "/api/v1/vitals", null, caller).body(), "totalItems")).isEqualTo("2");
        assertThat(number(send("GET", "/api/v1/vitals", null, other).body(), "totalItems")).isEqualTo("0");
    }

    @Test
    void importAlsoWorksWithAPlainJsonContentType() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15, null);
        String observation = send("GET", "/api/v1/vitals/" + id + "/fhir", null, user).body();

        HttpResponse<String> imported = send("POST", "/api/v1/vitals/fhir", observation, user);

        assertThat(imported.statusCode()).isEqualTo(201);
    }

    @Test
    void anUnknownLoincCodeIsRefused() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15, null);
        String observation = send("GET", "/api/v1/vitals/" + id + "/fhir", null, user).body();

        HttpResponse<String> response = importJson(user, observation.replace("8867-4", "1234-5"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).contains("supported LOINC");
    }

    @Test
    void unitsThatAreNotUcumAreRefused() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15, null);
        String observation = send("GET", "/api/v1/vitals/" + id + "/fhir", null, user).body();

        HttpResponse<String> response = importJson(user,
                observation.replace("http://unitsofmeasure.org", "http://example.org/units"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).contains("UCUM");
    }

    @Test
    void importedValuesGoThroughTheSameRulesAsTheNormalApi() throws Exception {
        Session user = registerUser();
        String id = createHeartRate(user, 15, null);
        String observation = send("GET", "/api/v1/vitals/" + id + "/fhir", null, user).body();
        String implausible = observation.replace("\"value\":72", "\"value\":5");
        assertThat(implausible).isNotEqualTo(observation);

        HttpResponse<String> response = importJson(user, implausible);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"value\"").contains("between 20 and 250");
        assertThat(number(send("GET", "/api/v1/vitals", null, user).body(), "totalItems")).isEqualTo("1");
    }

    @Test
    void garbageIsRefusedWithoutEchoingIt() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importJson(user, "this-is-not-json secret-token-123");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).isEqualTo("Not a valid FHIR R4 JSON resource");
        assertThat(response.body()).doesNotContain("secret-token-123");
    }

    @Test
    void onlyObservationsCanBeImported() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importJson(user, "{\"resourceType\":\"Patient\",\"id\":\"x\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).isEqualTo("Only Observation resources can be imported");
    }

    @Test
    void anOversizedBodyIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importJson(user, "x".repeat(100_001));

        assertThat(response.statusCode()).isEqualTo(413);
    }
}