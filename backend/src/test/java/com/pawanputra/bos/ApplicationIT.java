package com.pawanputra.bos;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import com.pawanputra.bos.support.AbstractIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class ApplicationIT extends AbstractIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void healthEndpointIsUp() {
        given().when().get("/actuator/health")
                .then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void apiReturnsExactlyTheSixSeededVerticalsInDisplayOrder() {
        given().when().get("/api/v1/service-verticals")
                .then().statusCode(200)
                .body("$", hasSize(6))
                .body("id", everyItem(matchesPattern("[0-9a-f-]{36}")))
                .body("code", contains(
                        "CCTV_SECURITY", "DIGITAL_MARKETING", "INTERIOR_DESIGN",
                        "ARCHITECTURE_TECH", "SOLAR", "IT_SUPPORT"))
                .body("name", contains(
                        "CCTV & Security", "Digital Marketing", "Interior Design",
                        "Architecture & Tech", "Solar", "IT Support"));
    }

    @Test
    void unknownVerticalReturnsStandardErrorBody() {
        given().when().get("/api/v1/service-verticals/REAL_ESTATE")
                .then().statusCode(404)
                .contentType("application/problem+json")
                .body("code", equalTo("RESOURCE_NOT_FOUND"))
                .body("requestId", notNullValue());
    }

    @Test
    void endpointsOutsideTheAllowListRequireAuthentication() {
        given().when().get("/api/v1/anything-else")
                .then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
    }
}
