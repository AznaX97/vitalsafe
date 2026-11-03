package com.vitalsafe.api.vitals.csv;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static com.vitalsafe.api.support.JsonText.count;
import static com.vitalsafe.api.support.JsonText.number;
import static org.assertj.core.api.Assertions.assertThat;

class VitalCsvApiTest extends IntegrationTestBase {

    private static final String HEADER = "type,value,secondaryValue,unit,measuredAt,note\n";

    private static String day(int dayOfJanuary) {
        return String.format("2026-01-%02dT08:00:00Z", dayOfJanuary);
    }

    private HttpResponse<String> importCsv(Session session, String csv) throws Exception {
        return call("POST", "/api/v1/vitals/import", csv,
                Map.of("Authorization", "Bearer " + session.accessToken(), "Content-Type", "text/csv"));
    }

    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        return call(method, path, body, Map.of("Authorization", "Bearer " + session.accessToken()));
    }

    private int total(Session session) throws Exception {
        return Integer.parseInt(number(send("GET", "/api/v1/vitals?size=100", null, session).body(), "totalItems"));
    }

    private String threeReadings() {
        return HEADER
                + "BLOOD_PRESSURE,120,80,," + day(10) + ",morning\n"
                + "HEART_RATE,72,,," + day(11) + ",\n"
                + "BLOOD_GLUCOSE,5.5,,mmol/L," + day(12) + ",\n";
    }

    // ---------- import ----------

    @Test
    void importsAFileOfReadings() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, threeReadings());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(number(response.body(), "imported")).isEqualTo("3");
        assertThat(number(response.body(), "skippedDuplicates")).isEqualTo("0");
        assertThat(total(user)).isEqualTo(3);
        HttpResponse<String> list = send("GET", "/api/v1/vitals", null, user);
        assertThat(list.body()).contains("BLOOD_PRESSURE").contains("\"unit\":\"mmol/L\"").contains("morning");
    }

    @Test
    void columnOrderAndHeaderCaseDoNotMatter() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user,
                "MeasuredAt,VALUE,Type\n" + day(10) + ",72,heart_rate\n");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(number(response.body(), "imported")).isEqualTo("1");
    }

    @Test
    void quotedNotesWithCommasQuotesAndLineBreaksAreKept() throws Exception {
        Session user = registerUser();
        String note = "\"line one, \"\"quoted\"\"\nline two\"";

        HttpResponse<String> response = importCsv(user, HEADER + "HEART_RATE,72,,," + day(10) + "," + note + "\n");

        assertThat(response.statusCode()).isEqualTo(200);
        HttpResponse<String> list = send("GET", "/api/v1/vitals", null, user);
        assertThat(list.body()).contains("line one, \\\"quoted\\\"\\nline two");
    }

    @Test
    void aByteOrderMarkAndWindowsLineEndingsAreAccepted() throws Exception {
        Session user = registerUser();
        String csv = "\uFEFFtype,value,measuredAt\r\nHEART_RATE,72," + day(10) + "\r\nHEART_RATE,73," + day(11) + "\r\n";

        HttpResponse<String> response = importCsv(user, csv);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(number(response.body(), "imported")).isEqualTo("2");
    }

    @Test
    void blankLinesAreIgnored() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, HEADER + "\nHEART_RATE,72,,," + day(10) + ",\n\n");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(number(response.body(), "imported")).isEqualTo("1");
    }

    @Test
    void oneBadRowRefusesTheWholeFileAndNothingIsImported() throws Exception {
        Session user = registerUser();
        String csv = HEADER
                + "HEART_RATE,72,,," + day(10) + ",\n"
                + "HEART_RATE,5,,," + day(11) + ",\n"
                + "HEART_RATE,73,,," + day(12) + ",\n";

        HttpResponse<String> response = importCsv(user, csv);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).contains("nothing was imported");
        assertThat(response.body()).contains("\"row\":3").contains("between 20 and 250");
        assertThat(total(user)).isZero();
    }

    @Test
    void everyBadRowIsReportedWithItsRowNumber() throws Exception {
        Session user = registerUser();
        String csv = HEADER
                + "MOOD,5,,," + day(10) + ",\n"
                + "HEART_RATE,72,,," + day(11) + ",\n"
                + "BLOOD_GLUCOSE,100,,,"  + day(12) + ",\n";

        HttpResponse<String> response = importCsv(user, csv);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(number(response.body(), "rowErrors")).isEqualTo("2");
        assertThat(response.body()).contains("\"row\":2").contains("\"row\":4").doesNotContain("\"row\":3");
    }

    @Test
    void cellProblemsAreDescribedTogetherForOneRow() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, HEADER + "MOOD,abc,,,not-a-date,\n");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"type\"").contains("\"value\"").contains("\"measuredAt\"");
    }

    @Test
    void theSameRulesApplyAsInTheNormalApi() throws Exception {
        Session user = registerUser();

        HttpResponse<String> future = importCsv(user, HEADER + "HEART_RATE,72,,,2999-01-01T00:00:00Z,\n");
        HttpResponse<String> glucoseNoUnit = importCsv(user, HEADER + "BLOOD_GLUCOSE,100,,," + day(10) + ",\n");

        assertThat(future.statusCode()).isEqualTo(400);
        assertThat(future.body()).contains("cannot be in the future");
        assertThat(glucoseNoUnit.statusCode()).isEqualTo(400);
        assertThat(glucoseNoUnit.body()).contains("\"unit\"");
    }

    @Test
    void aRowWithTheWrongNumberOfColumnsIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, HEADER + "HEART_RATE,72\n");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Expected 6 columns");
    }

    @Test
    void anUnknownColumnIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, "type,value,measuredAt,mood\nHEART_RATE,72," + day(10) + ",happy\n");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).contains("Column 4 is not recognised");
    }

    @Test
    void aMissingRequiredColumnIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, "type,value\nHEART_RATE,72\n");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).isEqualTo("Missing required column: measuredAt");
    }

    @Test
    void aRepeatedColumnIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, "type,value,value,measuredAt\nHEART_RATE,72,73," + day(10) + "\n");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).contains("repeats");
    }

    @Test
    void anEmptyFileAndAHeaderOnlyFileAreRefused() throws Exception {
        Session user = registerUser();

        assertThat(importCsv(user, "").statusCode()).isEqualTo(400);
        HttpResponse<String> headerOnly = importCsv(user, HEADER);
        assertThat(headerOnly.statusCode()).isEqualTo(400);
        assertThat(field(headerOnly.body(), "error")).isEqualTo("The file has no data rows");
    }

    @Test
    void malformedCsvIsRefusedWithoutRepeatingTheContent() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, "type,value\n\"secret-unterminated");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("secret-unterminated");
    }

    // ---------- duplicates and ownership ----------

    @Test
    void importingTheSameFileTwiceSkipsTheDuplicates() throws Exception {
        Session user = registerUser();
        importCsv(user, threeReadings());

        HttpResponse<String> second = importCsv(user, threeReadings());

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(number(second.body(), "imported")).isEqualTo("0");
        assertThat(number(second.body(), "skippedDuplicates")).isEqualTo("3");
        assertThat(total(user)).isEqualTo(3);
    }

    @Test
    void repeatsInsideOneFileAreSkipped() throws Exception {
        Session user = registerUser();
        String csv = HEADER + "HEART_RATE,72,,," + day(10) + ",\nHEART_RATE,75,,," + day(10) + ",\n";

        HttpResponse<String> response = importCsv(user, csv);

        assertThat(number(response.body(), "imported")).isEqualTo("1");
        assertThat(number(response.body(), "skippedDuplicates")).isEqualTo("1");
    }

    @Test
    void anotherUsersReadingsDoNotCountAsDuplicates() throws Exception {
        Session first = registerUser();
        Session second = registerUser();
        importCsv(first, threeReadings());

        HttpResponse<String> response = importCsv(second, threeReadings());

        assertThat(number(response.body(), "imported")).isEqualTo("3");
        assertThat(total(first)).isEqualTo(3);
        assertThat(total(second)).isEqualTo(3);
    }

    @Test
    void importedValuesAreEncryptedAtRest() throws Exception {
        Session user = registerUser();
        importCsv(user, HEADER + "BLOOD_PRESSURE,121,79,," + day(10) + ",private note text\n");

        Map<String, Object> row = jdbc.queryForMap(
                "select value_primary, note from vital_readings where user_id = (select id from users where email = ?)",
                user.email());

        assertThat((String) row.get("value_primary")).startsWith("v1.");
        assertThat((String) row.get("note")).startsWith("v1.").doesNotContain("private");
    }

    // ---------- limits and access ----------

    @Test
    void moreThanOneThousandDataRowsAreRefused() throws Exception {
        Session user = registerUser();
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < 1001; i++) {
            csv.append("HEART_RATE,72,,,").append(day(10)).append(",\n");
        }

        HttpResponse<String> response = importCsv(user, csv.toString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response.body(), "error")).contains("more than 1000 data rows");
    }

    @Test
    void anOversizedFileIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = importCsv(user, HEADER + "x".repeat(VitalCsvService.MAX_BYTES + 100));

        assertThat(response.statusCode()).isEqualTo(413);
    }

    @Test
    void aWrongContentTypeIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = call("POST", "/api/v1/vitals/import", threeReadings(),
                Map.of("Authorization", "Bearer " + user.accessToken(), "Content-Type", "application/json"));

        assertThat(response.statusCode()).isEqualTo(415);
    }

    @Test
    void importAndExportNeedALogin() throws Exception {
        assertThat(call("POST", "/api/v1/vitals/import", threeReadings(), Map.of("Content-Type", "text/csv")).statusCode())
                .isEqualTo(401);
        assertThat(get("/api/v1/vitals/export").statusCode()).isEqualTo(401);
    }

    // ---------- export ----------

    @Test
    void exportsOnlyTheCallersReadingsWithAHeader() throws Exception {
        Session user = registerUser();
        Session other = registerUser();
        importCsv(user, threeReadings());
        importCsv(other, HEADER + "HEART_RATE,99,,," + day(10) + ",not-yours\n");

        HttpResponse<String> response = send("GET", "/api/v1/vitals/export", null, user);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("text/csv"));
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(value -> assertThat(value).contains("vitalsafe-readings.csv"));
        String[] lines = response.body().split("\r\n");
        assertThat(lines).hasSize(4);
        assertThat(lines[0]).isEqualTo("type,value,secondaryValue,unit,measuredAt,note");
        assertThat(response.body()).contains("BLOOD_PRESSURE,120,80,mm[Hg]," + day(10) + ",morning")
                .doesNotContain("not-yours");
    }

    @Test
    void notesThatWouldRunAsFormulasAreNeutralisedInTheExport() throws Exception {
        Session user = registerUser();
        send("POST", "/api/v1/vitals",
                "{\"type\":\"HEART_RATE\",\"value\":72,\"measuredAt\":\"" + day(10) + "\",\"note\":\"=1+1\"}", user);

        HttpResponse<String> response = send("GET", "/api/v1/vitals/export", null, user);

        assertThat(response.body()).contains(",'=1+1").doesNotContain(",=1+1");
    }

    @Test
    void notesWithCommasAreQuotedInTheExport() throws Exception {
        Session user = registerUser();
        importCsv(user, HEADER + "HEART_RATE,72,,," + day(10) + ",\"a, b\"\n");

        HttpResponse<String> response = send("GET", "/api/v1/vitals/export", null, user);

        assertThat(response.body()).contains(",\"a, b\"");
    }

    @Test
    void anExportCanBeImportedByAnotherAccount() throws Exception {
        Session first = registerUser();
        Session second = registerUser();
        importCsv(first, threeReadings());
        String exported = send("GET", "/api/v1/vitals/export", null, first).body();

        HttpResponse<String> response = importCsv(second, exported);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(number(response.body(), "imported")).isEqualTo("3");
        assertThat(total(second)).isEqualTo(3);
    }

    @Test
    void theExportCanBeLimitedToATimeRange() throws Exception {
        Session user = registerUser();
        importCsv(user, threeReadings());

        HttpResponse<String> response = send("GET",
                "/api/v1/vitals/export?from=" + day(11) + "&to=" + day(12), null, user);

        assertThat(response.body().split("\r\n")).hasSize(3);
        assertThat(count(response.body(), "HEART_RATE")).isEqualTo(1);
    }

    @Test
    void aBackwardsExportRangeIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = send("GET",
                "/api/v1/vitals/export?from=" + day(12) + "&to=" + day(11), null, user);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(UUID.randomUUID()).isNotNull();
    }
}