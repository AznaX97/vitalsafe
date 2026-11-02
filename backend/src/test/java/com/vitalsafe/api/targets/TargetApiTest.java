package com.vitalsafe.api.targets;

import com.vitalsafe.api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static com.vitalsafe.api.support.JsonText.count;
import static com.vitalsafe.api.support.JsonText.number;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetApiTest extends IntegrationTestBase {

    private HttpResponse<String> send(String method, String path, String body, Session session) throws Exception {
        return call(method, path, body, Map.of("Authorization", "Bearer " + session.accessToken()));
    }

    private static String target(String unit, String low, String high, String lowSecondary, String highSecondary) {
        StringBuilder body = new StringBuilder("{");
        String separator = "";
        if (unit != null) {
            body.append("\"unit\":\"").append(unit).append("\"");
            separator = ",";
        }
        String[][] bounds = {{"lowValue", low}, {"highValue", high},
                {"lowSecondaryValue", lowSecondary}, {"highSecondaryValue", highSecondary}};
        for (String[] bound : bounds) {
            if (bound[1] != null) {
                body.append(separator).append("\"").append(bound[0]).append("\":").append(bound[1]);
                separator = ",";
            }
        }
        return body.append("}").toString();
    }

    private HttpResponse<String> put(Session session, String type, String body) throws Exception {
        return send("PUT", "/api/v1/targets/" + type, body, session);
    }

    // ---------- access ----------

    @Test
    void targetsNeedALogin() throws Exception {
        assertThat(get("/api/v1/targets").statusCode()).isEqualTo(401);
        assertThat(call("PUT", "/api/v1/targets/HEART_RATE", target(null, "50", "100", null, null), Map.of())
                .statusCode()).isEqualTo(401);
    }

    // ---------- set and read ----------

    @Test
    void setsAHeartRateRangeWithTheDefaultUnit() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "HEART_RATE", target(null, "50", "100", null, null));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(field(response.body(), "type")).isEqualTo("HEART_RATE");
        assertThat(field(response.body(), "unit")).isEqualTo("/min");
        assertThat(number(response.body(), "lowValue")).isEqualTo("50");
        assertThat(number(response.body(), "highValue")).isEqualTo("100");
    }

    @Test
    void setsABloodPressureRangeWithBothParts() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "BLOOD_PRESSURE", target(null, "90", "130", "60", "85"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(number(response.body(), "lowValue")).isEqualTo("90");
        assertThat(number(response.body(), "highValue")).isEqualTo("130");
        assertThat(number(response.body(), "lowSecondaryValue")).isEqualTo("60");
        assertThat(number(response.body(), "highSecondaryValue")).isEqualTo("85");
    }

    @Test
    void settingAgainReplacesTheRangeAndClearsBoundsThatWereLeftOut() throws Exception {
        Session user = registerUser();
        put(user, "HEART_RATE", target(null, "50", "100", null, null));

        HttpResponse<String> replaced = put(user, "HEART_RATE", target(null, "55", null, null, null));

        assertThat(number(replaced.body(), "lowValue")).isEqualTo("55");
        assertThat(number(replaced.body(), "highValue")).isNull();
        HttpResponse<String> list = send("GET", "/api/v1/targets", null, user);
        assertThat(count(list.body(), "\"type\":\"HEART_RATE\"")).isEqualTo(1);
    }

    @Test
    void glucoseCanHaveOneRangePerUnit() throws Exception {
        Session user = registerUser();

        assertThat(put(user, "BLOOD_GLUCOSE", target("mg/dL", "70", "140", null, null)).statusCode()).isEqualTo(200);
        assertThat(put(user, "BLOOD_GLUCOSE", target("mmol/L", "4", "8", null, null)).statusCode()).isEqualTo(200);

        HttpResponse<String> list = send("GET", "/api/v1/targets", null, user);
        assertThat(count(list.body(), "\"type\":\"BLOOD_GLUCOSE\"")).isEqualTo(2);
    }

    @Test
    void theBoundsAreEncryptedInTheDatabase() throws Exception {
        Session user = registerUser();
        put(user, "HEART_RATE", target(null, "50", "100", null, null));

        Map<String, Object> row = jdbc.queryForMap(
                "select low_primary, high_primary, low_secondary from target_ranges "
                        + "where user_id = (select id from users where email = ?)", user.email());

        assertThat((String) row.get("low_primary")).startsWith("v1.").isNotEqualTo("50");
        assertThat((String) row.get("high_primary")).startsWith("v1.").isNotEqualTo("100");
        assertThat(row.get("low_secondary")).isNull();
    }

    @Test
    void listsOnlyTheCallersOwnRanges() throws Exception {
        Session first = registerUser();
        Session second = registerUser();
        put(first, "HEART_RATE", target(null, "50", "100", null, null));
        put(first, "BODY_WEIGHT", target(null, "60", "80", null, null));
        put(second, "HEART_RATE", target(null, "40", "90", null, null));

        assertThat(count(send("GET", "/api/v1/targets", null, first).body(), "\"type\":")).isEqualTo(2);
        HttpResponse<String> theirs = send("GET", "/api/v1/targets", null, second);
        assertThat(count(theirs.body(), "\"type\":")).isEqualTo(1);
        assertThat(number(theirs.body(), "lowValue")).isEqualTo("40");
    }

    // ---------- delete ----------

    @Test
    void aRangeCanBeDeletedAndThenIsGone() throws Exception {
        Session user = registerUser();
        put(user, "HEART_RATE", target(null, "50", "100", null, null));

        assertThat(send("DELETE", "/api/v1/targets/HEART_RATE", null, user).statusCode()).isEqualTo(204);
        assertThat(send("DELETE", "/api/v1/targets/HEART_RATE", null, user).statusCode()).isEqualTo(404);
        assertThat(count(send("GET", "/api/v1/targets", null, user).body(), "\"type\":")).isZero();
    }

    @Test
    void anotherUserCannotDeleteSomeoneElsesRange() throws Exception {
        Session owner = registerUser();
        Session intruder = registerUser();
        put(owner, "HEART_RATE", target(null, "50", "100", null, null));

        assertThat(send("DELETE", "/api/v1/targets/HEART_RATE", null, intruder).statusCode()).isEqualTo(404);
        assertThat(count(send("GET", "/api/v1/targets", null, owner).body(), "\"type\":")).isEqualTo(1);
    }

    @Test
    void deletingAGlucoseRangeNeedsTheUnit() throws Exception {
        Session user = registerUser();
        put(user, "BLOOD_GLUCOSE", target("mg/dL", "70", "140", null, null));

        assertThat(send("DELETE", "/api/v1/targets/BLOOD_GLUCOSE", null, user).statusCode()).isEqualTo(400);
        assertThat(send("DELETE", "/api/v1/targets/BLOOD_GLUCOSE?unit=mg/dL", null, user).statusCode()).isEqualTo(204);
    }

    // ---------- validation ----------

    @Test
    void aRangeWithNoBoundsIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "HEART_RATE", "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Set at least one bound");
    }

    @Test
    void aLowBoundAboveTheHighBoundIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "HEART_RATE", target(null, "100", "50", null, null));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("must not be higher");
    }

    @Test
    void anImpossibleBoundIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "HEART_RATE", target(null, "50", "900", null, null));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"highValue\"").contains("between 20 and 250");
    }

    @Test
    void aDecimalForAWholeNumberTypeIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "HEART_RATE", target(null, "50.5", "100", null, null));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("whole number");
    }

    @Test
    void aSecondaryBoundIsRefusedForTypesThatHaveNone() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "HEART_RATE", target(null, "50", "100", null, "80"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("not used");
    }

    @Test
    void aWrongUnitIsRefused() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "HEART_RATE", target("bpm", "50", "100", null, null));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("/min");
    }

    @Test
    void glucoseNeedsAUnitAndItsLimitsFollowTheUnit() throws Exception {
        Session user = registerUser();

        HttpResponse<String> noUnit = put(user, "BLOOD_GLUCOSE", target(null, "70", "140", null, null));
        HttpResponse<String> wrongScale = put(user, "BLOOD_GLUCOSE", target("mg/dL", null, "5", null, null));

        assertThat(noUnit.statusCode()).isEqualTo(400);
        assertThat(noUnit.body()).contains("\"unit\"");
        assertThat(wrongScale.statusCode()).isEqualTo(400);
        assertThat(wrongScale.body()).contains("\"highValue\"");
    }

    @Test
    void anUnknownTypeInThePathGetsACleanError() throws Exception {
        Session user = registerUser();

        HttpResponse<String> response = put(user, "MOOD", target(null, "1", "5", null, null));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid value");
    }

    // ---------- the database also protects itself ----------

    @Test
    void theDatabaseRefusesARangeWithNoBounds() throws Exception {
        Session user = registerUser();
        UUID userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, user.email());

        assertThatThrownBy(() -> jdbc.update(
                "insert into target_ranges (user_id, type, unit) values (?, 'HEART_RATE', '/min')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAUserDeletesTheirRanges() throws Exception {
        Session user = registerUser();
        put(user, "HEART_RATE", target(null, "50", "100", null, null));

        jdbc.update("delete from users where email = ?", user.email());

        assertThat(jdbc.queryForObject("select count(*) from target_ranges", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from target_ranges where user_id not in (select id from users)", Integer.class)).isZero();
    }
}