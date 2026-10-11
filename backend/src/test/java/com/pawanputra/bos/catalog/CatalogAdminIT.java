package com.pawanputra.bos.catalog;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.TestAccounts;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Catalog administration over real HTTP and PostgreSQL. The catalog is shared by every test in the
 * run, so each test works with codes and names containing its own random tag.
 */
class CatalogAdminIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/catalog";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;

    String admin;
    String tag;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        admin = tokenFor(accounts.activeUser(RoleCodes.ADMIN));
        tag = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    // ------------------------------------------------------------------ access control

    @Test
    void catalogAdministrationRequiresAuthentication() {
        given().get(BASE + "/services").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
        given().get(BASE + "/categories").then().statusCode(401);
        given().get(BASE + "/verticals").then().statusCode(401);
    }

    @Test
    void viewPermissionAllowsReadingButNotChanging() {
        String salesExecutive = tokenFor(accounts.activeUser(RoleCodes.SALES_EXECUTIVE)); // CATALOG_VIEW only
        String categoryId = createCategory("SOLAR", "CAT_" + tag, "Category " + tag).path("id");
        String serviceId = createService(categoryId, "SVC_" + tag, "Service " + tag, Map.of()).path("id");

        as(salesExecutive).get(BASE + "/services").then().statusCode(200);
        as(salesExecutive).get(BASE + "/verticals").then().statusCode(200);

        as(salesExecutive).body(serviceBody(categoryId, "OTHER_" + tag, "Other " + tag, Map.of()))
                .post(BASE + "/services").then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        as(salesExecutive).post(BASE + "/services/" + serviceId + "/deactivate").then().statusCode(403);
        as(salesExecutive).delete(BASE + "/services/" + serviceId).then().statusCode(403);
        as(salesExecutive).post(BASE + "/verticals/SOLAR/deactivate").then().statusCode(403);
    }

    @Test
    void roleWithoutCatalogPermissionsCannotEvenList() {
        String technician = tokenFor(accounts.activeUser(RoleCodes.TECHNICIAN));

        as(technician).get(BASE + "/services").then().statusCode(403);
    }

    // ------------------------------------------------------------------ verticals

    @Test
    void thereAreExactlySixVerticalsAndNoWayToAddOrRemoveOne() {
        as(admin).get(BASE + "/verticals").then().statusCode(200)
                .body("code", contains(
                        "CCTV_SECURITY", "DIGITAL_MARKETING", "INTERIOR_DESIGN",
                        "ARCHITECTURE_TECH", "SOLAR", "IT_SUPPORT"));

        as(admin).body(Map.of("code", "REAL_ESTATE", "name", "Real Estate", "displayOrder", 7))
                .post(BASE + "/verticals").then().statusCode(405);
        as(admin).delete(BASE + "/verticals/SOLAR").then().statusCode(405);
        as(admin).get(BASE + "/verticals/REAL_ESTATE").then().statusCode(404);
        as(admin).body(Map.of("name", "Real Estate", "displayOrder", 7, "version", 0))
                .put(BASE + "/verticals/REAL_ESTATE").then().statusCode(404);
    }

    @Test
    void verticalCanBeRenamedAndStaleEditsAreRejected() {
        Response before = as(admin).get(BASE + "/verticals/IT_SUPPORT");
        int version = before.path("version");
        String originalName = before.path("name");
        int order = before.path("displayOrder");
        try {
            as(admin).body(Map.of("name", "IT Support " + tag, "description", "Managed IT", "displayOrder", order,
                            "version", version))
                    .put(BASE + "/verticals/IT_SUPPORT")
                    .then().statusCode(200)
                    .body("name", equalTo("IT Support " + tag))
                    .body("code", equalTo("IT_SUPPORT"))
                    .body("version", equalTo(version + 1));

            // Someone still holding the old version cannot overwrite the change.
            as(admin).body(Map.of("name", "Overwritten", "displayOrder", order, "version", version))
                    .put(BASE + "/verticals/IT_SUPPORT")
                    .then().statusCode(409).body("code", equalTo("CONFLICT"));
        } finally {
            Map<String, Object> restore = new HashMap<>();
            restore.put("name", originalName);
            restore.put("description", null);
            restore.put("displayOrder", order);
            restore.put("version", as(admin).get(BASE + "/verticals/IT_SUPPORT").path("version"));
            as(admin).body(restore).put(BASE + "/verticals/IT_SUPPORT").then().statusCode(200);
        }
    }

    @Test
    void verticalNameAndOrderMustStayUniqueAndValid() {
        Response solar = as(admin).get(BASE + "/verticals/SOLAR");
        int version = solar.path("version");

        as(admin).body(Map.of("name", "IT Support", "displayOrder", 5, "version", version))
                .put(BASE + "/verticals/SOLAR").then().statusCode(409);
        as(admin).body(Map.of("name", "Solar", "displayOrder", 1, "version", version))
                .put(BASE + "/verticals/SOLAR").then().statusCode(409).body("detail", containsString("display order"));
        as(admin).body(Map.of("name", " ", "displayOrder", 0, "version", version))
                .put(BASE + "/verticals/SOLAR")
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", containsInAnyOrder("name", "displayOrder"));
    }

    @Test
    void deactivatedVerticalDisappearsFromThePublicListAndComesBack() {
        try {
            as(admin).post(BASE + "/verticals/DIGITAL_MARKETING/deactivate")
                    .then().statusCode(200).body("active", equalTo(false));

            given().get("/api/v1/service-verticals").then().body("code", hasSize(5));
            as(admin).get(BASE + "/verticals").then().body("code", hasSize(6));
        } finally {
            as(admin).post(BASE + "/verticals/DIGITAL_MARKETING/activate")
                    .then().statusCode(200).body("active", equalTo(true));
        }
        given().get("/api/v1/service-verticals").then().body("code", hasSize(6));
    }

    // ------------------------------------------------------------------ categories

    @Test
    void categoryLifecycle() {
        Response created = createCategory("CCTV_SECURITY", "INSTALL_" + tag, "Installation " + tag);
        created.then().statusCode(201)
                .header("Location", endsWith("/catalog/categories/" + created.path("id")))
                .body("vertical.code", equalTo("CCTV_SECURITY"))
                .body("vertical.name", notNullValue())
                .body("active", equalTo(true))
                .body("version", equalTo(0));
        String id = created.path("id");

        as(admin).body(Map.of("name", "Installations " + tag, "description", "On-site fitting",
                        "displayOrder", 3, "version", 0))
                .put(BASE + "/categories/" + id)
                .then().statusCode(200)
                .body("name", equalTo("Installations " + tag))
                .body("description", equalTo("On-site fitting"))
                .body("code", equalTo("INSTALL_" + tag))
                .body("version", equalTo(1));

        as(admin).body(Map.of("name", "Stale edit", "version", 0)).put(BASE + "/categories/" + id)
                .then().statusCode(409);

        as(admin).post(BASE + "/categories/" + id + "/deactivate").then().statusCode(200).body("active", equalTo(false));
        as(admin).post(BASE + "/categories/" + id + "/activate").then().statusCode(200).body("active", equalTo(true));

        as(admin).delete(BASE + "/categories/" + id).then().statusCode(204);
        as(admin).get(BASE + "/categories/" + id).then().statusCode(404);
    }

    @Test
    void categoryMustBelongToOneOfTheSixVerticals() {
        createCategory("REAL_ESTATE", "PLOTS_" + tag, "Plots " + tag)
                .then().statusCode(404).body("detail", containsString("REAL_ESTATE"));
    }

    @Test
    void categoryCodeAndNameAreUniqueWithinAVertical() {
        createCategory("SOLAR", "AMC_" + tag, "Maintenance " + tag).then().statusCode(201);

        createCategory("SOLAR", "AMC_" + tag, "Something else " + tag).then().statusCode(409);
        createCategory("SOLAR", "OTHER_" + tag, "MAINTENANCE " + tag).then().statusCode(409);
        // The same code and name are fine in a different vertical.
        createCategory("IT_SUPPORT", "AMC_" + tag, "Maintenance " + tag).then().statusCode(201);
    }

    @Test
    void categoryRequestIsValidated() {
        as(admin).body(Map.of("vertical", "SOLAR", "code", "lower case", "name", ""))
                .post(BASE + "/categories")
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", hasItem("code"))
                .body("errors.field", hasItem("name"));
    }

    @Test
    void categoryWithServicesCannotBeDeleted() {
        String categoryId = createCategory("SOLAR", "CAT_" + tag, "Category " + tag).path("id");
        createService(categoryId, "SVC_" + tag, "Service " + tag, Map.of()).then().statusCode(201);

        as(admin).delete(BASE + "/categories/" + categoryId)
                .then().statusCode(422).body("code", equalTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void categoriesCanBeSearchedAndFilteredByVerticalAndStatus() {
        createCategory("SOLAR", "A_" + tag, "Alpha " + tag);
        String betaId = createCategory("SOLAR", "B_" + tag, "Beta " + tag).path("id");
        createCategory("INTERIOR_DESIGN", "C_" + tag, "Gamma " + tag);
        as(admin).post(BASE + "/categories/" + betaId + "/deactivate");

        as(admin).queryParam("search", tag.toLowerCase()).get(BASE + "/categories")
                .then().statusCode(200).body("totalItems", equalTo(3));
        as(admin).queryParam("search", tag).queryParam("vertical", "SOLAR").get(BASE + "/categories")
                .then().body("items.name", containsInAnyOrder("Alpha " + tag, "Beta " + tag));
        as(admin).queryParam("search", tag).queryParam("active", false).get(BASE + "/categories")
                .then().body("items.name", contains("Beta " + tag));
        as(admin).queryParam("vertical", "REAL_ESTATE").get(BASE + "/categories")
                .then().statusCode(400).body("code", equalTo("MALFORMED_REQUEST"));
    }

    // ------------------------------------------------------------------ services

    @Test
    void serviceLifecycleIncludingMoveBetweenCategories() {
        String solarCategory = createCategory("SOLAR", "ROOF_" + tag, "Rooftop " + tag).path("id");
        String cctvCategory = createCategory("CCTV_SECURITY", "CAM_" + tag, "Cameras " + tag).path("id");

        Response created = createService(solarCategory, "ROOFTOP_3KW_" + tag, "3 kW rooftop " + tag, Map.of(
                "billingType", "ONE_TIME", "unitLabel", "per kW", "basePrice", 55000.50,
                "requiresSiteVisit", true, "estimatedDurationDays", 7, "description", "Supply and install"));
        created.then().statusCode(201)
                .header("Location", endsWith("/catalog/services/" + created.path("id")))
                .body("code", equalTo("ROOFTOP_3KW_" + tag))
                .body("vertical.code", equalTo("SOLAR"))
                .body("category.id", equalTo(solarCategory))
                .body("billingType", equalTo("ONE_TIME"))
                .body("unitLabel", equalTo("per kW"))
                .body("basePrice", equalTo(55000.50f))
                .body("requiresSiteVisit", equalTo(true))
                .body("estimatedDurationDays", equalTo(7))
                .body("active", equalTo(true));
        String id = created.path("id");

        Map<String, Object> update = serviceBody(cctvCategory, null, "Camera install " + tag, Map.of(
                "billingType", "QUOTE_BASED"));
        update.put("version", 0);
        as(admin).body(update).put(BASE + "/services/" + id)
                .then().statusCode(200)
                .body("name", equalTo("Camera install " + tag))
                .body("code", equalTo("ROOFTOP_3KW_" + tag)) // the code never changes
                .body("vertical.code", equalTo("CCTV_SECURITY"))
                .body("category.id", equalTo(cctvCategory))
                .body("billingType", equalTo("QUOTE_BASED"))
                .body("basePrice", equalTo(null))
                .body("version", equalTo(1));

        as(admin).body(update).put(BASE + "/services/" + id).then().statusCode(409); // version 0 is stale now

        as(admin).post(BASE + "/services/" + id + "/deactivate").then().statusCode(200).body("active", equalTo(false));
        as(admin).get(BASE + "/services/" + id).then().statusCode(200).body("active", equalTo(false));
        as(admin).post(BASE + "/services/" + id + "/activate").then().statusCode(200).body("active", equalTo(true));

        as(admin).delete(BASE + "/services/" + id).then().statusCode(204);
        as(admin).get(BASE + "/services/" + id).then().statusCode(404);
        // The code of a deleted service can be used again.
        createService(solarCategory, "ROOFTOP_3KW_" + tag, "3 kW rooftop again " + tag, Map.of())
                .then().statusCode(201);
    }

    @Test
    void serviceRequestIsValidated() {
        String categoryId = createCategory("SOLAR", "CAT_" + tag, "Category " + tag).path("id");

        as(admin).body(Map.of("code", "bad code", "name", "", "billingType", "ONE_TIME", "basePrice", -1,
                        "estimatedDurationDays", 0))
                .post(BASE + "/services")
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", containsInAnyOrder(
                        "code", "name", "categoryId", "basePrice", "estimatedDurationDays"));

        // An unknown billing type is not a value the API understands at all.
        as(admin).body(serviceBody(categoryId, "X_" + tag, "X " + tag, Map.of("billingType", "BARTER")))
                .post(BASE + "/services").then().statusCode(400).body("code", equalTo("MALFORMED_REQUEST"));
    }

    @Test
    void serviceBusinessRulesAreEnforced() {
        String categoryId = createCategory("SOLAR", "CAT_" + tag, "Category " + tag).path("id");
        createService(categoryId, "SVC_" + tag, "Service " + tag, Map.of()).then().statusCode(201);

        // Fixed-price and recurring services need a price; quote-based ones do not.
        createService(categoryId, "NOPRICE_" + tag, "No price " + tag, Map.of("billingType", "RECURRING"))
                .then().statusCode(422).body("detail", containsString("base price"));
        // Codes are unique across the whole catalog, names within a category.
        createService(categoryId, "SVC_" + tag, "Different name " + tag, Map.of()).then().statusCode(409);
        createService(categoryId, "OTHER_" + tag, "SERVICE " + tag, Map.of()).then().statusCode(409);
        // The category must exist.
        createService(UUID.randomUUID().toString(), "ORPHAN_" + tag, "Orphan " + tag, Map.of())
                .then().statusCode(422);
    }

    @Test
    void servicesCanBeSearchedFilteredSortedAndPaged() {
        String solar = createCategory("SOLAR", "SOL_" + tag, "Solar cat " + tag).path("id");
        String it = createCategory("IT_SUPPORT", "ITS_" + tag, "IT cat " + tag).path("id");
        createService(solar, "S1_" + tag, "Alpha " + tag, Map.of("billingType", "ONE_TIME", "basePrice", 100));
        createService(solar, "S2_" + tag, "Bravo " + tag, Map.of("billingType", "ONE_TIME", "basePrice", 300));
        createService(solar, "S3_" + tag, "Charlie " + tag, Map.of("billingType", "RECURRING", "basePrice", 200));
        createService(it, "S4_" + tag, "Delta " + tag, Map.of());
        String echoId = createService(it, "S5_" + tag, "Echo " + tag, Map.of()).path("id");
        as(admin).post(BASE + "/services/" + echoId + "/deactivate");

        // search matches name or code, case-insensitively
        as(admin).queryParam("search", tag.toLowerCase()).get(BASE + "/services")
                .then().statusCode(200).body("totalItems", equalTo(5));
        as(admin).queryParam("search", "s3_" + tag.toLowerCase()).get(BASE + "/services")
                .then().body("items.name", contains("Charlie " + tag));
        // a literal % or _ typed by the user is not a wildcard
        as(admin).queryParam("search", "%").get(BASE + "/services").then().body("totalItems", equalTo(0));

        // filters
        as(admin).queryParam("search", tag).queryParam("vertical", "SOLAR").get(BASE + "/services")
                .then().body("totalItems", equalTo(3)).body("items.vertical.code", everyItem(equalTo("SOLAR")));
        as(admin).queryParam("search", tag).queryParam("categoryId", it).get(BASE + "/services")
                .then().body("items.name", containsInAnyOrder("Delta " + tag, "Echo " + tag));
        as(admin).queryParam("search", tag).queryParam("active", false).get(BASE + "/services")
                .then().body("items.name", contains("Echo " + tag));
        as(admin).queryParam("search", tag).queryParam("billingType", "RECURRING").get(BASE + "/services")
                .then().body("items.name", contains("Charlie " + tag));
        as(admin).queryParam("search", tag).queryParam("vertical", "SOLAR").queryParam("active", true)
                .queryParam("billingType", "ONE_TIME").get(BASE + "/services")
                .then().body("items.name", containsInAnyOrder("Alpha " + tag, "Bravo " + tag));

        // sorting
        as(admin).queryParam("search", tag).queryParam("sort", "name,desc").get(BASE + "/services")
                .then().body("items.name", contains(
                        "Echo " + tag, "Delta " + tag, "Charlie " + tag, "Bravo " + tag, "Alpha " + tag));
        as(admin).queryParam("search", tag).queryParam("vertical", "SOLAR").queryParam("sort", "basePrice,desc")
                .get(BASE + "/services")
                .then().body("items.name", contains("Bravo " + tag, "Charlie " + tag, "Alpha " + tag));

        // pagination
        Response firstPage = as(admin).queryParam("search", tag).queryParam("sort", "name,asc")
                .queryParam("size", 2).get(BASE + "/services");
        firstPage.then().body("page", equalTo(0)).body("size", equalTo(2))
                .body("totalItems", equalTo(5)).body("totalPages", equalTo(3))
                .body("items.name", contains("Alpha " + tag, "Bravo " + tag));
        as(admin).queryParam("search", tag).queryParam("sort", "name,asc").queryParam("size", 2)
                .queryParam("page", 2).get(BASE + "/services")
                .then().body("items.name", contains("Echo " + tag));
        List<String> beyond = as(admin).queryParam("search", tag).queryParam("size", 2).queryParam("page", 9)
                .get(BASE + "/services").path("items");
        assertThat(beyond).isEmpty();
    }

    @Test
    void invalidListParametersAreRejected() {
        as(admin).queryParam("sort", "passwordHash").get(BASE + "/services")
                .then().statusCode(400).body("code", equalTo("MALFORMED_REQUEST"));
        as(admin).queryParam("sort", "name,sideways").get(BASE + "/services").then().statusCode(400);
        as(admin).queryParam("size", 500).get(BASE + "/services").then().statusCode(400);
        as(admin).queryParam("size", 0).get(BASE + "/services").then().statusCode(400);
        as(admin).queryParam("page", -1).get(BASE + "/services").then().statusCode(400);
        as(admin).queryParam("categoryId", "not-a-uuid").get(BASE + "/services").then().statusCode(400);
        as(admin).queryParam("billingType", "BARTER").get(BASE + "/services").then().statusCode(400);
    }

    // ------------------------------------------------------------------ helpers

    private Response createCategory(String vertical, String code, String name) {
        return as(admin).body(Map.of("vertical", vertical, "code", code, "name", name)).post(BASE + "/categories");
    }

    private Response createService(String categoryId, String code, String name, Map<String, Object> extra) {
        return as(admin).body(serviceBody(categoryId, code, name, extra)).post(BASE + "/services");
    }

    private static Map<String, Object> serviceBody(
            String categoryId, String code, String name, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        if (code != null) {
            body.put("code", code);
        }
        body.put("name", name);
        body.put("billingType", "QUOTE_BASED");
        body.putAll(extra);
        return body;
    }
}
