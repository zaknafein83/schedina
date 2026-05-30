package it.schedina;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * E2E del modello Calendario/Giornate: schedina 1X2+U/O per giornata
 * e scommessa extra con giocata indipendente. Gira contro Postgres (Dev Services).
 */
@QuarkusTest
class GiornataFlowTest {

    private String token() {
        return given().contentType("application/json")
                .body("{\"email\":\"admin@schedina.it\",\"password\":\"12345678\"}")
                .when().post("/auth/login")
                .then().statusCode(200).extract().jsonPath().getString("accessToken");
    }

    private long post(String auth, String path, String body, int expected) {
        return given().header("Authorization", auth).contentType("application/json").body(body)
                .when().post(path).then().statusCode(expected).extract().jsonPath().getLong("id");
    }

    @Test
    void schedina_1x2_uo_vincente() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long home = post(auth, "/admin/teams", "{\"name\":\"Casa\",\"leagueId\":" + leagueId + "}", 201);
        long away = post(auth, "/admin/teams", "{\"name\":\"Ospite\",\"leagueId\":" + leagueId + "}", 201);

        long g = post(auth, "/admin/giornate",
                "{\"name\":\"Giornata test\",\"openAt\":\"2020-01-01T00:00:00\",\"closeAt\":\"2999-12-31T23:59:59\",\"winningThresholds\":[2]}", 201);
        long m = post(auth, "/admin/matches",
                "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"giornataId\":" + g + ",\"scheduledAt\":\"2999-01-01T20:45:00\",\"overUnderLine\":2.5}", 201);

        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/giornate/" + g + "/open").then().statusCode(200).body("status", equalTo("OPEN"));

        long sched = post(auth, "/schedine",
                "{\"giornataId\":" + g + ",\"pronostici\":[{\"matchId\":" + m + ",\"choice1x2\":\"1\",\"choiceUo\":\"O\"}]}", 201);
        given().header("Authorization", auth).contentType("application/json")
                .post("/schedine/" + sched + "/confirm").then().statusCode(200).body("status", equalTo("CONFIRMED"));

        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/giornate/" + g + "/close").then().statusCode(200);

        // 2-1 → 1X2 = "1", totale 3 > 2.5 → Over
        given().header("Authorization", auth).contentType("application/json")
                .body("{\"homeScore\":2,\"awayScore\":1}")
                .when().put("/admin/matches/" + m + "/result").then().statusCode(200);

        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/giornate/" + g + "/process").then().statusCode(200)
                .body("allScored", is(true)).body("winners", is(1)).body("status", equalTo("PROCESSED"));

        given().header("Authorization", auth).get("/schedine/" + sched).then().statusCode(200)
                .body("status", equalTo("WINNING")).body("correctCount", is(2))
                .body("selezioni[0].correct1x2", is(true)).body("selezioni[0].correctUo", is(true));
    }

    @Test
    void scommessa_extra_con_giocata() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long teamA = post(auth, "/admin/teams", "{\"name\":\"Alpha\",\"leagueId\":" + leagueId + "}", 201);
        long teamB = post(auth, "/admin/teams", "{\"name\":\"Beta\",\"leagueId\":" + leagueId + "}", 201);

        long bet = post(auth, "/admin/scommesse",
                "{\"scope\":\"SEASON\",\"label\":\"Vincitore\",\"market\":\"WINNER\",\"options\":["
                        + "{\"ref\":\"" + teamA + "\",\"label\":\"Alpha\"},{\"ref\":\"" + teamB + "\",\"label\":\"Beta\"}]}", 201);

        // l'utente (admin come utente) gioca su teamA
        given().header("Authorization", auth).contentType("application/json")
                .body("{\"scommessaId\":" + bet + ",\"choiceRef\":\"" + teamA + "\"}")
                .when().post("/scommesse").then().statusCode(201);

        // admin risolve: vince teamA
        given().header("Authorization", auth).contentType("application/json")
                .body("{\"officialResultRef\":\"" + teamA + "\"}")
                .when().patch("/admin/scommesse/" + bet + "/resolve").then().statusCode(200).body("status", equalTo("RESOLVED"));

        // la giocata risulta corretta
        given().header("Authorization", auth).get("/scommesse/mine").then().statusCode(200)
                .body("find { it.scommessaId == " + bet + " }.isCorrect", is(true));
    }
}
