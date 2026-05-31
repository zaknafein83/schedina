package it.schedina;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Import/export del calendario (giornate per-lega + partite) via CSV.
 * Le squadre devono già esistere: le righe con squadre non trovate vengono saltate e segnalate.
 */
@QuarkusTest
class ImportGiornateTest {

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
    void import_giornate_da_csv_salta_squadre_non_trovate() {
        String auth = "Bearer " + token();
        String lega = "ImpLega " + System.nanoTime();
        long leagueId = post(auth, "/admin/leagues", "{\"name\":\"" + lega + "\"}", 201);
        post(auth, "/admin/teams", "{\"name\":\"CasaX\",\"leagueId\":" + leagueId + "}", 201);
        post(auth, "/admin/teams", "{\"name\":\"OspiteX\",\"leagueId\":" + leagueId + "}", 201);

        // 1ª riga valida; 2ª riga con squadra inesistente → saltata.
        String csv = "leagueName,number,giornataName,homeTeamName,awayTeamName,date,overUnderLine\n"
                + lega + ",1,Turno 1,CasaX,OspiteX,2026-09-01,1.5\n"
                + lega + ",1,Turno 1,Ignota,OspiteX,,\n";

        given().header("Authorization", auth).contentType("text/plain").body(csv)
                .when().post("/admin/import/giornate")
                .then().statusCode(200)
                .body("imported", is(1))
                .body("giornateCreated", is(1))
                .body("skipped", is(1));

        // La giornata creata ha esattamente 1 partita.
        Long gId = given().header("Authorization", auth).queryParam("leagueId", leagueId)
                .get("/admin/giornate").then().statusCode(200)
                .extract().jsonPath().getLong("find { it.number == 1 }.id");

        given().header("Authorization", auth).get("/admin/giornate/" + gId)
                .then().statusCode(200).body("matchCount", is(1));

        // Re-import idempotente: nessun duplicato (upsert per giornata+casa+ospite).
        given().header("Authorization", auth).contentType("text/plain").body(csv)
                .when().post("/admin/import/giornate").then().statusCode(200).body("imported", is(1));
        given().header("Authorization", auth).get("/admin/giornate/" + gId)
                .then().statusCode(200).body("matchCount", is(1));

        // L'export contiene la partita importata con la data fornita.
        given().header("Authorization", auth).get("/admin/export/giornate")
                .then().statusCode(200)
                .body("find { it.leagueName == '" + lega + "' && it.homeTeamName == 'CasaX' }.date", equalTo("2026-09-01"))
                .body("find { it.leagueName == '" + lega + "' && it.homeTeamName == 'CasaX' }.awayTeamName", equalTo("OspiteX"));
    }
}
