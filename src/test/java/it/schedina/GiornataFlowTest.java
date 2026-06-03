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

        // Soglia [1]: con una sola partita, 1/1 esatto = vinto quel gioco (1X2 e U/O valutati a parte).
        long ruleId = post(auth, "/admin/rules", "{\"name\":\"R " + System.nanoTime() + "\",\"winningThresholds\":[1]}", 201);
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
                .body("status", equalTo("WINNING")).body("correctCount", is(2))
                .body("isWinner1x2", is(true)).body("isWinnerUo", is(true));
    }

    @Test
    void schedina_scoring_separato_1x2_e_uo() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long home = post(auth, "/admin/teams", "{\"name\":\"Casa\",\"leagueId\":" + leagueId + "}", 201);
        long away = post(auth, "/admin/teams", "{\"name\":\"Ospite\",\"leagueId\":" + leagueId + "}", 201);

        long g = post(auth, "/admin/giornate",
                "{\"leagueId\":" + leagueId + ",\"name\":\"Serie X g.1\",\"number\":1}", 201);
        long m = post(auth, "/admin/matches",
                "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"giornataId\":" + g + ",\"scheduledAt\":\"2999-01-01T20:45:00\",\"overUnderLine\":2.5}", 201);

        // Soglia [1]: in ciascun gioco basta 1 esatto per vincere quel gioco.
        long ruleId = post(auth, "/admin/rules", "{\"name\":\"R " + System.nanoTime() + "\",\"winningThresholds\":[1]}", 201);
        long c = post(auth, "/admin/concorsi",
                "{\"name\":\"Turno 1\",\"number\":1,\"ruleId\":" + ruleId + ",\"openAt\":\"2020-01-01T00:00:00\",\"closeAt\":\"2999-12-31T23:59:59\"}", 201);
        given().header("Authorization", auth).contentType("application/json").body("{\"matchId\":" + m + "}")
                .post("/admin/concorsi/" + c + "/matches").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/open").then().statusCode(200);

        // L'utente azzecca l'1X2 ("1") ma sbaglia U/O ("U" mentre il 2-1 = 3 gol = Over).
        long sched = post(auth, "/schedine",
                "{\"concorsoId\":" + c + ",\"pronostici\":[{\"matchId\":" + m + ",\"choice1x2\":\"1\",\"choiceUo\":\"U\"}]}", 201);
        given().header("Authorization", auth).contentType("application/json")
                .post("/schedine/" + sched + "/confirm").then().statusCode(200);

        given().header("Authorization", auth).contentType("application/json").body("{\"homeScore\":2,\"awayScore\":1}")
                .when().put("/admin/matches/" + m + "/result").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/close").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/process").then().statusCode(200);

        // Vince il Totocalcio (1X2) ma NON l'Under/Over; stato aggregato WINNING (vince almeno un gioco).
        given().header("Authorization", auth).get("/schedine/" + sched).then().statusCode(200)
                .body("status", equalTo("WINNING"))
                .body("correct1x2Count", is(1)).body("isWinner1x2", is(true))
                .body("correctUoCount", is(0)).body("isWinnerUo", is(false))
                .body("correctCount", is(1)).body("isWinner", is(true));
    }

    @Test
    void notifiche_per_gioco_idempotenti_e_pulite() {
        String auth = "Bearer " + token();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + System.nanoTime() + "\"}", 201);
        long home = post(auth, "/admin/teams", "{\"name\":\"Casa\",\"leagueId\":" + leagueId + "}", 201);
        long away = post(auth, "/admin/teams", "{\"name\":\"Ospite\",\"leagueId\":" + leagueId + "}", 201);
        long g = post(auth, "/admin/giornate",
                "{\"leagueId\":" + leagueId + ",\"name\":\"g\",\"number\":1}", 201);
        long m = post(auth, "/admin/matches",
                "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"giornataId\":" + g + ",\"scheduledAt\":\"2999-01-01T20:45:00\",\"overUnderLine\":2.5}", 201);

        long ruleId = post(auth, "/admin/rules", "{\"name\":\"R " + System.nanoTime() + "\",\"winningThresholds\":[1]}", 201);
        long c = post(auth, "/admin/concorsi",
                "{\"name\":\"Turno N\",\"number\":1,\"ruleId\":" + ruleId + ",\"openAt\":\"2020-01-01T00:00:00\",\"closeAt\":\"2999-12-31T23:59:59\"}", 201);
        given().header("Authorization", auth).contentType("application/json").body("{\"matchId\":" + m + "}")
                .post("/admin/concorsi/" + c + "/matches").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/open").then().statusCode(200);

        // Pronostico: 1X2 = "1", U/O = "U".
        long sched = post(auth, "/schedine",
                "{\"concorsoId\":" + c + ",\"pronostici\":[{\"matchId\":" + m + ",\"choice1x2\":\"1\",\"choiceUo\":\"U\"}]}", 201);
        given().header("Authorization", auth).contentType("application/json")
                .post("/schedine/" + sched + "/confirm").then().statusCode(200);

        // Risultato 2-1 → 1X2 "1" giusto, 3 gol = Over → U/O "U" sbagliato: vince SOLO il Totocalcio.
        given().header("Authorization", auth).contentType("application/json").body("{\"homeScore\":2,\"awayScore\":1}")
                .when().put("/admin/matches/" + m + "/result").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/close").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/process").then().statusCode(200)
                .body("notifications.sent", is(1));

        // Una sola notifica per la schedina, gioco TOTOCALCIO.
        given().header("Authorization", auth).get("/notifications").then().statusCode(200)
                .body("findAll { it.schedinaId == " + sched + " }.size()", is(1))
                .body("find { it.schedinaId == " + sched + " }.game", equalTo("TOTOCALCIO"));

        // Ri-elaborazione: idempotente, nessun duplicato (skipped, non sent).
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/process").then().statusCode(200)
                .body("notifications.sent", is(0)).body("notifications.skipped", is(1));
        given().header("Authorization", auth).get("/notifications").then().statusCode(200)
                .body("findAll { it.schedinaId == " + sched + " }.size()", is(1));

        // Cambio risultato a 0-0 → 1X2 "X" (l'1 ora sbaglia), 0 gol = Under → "U" giusto: vince SOLO l'U/O.
        given().header("Authorization", auth).contentType("application/json").body("{\"homeScore\":0,\"awayScore\":0}")
                .when().put("/admin/matches/" + m + "/result").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/process").then().statusCode(200);

        // La notifica obsoleta (TOTOCALCIO) è rimossa e ne resta una sola, gioco UNDER_OVER.
        given().header("Authorization", auth).get("/notifications").then().statusCode(200)
                .body("findAll { it.schedinaId == " + sched + " }.size()", is(1))
                .body("find { it.schedinaId == " + sched + " }.game", equalTo("UNDER_OVER"));
    }

    @Test
    void montepremi_concorso_13_su_13() {
        String auth = "Bearer " + token();
        long nano = System.nanoTime();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + nano + "\"}", 201);
        long home = post(auth, "/admin/teams", "{\"name\":\"Casa\",\"leagueId\":" + leagueId + "}", 201);
        long away = post(auth, "/admin/teams", "{\"name\":\"Ospite\",\"leagueId\":" + leagueId + "}", 201);
        long g = post(auth, "/admin/giornate", "{\"leagueId\":" + leagueId + ",\"name\":\"g\",\"number\":1}", 201);
        long ruleId = post(auth, "/admin/rules", "{\"name\":\"R " + nano + "\",\"winningThresholds\":[13]}", 201);
        long c = post(auth, "/admin/concorsi",
                "{\"name\":\"T1\",\"number\":1,\"ruleId\":" + ruleId + ",\"openAt\":\"2020-01-01T00:00:00\",\"closeAt\":\"2999-12-31T23:59:59\"}", 201);

        // 13 partite (stesse due squadre), tutte aggiunte al concorso; pronostico 1 fisso + Over.
        long[] ids = new long[13];
        StringBuilder pron = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            long m = post(auth, "/admin/matches",
                    "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"giornataId\":" + g + ",\"scheduledAt\":\"2999-01-01T20:45:00\",\"overUnderLine\":2.5}", 201);
            ids[i] = m;
            given().header("Authorization", auth).contentType("application/json").body("{\"matchId\":" + m + "}")
                    .post("/admin/concorsi/" + c + "/matches").then().statusCode(200);
            if (i > 0) pron.append(",");
            pron.append("{\"matchId\":").append(m).append(",\"choice1x2\":\"1\",\"choiceUo\":\"O\"}");
        }
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/open").then().statusCode(200);

        long sched = post(auth, "/schedine", "{\"concorsoId\":" + c + ",\"pronostici\":[" + pron + "]}", 201);
        given().header("Authorization", auth).contentType("application/json")
                .post("/schedine/" + sched + "/confirm").then().statusCode(200);

        // Tutte 1-0: 1X2 "1" giusto ×13 (count 13); 1 gol = Under → "O" sbagliato ×13 (count 0).
        for (long m : ids) {
            given().header("Authorization", auth).contentType("application/json").body("{\"homeScore\":1,\"awayScore\":0}")
                    .when().put("/admin/matches/" + m + "/result").then().statusCode(200);
        }
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/close").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + c + "/process").then().statusCode(200);

        // Il concorso è a montepremi: input M (qualunque sia nella catena) e premio 13 = 75% di M / 1 vincitore.
        int m1x2 = given().header("Authorization", auth).get("/admin/concorsi/" + c)
                .then().statusCode(200).body("montepremiManaged", is(true)).extract().path("montepremi1x2");
        given().header("Authorization", auth).get("/schedine/" + sched).then().statusCode(200)
                .body("isWinner1x2", is(true)).body("prize1x2", is(m1x2 * 75 / 100))
                .body("isWinnerUo", is(false)).body("prizeUo", is(0));
    }

    @Test
    void concorsi_chiusi_archivio() {
        String auth = "Bearer " + token();
        long nano = System.nanoTime();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + nano + "\"}", 201);
        long home = post(auth, "/admin/teams", "{\"name\":\"Casa\",\"leagueId\":" + leagueId + "}", 201);
        long away = post(auth, "/admin/teams", "{\"name\":\"Ospite\",\"leagueId\":" + leagueId + "}", 201);
        long g = post(auth, "/admin/giornate", "{\"leagueId\":" + leagueId + ",\"name\":\"g\",\"number\":1}", 201);

        String matchBody = "{\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + ",\"giornataId\":" + g
                + ",\"scheduledAt\":\"2999-01-01T20:45:00\",\"overUnderLine\":2.5}";
        String concorsoBody = "\"number\":1,\"openAt\":\"2020-01-01T00:00:00\",\"closeAt\":\"2999-12-31T23:59:59\"}";

        // Concorso CHIUSO: aperto e poi chiuso (CLOSED) → deve comparire in archivio.
        long chiuso = post(auth, "/admin/concorsi", "{\"name\":\"Archivio-chiuso-" + nano + "\"," + concorsoBody, 201);
        long mC = post(auth, "/admin/matches", matchBody, 201);
        given().header("Authorization", auth).contentType("application/json").body("{\"matchId\":" + mC + "}")
                .post("/admin/concorsi/" + chiuso + "/matches").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + chiuso + "/open").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + chiuso + "/close").then().statusCode(200).body("status", equalTo("CLOSED"));

        // Concorso APERTO: non deve comparire in archivio, ma sì tra gli aperti.
        long aperto = post(auth, "/admin/concorsi", "{\"name\":\"Archivio-aperto-" + nano + "\"," + concorsoBody, 201);
        long mA = post(auth, "/admin/matches", matchBody, 201);
        given().header("Authorization", auth).contentType("application/json").body("{\"matchId\":" + mA + "}")
                .post("/admin/concorsi/" + aperto + "/matches").then().statusCode(200);
        given().header("Authorization", auth).contentType("application/json")
                .post("/admin/concorsi/" + aperto + "/open").then().statusCode(200);

        // Concorso BOZZA (DRAFT): non deve comparire da nessuna parte lato utente.
        long bozza = post(auth, "/admin/concorsi", "{\"name\":\"Archivio-bozza-" + nano + "\"," + concorsoBody, 201);

        // /concorsi/chiusi: contiene il CHIUSO, non l'APERTO né la BOZZA.
        given().header("Authorization", auth).get("/concorsi/chiusi").then().statusCode(200)
                .body("findAll { it.id == " + chiuso + " }.size()", is(1))
                .body("find { it.id == " + chiuso + " }.status", equalTo("CLOSED"))
                .body("findAll { it.id == " + aperto + " }.size()", is(0))
                .body("findAll { it.id == " + bozza + " }.size()", is(0));

        // /concorsi (aperti): contiene l'APERTO, non il CHIUSO.
        given().header("Authorization", auth).get("/concorsi").then().statusCode(200)
                .body("findAll { it.id == " + aperto + " }.size()", is(1))
                .body("findAll { it.id == " + chiuso + " }.size()", is(0));
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
    void scommessa_fine_campionato_self_service() {
        String auth = "Bearer " + token();
        long nano = System.nanoTime();
        long seasonId = post(auth, "/admin/seasons", "{\"label\":\"S-" + nano + "\",\"isCurrent\":true}", 201);
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"Lega " + nano + "\"}", 201);
        long teamA = post(auth, "/admin/teams", "{\"name\":\"Alpha\",\"leagueId\":" + leagueId + "}", 201);
        long pA = post(auth, "/admin/players", "{\"firstName\":\"Bomber\",\"lastName\":\"A\",\"teamId\":" + teamA + "}", 201);

        // L'utente apre/gioca direttamente: Capocannoniere della lega, sceglie pA (niente catalogo admin).
        given().header("Authorization", auth).contentType("application/json")
                .body("{\"seasonId\":" + seasonId + ",\"leagueId\":" + leagueId + ",\"market\":\"TOP_SCORER\",\"prediction\":\"" + pA + "\"}")
                .when().post("/scommesse/stagione").then().statusCode(201);

        // L'admin dichiara il risultato ufficiale: vince pA → la giocata si risolve.
        given().header("Authorization", auth).contentType("application/json")
                .body("{\"seasonId\":" + seasonId + ",\"leagueId\":" + leagueId + ",\"market\":\"TOP_SCORER\",\"officialResultRef\":\"" + pA + "\"}")
                .when().post("/admin/scommesse/result").then().statusCode(200).body("status", equalTo("RESOLVED"));

        given().header("Authorization", auth).get("/scommesse/mine").then().statusCode(200)
                .body("find { it.market == 'TOP_SCORER' }.isCorrect", is(true))
                .body("find { it.market == 'TOP_SCORER' }.choiceLabel", equalTo("Bomber A"));
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
}
