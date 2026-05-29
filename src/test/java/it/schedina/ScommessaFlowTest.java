package it.schedina;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Test end-to-end del nuovo modello Scommessa/Schedina, eseguiti contro l'API REST
 * con un PostgreSQL avviato da Quarkus Dev Services (Testcontainers).
 */
@QuarkusTest
class ScommessaFlowTest {

    private String adminToken() {
        return given()
                .contentType("application/json")
                .body("{\"email\":\"admin@schedina.it\",\"password\":\"12345678\"}")
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .extract().jsonPath().getString("accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long createLeague(String auth, String name) {
        return given().header("Authorization", auth).contentType("application/json")
                .body("{\"name\":\"" + name + "\"}")
        .when().post("/admin/leagues")
        .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private long createRule(String auth, Long leagueId, int requiredBets, String thresholds) {
        String league = leagueId != null ? "\"leagueId\":" + leagueId + "," : "";
        return given().header("Authorization", auth).contentType("application/json")
                .body("{" + league + "\"name\":\"Regola test\",\"requiredBets\":" + requiredBets +
                        ",\"winningThresholds\":" + thresholds + ",\"fullCompletionRequired\":true}")
        .when().post("/admin/rules")
        .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    private long createConcorso(String auth, long ruleId) {
        return given().header("Authorization", auth).contentType("application/json")
                .body("{\"name\":\"Concorso test\",\"kind\":\"MATCHDAY\",\"ruleId\":" + ruleId +
                        ",\"openAt\":\"2020-01-01T00:00:00\",\"closeAt\":\"2999-12-31T23:59:59\"}")
        .when().post("/admin/concorsi")
        .then().statusCode(201).extract().jsonPath().getLong("id");
    }

    @Test
    void manualFlow_producesWinningSchedina() {
        String auth = bearer(adminToken());

        long leagueId = createLeague(auth, "Lega Manuale " + System.nanoTime());
        long ruleId = createRule(auth, leagueId, 1, "[1]");
        long concorsoId = createConcorso(auth, ruleId);

        // Scommessa GOAL/NOGOAL (risoluzione manuale, opzioni autogenerate)
        long betId = given().header("Authorization", auth).contentType("application/json")
                .body("{\"concorsoId\":" + concorsoId + ",\"label\":\"Gol/NoGol\",\"market\":\"GOAL_NOGOAL\"}")
        .when().post("/admin/scommesse")
        .then().statusCode(201)
                .body("targetKind", equalTo("TOKEN"))
                .body("options.size()", is(2))
                .extract().jsonPath().getLong("id");

        // Apri concorso
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + concorsoId + "/open")
                .then().statusCode(200).body("status", equalTo("OPEN"));

        // Crea + conferma schedina
        long schedinaId = given().header("Authorization", auth).contentType("application/json")
                .body("{\"concorsoId\":" + concorsoId + ",\"selezioni\":[{\"betId\":" + betId + ",\"choiceRef\":\"GOAL\"}]}")
        .when().post("/schedine")
        .then().statusCode(201).body("status", equalTo("DRAFT"))
                .extract().jsonPath().getLong("id");

        given().header("Authorization", auth).contentType("application/json")
                .post("/schedine/" + schedinaId + "/confirm")
                .then().statusCode(200).body("status", equalTo("CONFIRMED"));

        // Chiudi, risolvi la scommessa, elabora
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + concorsoId + "/close")
                .then().statusCode(200).body("status", equalTo("CLOSED"));

        given().header("Authorization", auth).contentType("application/json")
                .body("{\"officialResultRef\":\"GOAL\"}")
        .when().patch("/admin/scommesse/" + betId + "/resolve")
        .then().statusCode(200).body("status", equalTo("RESOLVED"));

        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + concorsoId + "/process")
                .then().statusCode(200)
                .body("allSettled", is(true))
                .body("winners", is(1))
                .body("status", equalTo("PROCESSED"));

        // La schedina deve risultare vincente
        given().header("Authorization", auth).get("/schedine/" + schedinaId)
                .then().statusCode(200)
                .body("status", equalTo("WINNING"))
                .body("isWinner", is(true))
                .body("correctCount", is(1))
                .body("selezioni[0].isCorrect", is(true));
    }

    @Test
    void autoResolution_fromMatchScore() {
        String auth = bearer(adminToken());

        long leagueId = createLeague(auth, "Lega Auto " + System.nanoTime());
        long teamHome = given().header("Authorization", auth).contentType("application/json")
                .body("{\"name\":\"Casa\",\"leagueId\":" + leagueId + "}")
                .when().post("/admin/teams").then().statusCode(201).extract().jsonPath().getLong("id");
        long teamAway = given().header("Authorization", auth).contentType("application/json")
                .body("{\"name\":\"Ospite\",\"leagueId\":" + leagueId + "}")
                .when().post("/admin/teams").then().statusCode(201).extract().jsonPath().getLong("id");

        long matchId = given().header("Authorization", auth).contentType("application/json")
                .body("{\"homeTeamId\":" + teamHome + ",\"awayTeamId\":" + teamAway +
                        ",\"scheduledAt\":\"2999-01-01T20:45:00\"}")
        .when().post("/admin/matches")
        .then().statusCode(201).extract().jsonPath().getLong("id");

        long ruleId = createRule(auth, leagueId, 1, "[1]");
        long concorsoId = createConcorso(auth, ruleId);

        // Scommessa 1X2 collegata alla partita → risoluzione AUTO
        long betId = given().header("Authorization", auth).contentType("application/json")
                .body("{\"concorsoId\":" + concorsoId + ",\"label\":\"Esito\",\"market\":\"RESULT_1X2\",\"matchId\":" + matchId + "}")
        .when().post("/admin/scommesse")
        .then().statusCode(201)
                .body("resolutionMode", equalTo("AUTO"))
                .extract().jsonPath().getLong("id");

        // Inserisci punteggio 2-1 → la scommessa si risolve automaticamente a "1"
        given().header("Authorization", auth).contentType("application/json")
                .body("{\"homeScore\":2,\"awayScore\":1}")
        .when().put("/admin/matches/" + matchId + "/result")
        .then().statusCode(200).body("betsResolved", is(1));

        given().header("Authorization", auth).get("/admin/scommesse/" + betId)
                .then().statusCode(200)
                .body("status", equalTo("RESOLVED"))
                .body("officialResultRef", equalTo("1"));
    }
}
