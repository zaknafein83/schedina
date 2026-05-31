package it.schedina;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * E2E del redesign #3: giornata di campionato per-lega → Concorso (selezione partite) →
 * Schedina utente; scommesse di partita (auto) e di fine campionato (manuale).
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
    void concorso_schedina_vincente() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long home = post(auth, "/admin/teams", "{\"name\":\"Casa\",\"leagueId\":" + leagueId + "}", 201);
        long away = post(auth, "/admin/teams", "{\"name\":\"Ospite\",\"leagueId\":" + leagueId + "}", 201);

        long g = post(auth, "/admin/giornate",
                "{\"leagueId\":" + leagueId + ",\"name\":\"Serie X g.1\",\"number\":1}", 201);
        long m = post(auth, "/admin/matches",
                "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"giornataId\":" + g + ",\"scheduledAt\":\"2999-01-01T20:45:00\",\"overUnderLine\":2.5}", 201);

        long ruleId = post(auth, "/admin/rules", "{\"name\":\"R " + System.nanoTime() + "\",\"winningThresholds\":[2]}", 201);
        long c = post(auth, "/admin/concorsi",
                "{\"name\":\"Turno 1\",\"number\":1,\"ruleId\":" + ruleId + ",\"openAt\":\"2020-01-01T00:00:00\",\"closeAt\":\"2999-12-31T23:59:59\"}", 201);

        // seleziona la partita nel concorso
        given().header("Authorization", auth).contentType("application/json").body("{\"matchId\":" + m + "}")
                .post("/admin/concorsi/" + c + "/matches").then().statusCode(200);
        // apri
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/open").then().statusCode(200).body("status", equalTo("OPEN"));

        // l'utente compila la schedina (1 + Over, coerenti con 2-1)
        long sched = post(auth, "/schedine",
                "{\"concorsoId\":" + c + ",\"pronostici\":[{\"matchId\":" + m + ",\"choice1x2\":\"1\",\"choiceUo\":\"O\"}]}", 201);
        given().header("Authorization", auth).contentType("application/json")
                .post("/schedine/" + sched + "/confirm").then().statusCode(200).body("status", equalTo("CONFIRMED"));

        given().header("Authorization", auth).contentType("application/json").body("{\"homeScore\":2,\"awayScore\":1}")
                .when().put("/admin/matches/" + m + "/result").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/close").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/process").then().statusCode(200)
                .body("allScored", is(true)).body("winners", is(1)).body("status", equalTo("PROCESSED"));

        given().header("Authorization", auth).get("/schedine/" + sched).then().statusCode(200)
                .body("status", equalTo("WINNING")).body("correctCount", is(2));
    }

    @Test
    void scommessa_di_partita_vincitore_auto() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long home = post(auth, "/admin/teams", "{\"name\":\"Alpha\",\"leagueId\":" + leagueId + "}", 201);
        long away = post(auth, "/admin/teams", "{\"name\":\"Beta\",\"leagueId\":" + leagueId + "}", 201);
        long g = post(auth, "/admin/giornate",
                "{\"leagueId\":" + leagueId + ",\"name\":\"g\",\"number\":1}", 201);
        long m = post(auth, "/admin/matches",
                "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"giornataId\":" + g + ",\"scheduledAt\":\"2999-01-01T20:45:00\"}", 201);

        // l'utente scommette: vince la squadra di casa
        given().header("Authorization", auth).contentType("application/json")
                .body("{\"matchId\":" + m + ",\"market\":\"WINNER\",\"prediction\":\"" + home + "\"}")
                .when().post("/scommesse/partita").then().statusCode(201);

        // punteggio 3-0 → vince casa → giocata corretta (risoluzione automatica)
        given().header("Authorization", auth).contentType("application/json").body("{\"homeScore\":3,\"awayScore\":0}")
                .when().put("/admin/matches/" + m + "/result").then().statusCode(200);

        given().header("Authorization", auth).get("/scommesse/partita/mine").then().statusCode(200)
                .body("find { it.matchId == " + m + " }.isCorrect", is(true));
    }

    @Test
    void scommessa_fine_campionato_manuale() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long teamA = post(auth, "/admin/teams", "{\"name\":\"Alpha\",\"leagueId\":" + leagueId + "}", 201);
        long pA = post(auth, "/admin/players", "{\"firstName\":\"Bomber\",\"lastName\":\"A\",\"teamId\":" + teamA + "}", 201);
        long pB = post(auth, "/admin/players", "{\"firstName\":\"Bomber\",\"lastName\":\"B\",\"teamId\":" + teamA + "}", 201);

        long bet = post(auth, "/admin/scommesse",
                "{\"label\":\"Capocannoniere\",\"market\":\"TOP_SCORER\",\"options\":["
                        + "{\"ref\":\"" + pA + "\",\"label\":\"Bomber A\"},{\"ref\":\"" + pB + "\",\"label\":\"Bomber B\"}]}", 201);

        given().header("Authorization", auth).contentType("application/json")
                .body("{\"scommessaId\":" + bet + ",\"choiceRef\":\"" + pA + "\"}")
                .when().post("/scommesse").then().statusCode(201);

        given().header("Authorization", auth).contentType("application/json")
                .body("{\"officialResultRef\":\"" + pA + "\"}")
                .when().patch("/admin/scommesse/" + bet + "/resolve").then().statusCode(200).body("status", equalTo("RESOLVED"));

        given().header("Authorization", auth).get("/scommesse/mine").then().statusCode(200)
                .body("find { it.scommessaId == " + bet + " }.isCorrect", is(true));
    }

    @Test
    void scommessa_primo_marcatore_autogol() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long home = post(auth, "/admin/teams", "{\"name\":\"Alpha\",\"leagueId\":" + leagueId + "}", 201);
        long away = post(auth, "/admin/teams", "{\"name\":\"Beta\",\"leagueId\":" + leagueId + "}", 201);
        long g = post(auth, "/admin/giornate", "{\"leagueId\":" + leagueId + ",\"name\":\"g\",\"number\":1}", 201);
        long m = post(auth, "/admin/matches",
                "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"giornataId\":" + g + ",\"scheduledAt\":\"2999-01-01T20:45:00\"}", 201);

        // L'utente prevede "Autogol" come primo marcatore.
        given().header("Authorization", auth).contentType("application/json")
                .body("{\"matchId\":" + m + ",\"market\":\"FIRST_SCORER\",\"prediction\":\"OWN_GOAL\"}")
                .when().post("/scommesse/partita").then().statusCode(201);

        // Punteggio (serve perché le giocate si risolvano).
        given().header("Authorization", auth).contentType("application/json").body("{\"homeScore\":1,\"awayScore\":0}")
                .when().put("/admin/matches/" + m + "/result").then().statusCode(200);

        // L'admin segna il primo marcatore = Autogol (-1) → risolve la giocata.
        given().header("Authorization", auth).contentType("application/json").body("{\"playerId\":-1}")
                .when().put("/admin/matches/" + m + "/first-scorer").then().statusCode(200);

        given().header("Authorization", auth).get("/scommesse/partita/mine").then().statusCode(200)
                .body("find { it.matchId == " + m + " }.isCorrect", is(true))
                .body("find { it.matchId == " + m + " }.predictionLabel", equalTo("Autogol"));
    }

    @Test
    void scommessa_fine_campionato_una_sola_opzione() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long teamA = post(auth, "/admin/teams", "{\"name\":\"Alpha\",\"leagueId\":" + leagueId + "}", 201);
        long pA = post(auth, "/admin/players", "{\"firstName\":\"Solo\",\"lastName\":\"Uno\",\"teamId\":" + teamA + "}", 201);

        // Una sola opzione è ammessa (prima ne servivano almeno 2).
        post(auth, "/admin/scommesse",
                "{\"label\":\"Capocannoniere\",\"market\":\"TOP_SCORER\",\"options\":["
                        + "{\"ref\":\"" + pA + "\",\"label\":\"Solo Uno\"}]}", 201);
    }
}
