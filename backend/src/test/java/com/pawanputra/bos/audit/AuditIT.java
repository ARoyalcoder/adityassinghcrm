package com.pawanputra.bos.audit;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.pawanputra.bos.audit.api.AuditAction;
import com.pawanputra.bos.audit.api.AuditEvent;
import com.pawanputra.bos.audit.api.AuditRecorder;
import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.AuditedOperationProbe;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The audit trail end to end. Nothing in the catalog, branch or user code mentions auditing: these
 * tests call the ordinary APIs and check that the audit log filled itself in.
 */
class AuditIT extends AbstractIntegrationTest {

    private static final String LOGS = "/api/v1/audit/logs";
    private static final String CATALOG = "/api/v1/catalog";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditRecorder recorder;
    @Autowired AuditedOperationProbe probe;

    Account adminAccount;
    String admin;
    String tag;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        adminAccount = accounts.activeUser(RoleCodes.ADMIN);
        admin = tokenFor(adminAccount);
        tag = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    // ------------------------------------------------------------------ automatic entity auditing

    @Test
    void creatingARecordIsAuditedWithActorRequestDetailsAndValues() {
        String categoryId = createCategory();
        String serviceId = as(admin).header("User-Agent", "AuditIT-Browser").header("X-Request-Id", "audit-it-" + tag)
                .body(serviceBody(categoryId, "SVC_" + tag, "Service " + tag, Map.of(
                        "billingType", "ONE_TIME", "basePrice", 1500, "unitLabel", "per visit")))
                .post(CATALOG + "/services").then().statusCode(201).extract().path("id");

        Response list = as(admin).queryParam("entityType", "Service").queryParam("entityId", serviceId).get(LOGS);
        list.then().statusCode(200).body("totalItems", equalTo(1))
                .body("items[0].action", equalTo("CREATE"))
                .body("items[0].module", equalTo("catalog"))
                .body("items[0].entityType", equalTo("Service"))
                .body("items[0].entityLabel", equalTo("Service " + tag))
                .body("items[0].summary", equalTo("Created Service 'Service " + tag + "'"))
                .body("items[0].actorId", equalTo(adminAccount.id().toString()))
                .body("items[0].actorLabel", equalTo(adminAccount.email()))
                .body("items[0].ipAddress", notNullValue())
                .body("items[0].occurredAt", notNullValue())
                // the list stays light: values are on the detail endpoint
                .body("items[0]", not(hasKey("after")));

        as(admin).get(LOGS + "/" + list.path("items[0].id")).then().statusCode(200)
                .body("entry.action", equalTo("CREATE"))
                .body("before", nullValue())
                .body("after.code", equalTo("SVC_" + tag))
                .body("after.name", equalTo("Service " + tag))
                .body("after.billingType", equalTo("ONE_TIME"))
                .body("after.basePrice", equalTo(1500))
                .body("after.active", equalTo(true))
                .body("after.category", equalTo(categoryId)) // a reference is stored as the id
                // technical columns are not audit content
                .body("after", not(hasKey("version")))
                .body("after", not(hasKey("createdAt")))
                .body("after", not(hasKey("updatedBy")))
                .body("userAgent", equalTo("AuditIT-Browser"))
                .body("requestId", equalTo("audit-it-" + tag));
    }

    @Test
    void anUpdateRecordsOnlyTheFieldsThatChangedWithBeforeAndAfter() {
        String categoryId = createCategory();
        String serviceId = createService(categoryId, Map.of("billingType", "ONE_TIME", "basePrice", 100));

        Map<String, Object> update = serviceBody(categoryId, null, "Renamed " + tag,
                Map.of("billingType", "ONE_TIME", "basePrice", 120));
        update.put("version", 0);
        as(admin).body(update).put(CATALOG + "/services/" + serviceId).then().statusCode(200);

        String auditId = as(admin).queryParam("entityId", serviceId).queryParam("action", "UPDATE").get(LOGS)
                .then().body("totalItems", equalTo(1))
                .body("items[0].summary", equalTo("Updated Service 'Renamed " + tag + "': basePrice, name"))
                .extract().path("items[0].id");
        as(admin).get(LOGS + "/" + auditId).then()
                .body("before.name", equalTo("Service " + tag))
                .body("after.name", equalTo("Renamed " + tag))
                .body("before.basePrice", equalTo(100.0f))
                .body("after.basePrice", equalTo(120))
                .body("before.size()", equalTo(2))
                .body("after.size()", equalTo(2));
    }

    @Test
    void anUpdateThatChangesNothingIsNotAudited() {
        String categoryId = createCategory();
        String serviceId = createService(categoryId, Map.of());

        Map<String, Object> sameAgain = serviceBody(categoryId, null, "Service " + tag, Map.of());
        sameAgain.put("version", 0);
        as(admin).body(sameAgain).put(CATALOG + "/services/" + serviceId).then().statusCode(200);

        as(admin).queryParam("entityId", serviceId).get(LOGS).then().body("items.action", contains("CREATE"));
    }

    @Test
    void switchingOffIsAStatusChangeAndASoftDeleteIsADelete() {
        String categoryId = createCategory();
        String serviceId = createService(categoryId, Map.of());

        as(admin).post(CATALOG + "/services/" + serviceId + "/deactivate").then().statusCode(200);
        as(admin).post(CATALOG + "/services/" + serviceId + "/activate").then().statusCode(200);
        as(admin).delete(CATALOG + "/services/" + serviceId).then().statusCode(204);

        Response log = as(admin).queryParam("entityId", serviceId).get(LOGS);
        log.then().body("items.action", contains("DELETE", "STATUS_CHANGE", "STATUS_CHANGE", "CREATE")) // newest first
                .body("items.summary", contains(
                        "Deleted Service 'Service " + tag + "'",
                        "Activated Service 'Service " + tag + "'",
                        "Deactivated Service 'Service " + tag + "'",
                        "Created Service 'Service " + tag + "'"));

        as(admin).get(LOGS + "/" + log.path("items[2].id")).then()
                .body("before.active", equalTo(true)).body("after.active", equalTo(false))
                .body("after.size()", equalTo(1));
        // What was deleted is kept in full, so it can be seen (or recreated) later.
        as(admin).get(LOGS + "/" + log.path("items[0].id")).then()
                .body("before.code", equalTo("SVC_" + tag)).body("before.name", equalTo("Service " + tag))
                .body("after", nullValue());
    }

    @Test
    void aChangeThatFailsLeavesNoAuditRow() {
        String categoryId = createCategory();
        createService(categoryId, Map.of());

        // Same code again: rejected with 409, so nothing was created and nothing may be logged.
        as(admin).body(serviceBody(categoryId, "SVC_" + tag, "Duplicate " + tag, Map.of()))
                .post(CATALOG + "/services").then().statusCode(409);

        as(admin).queryParam("search", "Duplicate " + tag).get(LOGS).then().body("totalItems", equalTo(0));
    }

    @Test
    void otherModulesAreAuditedTheSameWayWithoutAnyCodeOfTheirOwn() {
        String branchId = as(admin)
                .body(Map.of("code", "LKO", "name", "Lucknow Office", "city", "Lucknow", "state", "Uttar Pradesh"))
                .post("/api/v1/branches").then().statusCode(201).extract().path("id");
        as(admin).post("/api/v1/branches/" + branchId + "/deactivate").then().statusCode(200);

        as(admin).queryParam("entityType", "Branch").queryParam("entityId", branchId).get(LOGS)
                .then().body("items.action", contains("STATUS_CHANGE", "CREATE"))
                .body("items.module", everyItem(equalTo("identity")));
    }

    @Test
    void deactivatingAUserIsAStatusChangeOnThatUser() {
        Account colleague = accounts.activeColleagueOf(adminAccount, RoleCodes.SALES_EXECUTIVE);

        as(admin).post("/api/v1/users/" + colleague.id() + "/deactivate").then().statusCode(200);

        String auditId = as(admin).queryParam("entityType", "User").queryParam("entityId", colleague.id().toString())
                .queryParam("action", "STATUS_CHANGE").get(LOGS)
                .then().body("totalItems", equalTo(1))
                .body("items[0].entityLabel", equalTo(colleague.email()))
                .body("items[0].actorLabel", equalTo(adminAccount.email()))
                .extract().path("items[0].id");
        as(admin).get(LOGS + "/" + auditId).then()
                .body("before.status", equalTo("ACTIVE")).body("after.status", equalTo("SUSPENDED"));
    }

    // ------------------------------------------------------------------ sign-in events and secrets

    @Test
    void signInSignOutAndFailedAttemptsAreAudited() {
        Account user = accounts.activeColleagueOf(adminAccount, RoleCodes.HR);
        login(user.email(), "wrong-password").then().statusCode(401);
        String cookie = login(user.email(), user.password()).then().statusCode(200).extract().cookie("bos_refresh");
        given().cookie("bos_refresh", cookie).post("/api/v1/auth/logout").then().statusCode(204);
        given().cookie("bos_refresh", cookie).post("/api/v1/auth/logout").then().statusCode(204); // no second event

        Response log = as(admin).queryParam("actorId", user.id().toString()).get(LOGS);
        log.then().body("items.action", contains("LOGOUT", "LOGIN", "LOGIN"))
                .body("items.summary", contains("Signed out", "Signed in", "Sign-in failed"))
                .body("items.ipAddress", everyItem(notNullValue()));

        as(admin).get(LOGS + "/" + log.path("items[2].id")).then()
                .body("metadata.outcome", equalTo("FAILURE")).body("metadata.reason", equalTo("WRONG_PASSWORD"));
        as(admin).get(LOGS + "/" + log.path("items[1].id")).then()
                .body("metadata.outcome", equalTo("SUCCESS")).body("metadata.sessionId", notNullValue());
    }

    @Test
    void signInAttemptForAnUnknownEmailIsOnRecordButShownToNoOrganization() {
        String email = "nobody-" + tag.toLowerCase() + "@example.com";
        login(email, "whatever-password").then().statusCode(401);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'LOGIN' AND actor_label = ? "
                        + "AND organization_id IS NULL AND metadata->>'reason' = 'UNKNOWN_EMAIL'",
                Long.class, email)).isEqualTo(1);
        as(admin).queryParam("search", email).get(LOGS).then().body("totalItems", equalTo(0));
    }

    @Test
    void passwordsAndHashesNeverReachTheAuditLog() {
        Account user = accounts.activeColleagueOf(adminAccount, RoleCodes.HR);
        String token = tokenFor(user);
        String newPassword = "A-Fresh-Password-" + tag;
        as(token).body(Map.of("currentPassword", user.password(), "newPassword", newPassword))
                .post("/api/v1/auth/password/change").then().statusCode(204);

        // The change itself is recorded...
        as(admin).queryParam("entityId", user.id().toString()).queryParam("action", "UPDATE").get(LOGS)
                .then().body("items.summary", hasItem("Changed own password"));
        // ...but no row about this user, in either table, contains the password or any hash.
        List<String> rows = jdbc.queryForList(
                "SELECT concat_ws(' ', summary, before_value::text, after_value::text, metadata::text) "
                        + "FROM audit_logs WHERE entity_id = ? OR actor_id = ?",
                String.class, user.id().toString(), user.id());
        rows.addAll(jdbc.queryForList(
                "SELECT concat_ws(' ', message, changes::text) FROM entity_activity_logs WHERE entity_id = ?",
                String.class, user.id().toString()));
        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row)
                .doesNotContain(newPassword).doesNotContain(user.password())
                .doesNotContain("{bcrypt}").doesNotContainIgnoringCase("passwordHash"));
    }

    @Test
    void signingInDoesNotProduceNoiseAboutLastLoginBookkeeping() {
        Account user = accounts.activeColleagueOf(adminAccount, RoleCodes.HR);
        login(user.email(), user.password());

        as(admin).queryParam("entityType", "User").queryParam("entityId", user.id().toString()).get(LOGS)
                .then().body("items.action", everyItem(equalTo("LOGIN")));
    }

    // ------------------------------------------------------------------ explicit events

    @Test
    void recorderAcceptsBusinessEventsSuchAsApprovalsAndPayments() {
        UUID invoiceId = UUID.randomUUID();
        Instant paidAt = Instant.parse("2026-10-01T10:15:30Z");

        recorder.record(AuditEvent.of(AuditAction.APPROVAL, "finance", "Approved invoice " + tag)
                .actor(adminAccount.id(), adminAccount.email(), adminAccount.organizationId())
                .entity("Invoice", invoiceId, "INV-" + tag)
                .before(Map.of("status", "PENDING"))
                .after(Map.of("status", "APPROVED")));
        recorder.record(AuditEvent.of(AuditAction.PAYMENT, "finance", "Recorded payment " + tag)
                .actor(adminAccount.id(), adminAccount.email(), adminAccount.organizationId())
                .entity("Invoice", invoiceId, "INV-" + tag)
                .metadata("amount", new java.math.BigDecimal("2500.50"))
                .metadata("method", "UPI")
                .metadata("paidAt", paidAt)
                .metadata("nothing", null));

        Response log = as(admin).queryParam("module", "finance").queryParam("entityId", invoiceId.toString()).get(LOGS);
        // Recorded within the same millisecond on a fast machine, so their order is not asserted.
        log.then().body("items.action", containsInAnyOrder("PAYMENT", "APPROVAL"));
        as(admin).get(LOGS + "/" + log.path("items.find { it.action == 'PAYMENT' }.id")).then()
                .body("metadata.amount", equalTo(2500.50f))
                .body("metadata.method", equalTo("UPI"))
                .body("metadata.paidAt", equalTo("2026-10-01T10:15:30Z"))
                .body("metadata", not(hasKey("nothing")));

        // The same events form the invoice's timeline, with the field-level change spelled out.
        as(admin).queryParam("entityType", "Invoice").queryParam("entityId", invoiceId.toString())
                .get("/api/v1/audit/activity")
                .then().statusCode(200).body("totalItems", equalTo(2))
                .body("items.action", contains("PAYMENT", "APPROVAL"))
                .body("items[1].message", equalTo("Approved invoice " + tag))
                .body("items[1].changes[0].field", equalTo("status"))
                .body("items[1].changes[0].from", equalTo("PENDING"))
                .body("items[1].changes[0].to", equalTo("APPROVED"))
                .body("items[1].auditLogId", equalTo(log.path("items[1].id")))
                .body("items[0].changes", nullValue());
    }

    @Test
    void annotatedOperationIsAuditedWhenItSucceedsAndNotWhenItFails() {
        long before = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE module = 'probe'", Long.class);

        probe.exportCustomers("xlsx-" + tag, 250);
        assertThatThrownBy(() -> probe.failingImport("prices-" + tag + ".csv")).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE module = 'probe'", Long.class))
                .isEqualTo(before + 1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT action, entity_type, summary, actor_label, metadata->>'format' AS format, "
                        + "metadata->>'rowCount' AS row_count FROM audit_logs "
                        + "WHERE module = 'probe' AND metadata->>'format' = ?", "xlsx-" + tag);
        assertThat(row).containsEntry("action", "EXPORT").containsEntry("entity_type", "Customer")
                .containsEntry("summary", "Exported customers").containsEntry("row_count", "250")
                .containsEntry("actor_label", "system"); // called outside a request
    }

    // ------------------------------------------------------------------ the audit screen's API

    @Test
    void logCanBeFilteredByUserModuleActionEntityDateAndText() {
        Account colleague = accounts.activeColleagueOf(adminAccount, RoleCodes.ADMIN);
        String colleagueToken = tokenFor(colleague);
        Instant start = Instant.now();
        String categoryId = createCategory();                                   // admin, catalog, CREATE Category
        String serviceId = createService(categoryId, Map.of());                 // admin, catalog, CREATE Service
        as(colleagueToken).post(CATALOG + "/services/" + serviceId + "/deactivate");   // colleague, STATUS_CHANGE
        as(colleagueToken).body(Map.of("code", "B" + tag, "name", "Branch " + tag, "city", "Pune", "state", "Maharashtra"))
                .post("/api/v1/branches").then().statusCode(201);               // colleague, identity, CREATE Branch
        Instant end = Instant.now().plusSeconds(1);

        // user
        as(admin).queryParam("actorId", colleague.id().toString()).queryParam("from", start.toString()).get(LOGS)
                .then().body("items.action", containsInAnyOrder("STATUS_CHANGE", "CREATE"));
        // module
        as(admin).queryParam("module", "catalog").queryParam("from", start.toString()).get(LOGS)
                .then().body("totalItems", equalTo(3)).body("items.module", everyItem(equalTo("catalog")));
        // action
        as(admin).queryParam("action", "CREATE").queryParam("from", start.toString()).get(LOGS)
                .then().body("items.entityType", containsInAnyOrder("Category", "Service", "Branch"));
        // entity
        as(admin).queryParam("entityType", "Service").queryParam("from", start.toString()).get(LOGS)
                .then().body("items.action", contains("STATUS_CHANGE", "CREATE"));
        as(admin).queryParam("entityType", "Service").queryParam("entityId", serviceId).get(LOGS)
                .then().body("totalItems", equalTo(2));
        // date: from is inclusive, to is exclusive
        as(admin).queryParam("from", end.toString()).get(LOGS).then().body("totalItems", equalTo(0));
        as(admin).queryParam("to", start.toString()).queryParam("module", "catalog").get(LOGS)
                .then().body("totalItems", equalTo(0));
        as(admin).queryParam("from", start.toString()).queryParam("to", end.toString()).queryParam("module", "catalog")
                .get(LOGS).then().body("totalItems", equalTo(3));
        // text: summary, user or entity name
        as(admin).queryParam("search", "branch " + tag.toLowerCase()).get(LOGS)
                .then().body("items.entityType", contains("Branch"));
        as(admin).queryParam("search", colleague.email().toUpperCase()).queryParam("module", "catalog").get(LOGS)
                .then().body("items.action", contains("STATUS_CHANGE"));
        // combined
        as(admin).queryParam("actorId", adminAccount.id().toString()).queryParam("module", "catalog")
                .queryParam("action", "CREATE").queryParam("entityType", "Category").queryParam("from", start.toString())
                .get(LOGS).then().body("totalItems", equalTo(1)).body("items[0].entityId", equalTo(categoryId));
    }

    @Test
    void logIsPagedNewestFirst() {
        String categoryId = createCategory();
        String serviceId = createService(categoryId, Map.of());
        for (int i = 0; i < 2; i++) {
            as(admin).post(CATALOG + "/services/" + serviceId + "/deactivate");
            as(admin).post(CATALOG + "/services/" + serviceId + "/activate");
        }

        as(admin).queryParam("entityId", serviceId).queryParam("size", 2).get(LOGS)
                .then().body("totalItems", equalTo(5)).body("totalPages", equalTo(3)).body("size", equalTo(2))
                .body("items.summary", contains(
                        "Activated Service 'Service " + tag + "'", "Deactivated Service 'Service " + tag + "'"));
        as(admin).queryParam("entityId", serviceId).queryParam("size", 2).queryParam("page", 2).get(LOGS)
                .then().body("items.action", contains("CREATE"));
        as(admin).queryParam("size", 500).get(LOGS).then().statusCode(400);
        as(admin).queryParam("action", "TELEPORT").get(LOGS).then().statusCode(400);
        as(admin).queryParam("from", "yesterday").get(LOGS).then().statusCode(400);
        as(admin).queryParam("actorId", "not-a-uuid").get(LOGS).then().statusCode(400);
    }

    @Test
    void facetsListWhatOccursInTheLog() {
        createService(createCategory(), Map.of());

        as(admin).get("/api/v1/audit/facets").then().statusCode(200)
                .body("modules", hasItems("catalog", "identity"))
                .body("entityTypes", hasItems("Category", "Service", "User"))
                .body("actors.id", contains(adminAccount.id().toString()))
                .body("actors.label", contains(adminAccount.email()));
    }

    // ------------------------------------------------------------------ access and integrity

    @Test
    void auditLogRequiresAuthenticationAndThePermission() {
        given().get(LOGS).then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));

        String salesExecutive = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.SALES_EXECUTIVE));
        as(salesExecutive).get(LOGS).then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        as(salesExecutive).get("/api/v1/audit/facets").then().statusCode(403);
        as(salesExecutive).queryParam("entityType", "Service").queryParam("entityId", "x")
                .get("/api/v1/audit/activity").then().statusCode(403);

        String director = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.DIRECTOR));
        as(director).get(LOGS).then().statusCode(200);
    }

    @Test
    void anOrganizationSeesOnlyItsOwnAuditLog() {
        String serviceId = createService(createCategory(), Map.of());
        String auditId = as(admin).queryParam("entityId", serviceId).get(LOGS).path("items[0].id");
        String outsider = tokenFor(accounts.activeUser(RoleCodes.ADMIN));

        as(outsider).queryParam("entityId", serviceId).get(LOGS).then().body("totalItems", equalTo(0));
        as(outsider).get(LOGS + "/" + auditId).then().statusCode(404);
        as(outsider).queryParam("entityType", "Service").queryParam("entityId", serviceId)
                .get("/api/v1/audit/activity").then().body("totalItems", equalTo(0));
        as(outsider).get("/api/v1/audit/facets").then().body("actors.label", not(hasItem(adminAccount.email())));
    }

    @Test
    void thereIsNoWayToWriteChangeOrRemoveAuditRowsThroughTheApi() {
        String serviceId = createService(createCategory(), Map.of());
        String auditId = as(admin).queryParam("entityId", serviceId).get(LOGS).path("items[0].id");
        String superAdmin = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.SUPER_ADMIN));

        as(superAdmin).body(Map.of("action", "CREATE", "summary", "forged")).post(LOGS).then().statusCode(405);
        as(superAdmin).body(Map.of("summary", "edited")).put(LOGS + "/" + auditId).then().statusCode(405);
        as(superAdmin).delete(LOGS + "/" + auditId).then().statusCode(405);
    }

    @Test
    void theDatabaseItselfRefusesToChangeOrDeleteAuditRows() {
        String serviceId = createService(createCategory(), Map.of());

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET summary = 'tampered' WHERE entity_id = ?", serviceId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE entity_id = ?", serviceId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");

        as(admin).queryParam("entityId", serviceId).get(LOGS)
                .then().body("totalItems", equalTo(1)).body("items[0].summary", containsString("Created Service"));
    }

    @Test
    void activityTimelineOfARecordListsItsHistoryWithFieldChanges() {
        String categoryId = createCategory();
        String serviceId = createService(categoryId, Map.of());
        Map<String, Object> update = serviceBody(categoryId, null, "Renamed " + tag, Map.of());
        update.put("version", 0);
        as(admin).body(update).put(CATALOG + "/services/" + serviceId).then().statusCode(200);

        as(admin).queryParam("entityType", "Service").queryParam("entityId", serviceId).get("/api/v1/audit/activity")
                .then().statusCode(200).body("items", hasSize(2))
                .body("items.action", contains("UPDATE", "CREATE"))
                .body("items[0].actorLabel", equalTo(adminAccount.email()))
                .body("items[0].changes", hasSize(1))
                .body("items[0].changes[0].field", equalTo("name"))
                .body("items[0].changes[0].from", equalTo("Service " + tag))
                .body("items[0].changes[0].to", equalTo("Renamed " + tag))
                .body("items[1].message", equalTo("Created Service 'Service " + tag + "'"));
        as(admin).get("/api/v1/audit/activity").then().statusCode(400); // entityType and entityId are required
    }

    // ------------------------------------------------------------------ helpers

    private static Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
                .post("/api/v1/auth/login");
    }

    private String createCategory() {
        return as(admin).body(Map.of("vertical", "SOLAR", "code", "CAT_" + tag, "name", "Category " + tag))
                .post(CATALOG + "/categories").then().statusCode(201).extract().path("id");
    }

    private String createService(String categoryId, Map<String, Object> extra) {
        return as(admin).body(serviceBody(categoryId, "SVC_" + tag, "Service " + tag, extra))
                .post(CATALOG + "/services").then().statusCode(201).extract().path("id");
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
