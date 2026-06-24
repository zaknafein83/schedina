package it.schedina;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * L'email è gestita in modo case-insensitive: la registrazione la normalizza
 * (trim + minuscolo) e login/forgot-password la cercano normalizzata. Così un
 * utente registrato con maiuscole può comunque accedere e resettare la password.
 */
@QuarkusTest
class AuthNormalizationTest {

    private String register(String email, String username, int expected) {
        return given().contentType("application/json")
                .body("{\"email\":\"" + email + "\",\"password\":\"password1\","
                        + "\"firstName\":\"Mario\",\"lastName\":\"Rossi\",\"username\":\"" + username + "\"}")
                .when().post("/auth/register")
                .then().statusCode(expected).extract().asString();
    }

    @Test
    void login_e_forgot_sono_case_insensitive() {
        long n = System.nanoTime();
        String mixed = "MixUser" + n + "@Example.COM";
        String lower = mixed.toLowerCase();
        String upper = mixed.toUpperCase();
        register(mixed, "mix" + n, 201);

        // login con casing diverso da quello di registrazione → entrambi 200
        for (String email : new String[]{lower, upper}) {
            given().contentType("application/json")
                    .body("{\"email\":\"" + email + "\",\"password\":\"password1\"}")
                    .when().post("/auth/login")
                    .then().statusCode(200).body("accessToken", notNullValue());
        }

        // forgot-password con email in maiuscolo trova comunque l'utente → genera token
        given().contentType("application/json")
                .body("{\"email\":\"" + upper + "\"}")
                .when().post("/auth/forgot-password")
                .then().statusCode(200).body("resetToken", notNullValue());
    }

    @Test
    void email_duplicata_con_casing_diverso_e_rifiutata() {
        long n = System.nanoTime();
        register("Foo" + n + "@X.com", "foo" + n, 201);
        given().contentType("application/json")
                .body("{\"email\":\"foo" + n + "@x.com\",\"password\":\"password1\","
                        + "\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"foo2" + n + "\"}")
                .when().post("/auth/register")
                .then().statusCode(400).body("error", equalTo("Email già registrata"));
    }

    @Test
    void username_duplicato_e_rifiutato_con_400() {
        long n = System.nanoTime();
        register("a" + n + "@x.com", "dup" + n, 201);
        given().contentType("application/json")
                .body("{\"email\":\"b" + n + "@x.com\",\"password\":\"password1\","
                        + "\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"dup" + n + "\"}")
                .when().post("/auth/register")
                .then().statusCode(400).body("error", equalTo("Username già in uso"));
    }
}
