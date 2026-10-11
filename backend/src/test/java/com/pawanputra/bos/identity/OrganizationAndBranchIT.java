package com.pawanputra.bos.identity;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** Organization and branch administration. Every test gets its own organization, so lists start empty. */
class OrganizationAndBranchIT extends AbstractIntegrationTest {

    private static final String BRANCHES = "/api/v1/branches";
    private static final String ORGANIZATION = "/api/v1/organization";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;

    Account adminAccount;
    String admin;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        adminAccount = accounts.activeUser(RoleCodes.ADMIN);
        admin = tokenFor(adminAccount);
    }

    // ------------------------------------------------------------------ access control

    @Test
    void endpointsRequireAuthentication() {
        given().get(BRANCHES).then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
        given().get(ORGANIZATION).then().statusCode(401);
    }

    @Test
    void viewPermissionAllowsReadingButNotChanging() {
        String hr = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.HR)); // BRANCH_VIEW, ORGANIZATION_VIEW
        String branchId = createBranch("LKO", "Lucknow Office", "Lucknow", "Uttar Pradesh").path("id");

        as(hr).get(BRANCHES).then().statusCode(200).body("totalItems", equalTo(1));
        as(hr).get(ORGANIZATION).then().statusCode(200);

        as(hr).body(branchBody("NEW", "New", "Kanpur", "Uttar Pradesh")).post(BRANCHES)
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        as(hr).post(BRANCHES + "/" + branchId + "/deactivate").then().statusCode(403);
        as(hr).delete(BRANCHES + "/" + branchId).then().statusCode(403);
        as(hr).body(Map.of("name", "Renamed", "version", 0)).put(ORGANIZATION).then().statusCode(403);
    }

    @Test
    void roleWithoutBranchPermissionsCannotList() {
        String salesExecutive = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.SALES_EXECUTIVE));

        as(salesExecutive).get(BRANCHES).then().statusCode(403);
        as(salesExecutive).get(ORGANIZATION).then().statusCode(403);
    }

    @Test
    void branchesOfAnotherOrganizationAreInvisible() {
        String branchId = createBranch("LKO", "Lucknow Office", "Lucknow", "Uttar Pradesh").path("id");
        String outsider = tokenFor(accounts.activeUser(RoleCodes.ADMIN)); // a different organization

        as(outsider).get(BRANCHES).then().statusCode(200).body("totalItems", equalTo(0));
        as(outsider).get(BRANCHES + "/" + branchId).then().statusCode(404);
        as(outsider).post(BRANCHES + "/" + branchId + "/deactivate").then().statusCode(404);
        as(outsider).delete(BRANCHES + "/" + branchId).then().statusCode(404);
        // The same branch code is free in the other organization.
        as(outsider).body(branchBody("LKO", "Their Lucknow", "Lucknow", "Uttar Pradesh")).post(BRANCHES)
                .then().statusCode(201);
    }

    // ------------------------------------------------------------------ organization

    @Test
    void organizationCanBeViewedAndEdited() {
        Response current = as(admin).get(ORGANIZATION);
        current.then().statusCode(200).body("id", equalTo(adminAccount.organizationId().toString()))
                .body("status", equalTo("ACTIVE"));
        String code = current.path("code");

        as(admin).body(Map.of("name", "Pawan Putra Group", "legalName", "Pawan Putra Pvt Ltd",
                        "taxId", "09ABCDE1234F1Z5", "email", "info@example.com", "phone", "+91 522 400 0000",
                        "version", 0))
                .put(ORGANIZATION)
                .then().statusCode(200)
                .body("name", equalTo("Pawan Putra Group"))
                .body("legalName", equalTo("Pawan Putra Pvt Ltd"))
                .body("code", equalTo(code))
                .body("version", equalTo(1));

        as(admin).body(Map.of("name", "Stale", "version", 0)).put(ORGANIZATION).then().statusCode(409);
        as(admin).body(Map.of("name", "", "email", "not-an-email", "phone", "abc", "version", 1)).put(ORGANIZATION)
                .then().statusCode(400).body("errors.field", containsInAnyOrder("name", "email", "phone"));
    }

    // ------------------------------------------------------------------ branches

    @Test
    void branchLifecycle() {
        Map<String, Object> body = branchBody("LKO-HZG", "Hazratganj Office", "Lucknow", "Uttar Pradesh");
        body.put("addressLine1", "12 MG Road");
        body.put("postalCode", "226001");
        body.put("phone", "+91 98765 43210");
        body.put("email", "lucknow@example.com");
        Response created = as(admin).body(body).post(BRANCHES);
        created.then().statusCode(201)
                .header("Location", endsWith("/branches/" + created.path("id")))
                .body("code", equalTo("LKO-HZG"))
                .body("city", equalTo("Lucknow"))
                .body("state", equalTo("Uttar Pradesh"))
                .body("countryCode", equalTo("IN"))
                .body("active", equalTo(true))
                .body("headOffice", equalTo(false));
        String id = created.path("id");

        Map<String, Object> update = branchBody(null, "Hazratganj Branch", "Lucknow", "Uttar Pradesh");
        update.put("version", 0);
        as(admin).body(update).put(BRANCHES + "/" + id)
                .then().statusCode(200)
                .body("name", equalTo("Hazratganj Branch"))
                .body("code", equalTo("LKO-HZG"))
                .body("addressLine1", equalTo(null))
                .body("version", equalTo(1));
        as(admin).body(update).put(BRANCHES + "/" + id).then().statusCode(409);

        as(admin).post(BRANCHES + "/" + id + "/deactivate").then().statusCode(200).body("active", equalTo(false));
        as(admin).post(BRANCHES + "/" + id + "/activate").then().statusCode(200).body("active", equalTo(true));

        as(admin).delete(BRANCHES + "/" + id).then().statusCode(204);
        as(admin).get(BRANCHES + "/" + id).then().statusCode(404);
        // The code of a deleted branch can be used again.
        createBranch("LKO-HZG", "Hazratganj again", "Lucknow", "Uttar Pradesh").then().statusCode(201);
    }

    @Test
    void branchRequestIsValidated() {
        as(admin).body(Map.of("code", "lower", "name", "", "phone", "call me", "email", "nope", "countryCode", "India"))
                .post(BRANCHES)
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", containsInAnyOrder(
                        "code", "name", "city", "state", "phone", "email", "countryCode"));
    }

    @Test
    void branchCodeIsUniqueWithinTheOrganization() {
        createBranch("LKO", "Lucknow Office", "Lucknow", "Uttar Pradesh").then().statusCode(201);

        createBranch("LKO", "Another Lucknow Office", "Lucknow", "Uttar Pradesh")
                .then().statusCode(409).body("code", equalTo("CONFLICT"));
    }

    @Test
    void headOfficeTitleMovesAndTheHeadOfficeIsProtected() {
        Map<String, Object> first = branchBody("HO", "Head Office", "Lucknow", "Uttar Pradesh");
        first.put("headOffice", true);
        String firstId = as(admin).body(first).post(BRANCHES).then().statusCode(201)
                .body("headOffice", equalTo(true)).extract().path("id");
        String secondId = createBranch("DEL", "Delhi Office", "New Delhi", "Delhi").path("id");

        as(admin).post(BRANCHES + "/" + firstId + "/deactivate")
                .then().statusCode(422).body("detail", containsString("head office"));
        as(admin).delete(BRANCHES + "/" + firstId).then().statusCode(422);

        // Making Delhi the head office takes the title away from Lucknow.
        Map<String, Object> promote = branchBody(null, "Delhi Office", "New Delhi", "Delhi");
        promote.put("headOffice", true);
        promote.put("version", 0);
        as(admin).body(promote).put(BRANCHES + "/" + secondId).then().statusCode(200).body("headOffice", equalTo(true));

        as(admin).get(BRANCHES + "/" + firstId).then().body("headOffice", equalTo(false));
        as(admin).get(BRANCHES).then().body("items.findAll { it.headOffice }.code", contains("DEL"));
        as(admin).post(BRANCHES + "/" + firstId + "/deactivate").then().statusCode(200);
    }

    @Test
    void branchWithPeopleAssignedCannotBeDeleted() {
        String branchId = createBranch("LKO", "Lucknow Office", "Lucknow", "Uttar Pradesh").path("id");
        jdbc.update("UPDATE users SET branch_id = ? WHERE id = ?", UUID.fromString(branchId), adminAccount.id());

        as(admin).delete(BRANCHES + "/" + branchId)
                .then().statusCode(422).body("code", equalTo("BUSINESS_RULE_VIOLATION"));
        as(admin).post(BRANCHES + "/" + branchId + "/deactivate").then().statusCode(200);
    }

    @Test
    void branchesCanBeSearchedFilteredByLocationSortedAndPaged() {
        createBranch("LKO-1", "Hazratganj", "Lucknow", "Uttar Pradesh");
        createBranch("LKO-2", "Gomti Nagar", "Lucknow", "Uttar Pradesh");
        createBranch("KNP-1", "Civil Lines", "Kanpur", "Uttar Pradesh");
        createBranch("DEL-1", "Connaught Place", "New Delhi", "Delhi");
        String puneId = createBranch("PNQ-1", "Baner", "Pune", "Maharashtra").path("id");
        as(admin).post(BRANCHES + "/" + puneId + "/deactivate");

        // default order: state, then city, then name
        as(admin).get(BRANCHES).then().statusCode(200).body("totalItems", equalTo(5))
                .body("items.code", contains("DEL-1", "PNQ-1", "KNP-1", "LKO-2", "LKO-1"));

        // search matches name, code or city
        as(admin).queryParam("search", "gomti").get(BRANCHES).then().body("items.code", contains("LKO-2"));
        as(admin).queryParam("search", "knp").get(BRANCHES).then().body("items.code", contains("KNP-1"));
        as(admin).queryParam("search", "lucknow").get(BRANCHES).then().body("items", hasSize(2));

        // location filters ignore case
        as(admin).queryParam("state", "uttar pradesh").get(BRANCHES).then().body("totalItems", equalTo(3));
        as(admin).queryParam("state", "Uttar Pradesh").queryParam("city", "Kanpur").get(BRANCHES)
                .then().body("items.code", contains("KNP-1"));
        as(admin).queryParam("active", false).get(BRANCHES).then().body("items.code", contains("PNQ-1"));
        as(admin).queryParam("state", "Goa").get(BRANCHES).then().body("totalItems", equalTo(0));

        // sorting and pagination
        as(admin).queryParam("sort", "name,desc").queryParam("size", 2).get(BRANCHES)
                .then().body("items.name", contains("Hazratganj", "Gomti Nagar"))
                .body("totalPages", equalTo(3));
        as(admin).queryParam("sort", "name,desc").queryParam("size", 2).queryParam("page", 2).get(BRANCHES)
                .then().body("items.name", contains("Baner"));
        as(admin).queryParam("sort", "organization").get(BRANCHES).then().statusCode(400);
    }

    @Test
    void locationsListEveryStateAndCityTheOrganizationIsIn() {
        createBranch("LKO-1", "Hazratganj", "Lucknow", "Uttar Pradesh");
        createBranch("LKO-2", "Gomti Nagar", "Lucknow", "Uttar Pradesh");
        createBranch("KNP-1", "Civil Lines", "Kanpur", "Uttar Pradesh");
        createBranch("DEL-1", "Connaught Place", "New Delhi", "Delhi");

        as(admin).get(BRANCHES + "/locations").then().statusCode(200)
                .body("state", contains("Delhi", "Uttar Pradesh"))
                .body("find { it.state == 'Uttar Pradesh' }.cities", contains("Kanpur", "Lucknow"))
                .body("find { it.state == 'Delhi' }.cities", hasItem("New Delhi"));
    }

    // ------------------------------------------------------------------ helpers

    private Response createBranch(String code, String name, String city, String state) {
        return as(admin).body(branchBody(code, name, city, state)).post(BRANCHES);
    }

    private static Map<String, Object> branchBody(String code, String name, String city, String state) {
        Map<String, Object> body = new HashMap<>();
        if (code != null) {
            body.put("code", code);
        }
        body.put("name", name);
        body.put("city", city);
        body.put("state", state);
        return body;
    }
}
