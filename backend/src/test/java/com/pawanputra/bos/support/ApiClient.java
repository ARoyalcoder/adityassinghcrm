package com.pawanputra.bos.support;

import static io.restassured.RestAssured.given;

import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;

/** Small helpers for integration tests that call the API as a particular user. */
public final class ApiClient {

    private ApiClient() {
    }

    /** Signs the account in over HTTP and returns its access token. */
    public static String tokenFor(Account account) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", account.email(), "password", account.password()))
                .post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    /** A JSON request authenticated with the given access token. */
    public static RequestSpecification as(String accessToken) {
        return given().header("Authorization", "Bearer " + accessToken).contentType(ContentType.JSON);
    }
}
