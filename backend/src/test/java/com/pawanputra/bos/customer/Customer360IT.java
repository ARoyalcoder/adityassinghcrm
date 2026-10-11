package com.pawanputra.bos.customer;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.pawanputra.bos.customer.api.CustomerHealthContributor.Impact;
import com.pawanputra.bos.customer.api.CustomerHealthContributor.Signal;
import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEventTypes;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.FutureModuleProbe;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** The customer 360: interactions, the unified timeline, health and summary, over real HTTP and PostgreSQL. */
class Customer360IT extends AbstractIntegrationTest {

    private static final String CUSTOMERS = "/api/v1/customers";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired FutureModuleProbe futureModules;
    @Autowired JdbcTemplate jdbc;

    Account managerAccount;
    String manager;
    String customerId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        managerAccount = accounts.activeUser(RoleCodes.SALES_MANAGER);
        manager = tokenFor(managerAccount);
        customerId = as(manager)
                .body(Map.of("type", "BUSINESS", "companyName", "Acme Solar", "phone", "9876543210"))
                .post(CUSTOMERS).then().statusCode(201).extract().path("id");
    }

    // ------------------------------------------------------------------ interactions

    @Test
    void interactionsAreLoggedListedAndRemoved() {
        Instant yesterday = Instant.now().minus(Duration.ofDays(1));
        String callId = logInteraction(Map.of(
                "type", "CALL", "direction", "OUTBOUND", "occurredAt", yesterday.toString(),
                "subject", "  Followed up on rooftop quote ", "details", "Spoke to Asha.",
                "outcome", "Will confirm by Friday"))
                .then().statusCode(201)
                .body("type", equalTo("CALL"))
                .body("direction", equalTo("OUTBOUND"))
                .body("subject", equalTo("Followed up on rooftop quote"))
                .body("outcome", equalTo("Will confirm by Friday"))
                .body("loggedByName", equalTo("Test User"))
                .extract().path("id");
        // With no time given it is "just now"; direction is optional.
        logInteraction(Map.of("type", "MEETING", "subject", "Site walk-through")).then().statusCode(201)
                .body("direction", nullValue()).body("occurredAt", notNullValue());

        as(manager).get(CUSTOMERS + "/" + customerId + "/interactions").then().statusCode(200)
                .body("totalItems", equalTo(2))
                .body("items.subject", contains("Site walk-through", "Followed up on rooftop quote")); // newest first

        as(manager).delete(CUSTOMERS + "/" + customerId + "/interactions/" + callId).then().statusCode(204);
        as(manager).delete(CUSTOMERS + "/" + customerId + "/interactions/" + callId).then().statusCode(404);
        as(manager).get(CUSTOMERS + "/" + customerId + "/interactions").then().body("items.subject", contains("Site walk-through"));
    }

    @Test
    void interactionRequestIsValidated() {
        logInteraction(Map.of("subject", " ")).then().statusCode(400)
                .body("errors.field", containsInAnyOrder("type", "subject"));
        logInteraction(Map.of("type", "TELEPATHY", "subject", "x")).then().statusCode(400);
        logInteraction(Map.of("type", "CALL", "subject", "x", "occurredAt", "tomorrow")).then().statusCode(400);
        // An interaction is a record of something that happened.
        logInteraction(Map.of("type", "CALL", "subject", "Call next week",
                "occurredAt", Instant.now().plus(Duration.ofDays(7)).toString()))
                .then().statusCode(422).body("detail", containsString("future"));
    }

    @Test
    void interactionsFollowCustomerPermissionsAndOrganization() {
        String id = logInteraction(Map.of("type", "CALL", "subject", "Intro call")).path("id");
        given().get(CUSTOMERS + "/" + customerId + "/timeline").then().statusCode(401);

        String support = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.SUPPORT_AGENT)); // view only
        as(support).get(CUSTOMERS + "/" + customerId + "/interactions").then().statusCode(200);
        as(support).get(CUSTOMERS + "/" + customerId + "/timeline").then().statusCode(200);
        as(support).get(CUSTOMERS + "/" + customerId + "/overview").then().statusCode(200);
        as(support).body(Map.of("type", "CALL", "subject", "x")).post(CUSTOMERS + "/" + customerId + "/interactions")
                .then().statusCode(403);
        as(support).delete(CUSTOMERS + "/" + customerId + "/interactions/" + id).then().statusCode(403);

        String technician = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.TECHNICIAN));
        as(technician).get(CUSTOMERS + "/" + customerId + "/timeline").then().statusCode(403);
        as(technician).get(CUSTOMERS + "/" + customerId + "/overview").then().statusCode(403);

        String outsider = tokenFor(accounts.activeUser(RoleCodes.SALES_MANAGER));
        as(outsider).get(CUSTOMERS + "/" + customerId + "/timeline").then().statusCode(404);
        as(outsider).get(CUSTOMERS + "/" + customerId + "/overview").then().statusCode(404);
        as(outsider).get(CUSTOMERS + "/" + customerId + "/interactions").then().statusCode(404);
        as(outsider).body(Map.of("type", "CALL", "subject", "x")).post(CUSTOMERS + "/" + customerId + "/interactions")
                .then().statusCode(404);
    }

    // ------------------------------------------------------------------ timeline

    @Test
    void timelineMergesEveryRealSourceInChronologicalOrder() {
        Instant threeDaysAgo = Instant.now().minus(Duration.ofDays(3));
        Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2));
        // Entered today, but they happened earlier: the timeline is ordered by when things happened.
        logInteraction(Map.of("type", "CALL", "direction", "INBOUND", "occurredAt", threeDaysAgo.toString(),
                "subject", "Asked about rooftop solar", "outcome", "Wants a site visit"));
        logInteraction(Map.of("type", "SITE_VISIT", "occurredAt", twoDaysAgo.toString(), "subject", "Roof survey"));
        as(manager).body(Map.of("name", "Asha Verma", "phone", "9000000001"))
                .post(CUSTOMERS + "/" + customerId + "/contacts").then().statusCode(201);
        as(manager).body(Map.of("body", "Prefers calls after 5 pm."))
                .post(CUSTOMERS + "/" + customerId + "/notes").then().statusCode(201);
        Map<String, Object> update = new HashMap<>(Map.of("type", "BUSINESS", "companyName", "Acme Solar",
                "phone", "9876543210", "status", "INACTIVE", "version", 0));
        as(manager).body(update).put(CUSTOMERS + "/" + customerId).then().statusCode(200);
        logInteraction(Map.of("type", "MEETING", "subject", "Proposal review"));

        Response timeline = as(manager).get(CUSTOMERS + "/" + customerId + "/timeline");

        timeline.then().statusCode(200)
                .body("events.title", contains(
                        "Meeting completed", "Status changed", "Note added", "Contact added", "Customer created",
                        "Site visit completed", "Call logged"))
                .body("events.type", contains(
                        "MEETING_COMPLETED", "CUSTOMER_STATUS_CHANGED", "NOTE_ADDED", "CONTACT_ADDED",
                        "CUSTOMER_CREATED", "SITE_VISIT_COMPLETED", "CALL_LOGGED"))
                .body("events.category", contains(
                        "COMMUNICATION", "RECORD", "COMMUNICATION", "RECORD", "RECORD", "COMMUNICATION", "COMMUNICATION"))
                .body("nextCursor", nullValue());
        // Each event carries what a person needs to read it.
        timeline.then()
                .body("events[0].description", equalTo("Proposal review"))
                .body("events[0].actor", equalTo("Test User"))
                .body("events[0].reference.entity", equalTo("Interaction"))
                .body("events[2].description", equalTo("Prefers calls after 5 pm."))
                .body("events[3].description", equalTo("Created Contact 'Asha Verma'"))
                .body("events[3].actor", equalTo(managerAccount.email()))
                .body("events[6].description", equalTo("Asked about rooftop solar"))
                .body("events[6].details.Direction", equalTo("Incoming"))
                .body("events[6].details.Outcome", equalTo("Wants a site visit"));
    }

    @Test
    void nothingAppearsOnTheTimelineTwice() {
        logInteraction(Map.of("type", "CALL", "subject", "Intro call"));
        as(manager).body(Map.of("body", "A note.")).post(CUSTOMERS + "/" + customerId + "/notes").then().statusCode(201);

        Response timeline = as(manager).get(CUSTOMERS + "/" + customerId + "/timeline");

        // An interaction and a note are each audited as well, but the timeline shows the record once.
        timeline.then().body("events.title", contains("Note added", "Call logged", "Customer created"));
        List<String> ids = timeline.path("events.id");
        assertThat(ids).doesNotHaveDuplicates();
        // The audit log still has both, as it should.
        String admin = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.ADMIN));
        as(admin).queryParam("module", "customers").get("/api/v1/audit/logs")
                .then().body("items.entityType", hasItems("Interaction", "Note", "Customer"));
    }

    @Test
    void removingARecordRemovesItsEventBecauseNothingIsCopied() {
        String callId = logInteraction(Map.of("type", "CALL", "subject", "Logged by mistake")).path("id");
        as(manager).get(CUSTOMERS + "/" + customerId + "/timeline").then().body("events.title", hasItem("Call logged"));

        as(manager).delete(CUSTOMERS + "/" + customerId + "/interactions/" + callId).then().statusCode(204);

        as(manager).get(CUSTOMERS + "/" + customerId + "/timeline")
                .then().body("events.title", contains("Customer created"));
        // There is no timeline table that could have kept a stale copy.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema() "
                        + "AND table_name LIKE '%timeline%'", Long.class)).isZero();
    }

    @Test
    void aFutureModuleJoinsTheTimelineJustByProvidingASource() {
        UUID id = UUID.fromString(customerId);
        Instant base = Instant.now().minus(Duration.ofDays(10));
        String[][] events = {
            {TimelineEventTypes.LEAD_CREATED, "SALES", "Lead created"},
            {TimelineEventTypes.QUOTATION_SENT, "SALES", "Quotation sent"},
            {TimelineEventTypes.QUOTATION_ACCEPTED, "SALES", "Quotation accepted"},
            {TimelineEventTypes.ORDER_CREATED, "SALES", "Order created"},
            {TimelineEventTypes.PROJECT_STARTED, "PROJECT", "Project started"},
            {TimelineEventTypes.MILESTONE_COMPLETED, "PROJECT", "Milestone completed"},
            {TimelineEventTypes.INVOICE_GENERATED, "FINANCE", "Invoice generated"},
            {TimelineEventTypes.PAYMENT_RECEIVED, "FINANCE", "Payment received"},
            {TimelineEventTypes.TICKET_CREATED, "SERVICE", "Ticket created"},
            {TimelineEventTypes.TICKET_RESOLVED, "SERVICE", "Ticket resolved"},
            {TimelineEventTypes.AMC_CREATED, "AMC", "AMC created"},
            {TimelineEventTypes.AMC_RENEWED, "AMC", "AMC renewed"},
        };
        for (int i = 0; i < events.length; i++) {
            futureModules.addEvent(id, "probe-" + i + ":" + customerId, events[i][0],
                    TimelineCategory.valueOf(events[i][1]), base.plus(Duration.ofHours(i)), events[i][2], "Detail " + i);
        }
        logInteraction(Map.of("type", "CALL", "subject", "Welcome call",
                "occurredAt", base.plus(Duration.ofMinutes(90)).toString())); // between "Quotation sent" and "accepted"

        Response timeline = as(manager).queryParam("limit", 50).get(CUSTOMERS + "/" + customerId + "/timeline");

        timeline.then().statusCode(200).body("events.title", contains(
                "Customer created",
                "AMC renewed", "AMC created", "Ticket resolved", "Ticket created", "Payment received",
                "Invoice generated", "Milestone completed", "Project started", "Order created", "Quotation accepted",
                "Call logged",
                "Quotation sent", "Lead created"));
        timeline.then()
                .body("events[1].type", equalTo("AMC_RENEWED"))
                .body("events[1].category", equalTo("AMC"))
                .body("events[1].reference.entity", equalTo("ProbeRecord"))
                .body("events[1].details.Amount", equalTo("₹1,85,000"));

        // Filtering by category: one, or several.
        as(manager).queryParam("category", "FINANCE").get(CUSTOMERS + "/" + customerId + "/timeline")
                .then().body("events.title", contains("Payment received", "Invoice generated"));
        as(manager).queryParam("category", "SALES").queryParam("category", "COMMUNICATION")
                .get(CUSTOMERS + "/" + customerId + "/timeline")
                .then().body("events.title", contains(
                        "Order created", "Quotation accepted", "Call logged", "Quotation sent", "Lead created"));
        as(manager).queryParam("category", "RECORD").get(CUSTOMERS + "/" + customerId + "/timeline")
                .then().body("events.title", contains("Customer created"));
        as(manager).queryParam("category", "GOSSIP").get(CUSTOMERS + "/" + customerId + "/timeline").then().statusCode(400);

        // Another customer's timeline is unaffected.
        String otherId = as(manager).body(Map.of("type", "INDIVIDUAL", "firstName", "Ravi"))
                .post(CUSTOMERS).then().statusCode(201).extract().path("id");
        as(manager).get(CUSTOMERS + "/" + otherId + "/timeline").then().body("events.title", contains("Customer created"));
    }

    @Test
    void timelinePagesWithACursorShowingEveryEventOnce() {
        Instant base = Instant.now().minus(Duration.ofDays(30));
        for (int i = 0; i < 23; i++) {
            logInteraction(Map.of("type", i % 2 == 0 ? "CALL" : "EMAIL", "subject", "Contact %02d".formatted(i),
                    "occurredAt", base.plus(Duration.ofHours(i)).toString()));
        }
        for (int i = 0; i < 6; i++) {
            futureModules.addEvent(UUID.fromString(customerId), "invoice-" + i + ":" + customerId,
                    TimelineEventTypes.INVOICE_GENERATED, TimelineCategory.FINANCE,
                    base.plus(Duration.ofHours(i * 4)).plusSeconds(60), "Invoice generated", "INV-" + i);
        }

        List<String> ids = new ArrayList<>();
        List<String> times = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            Response page = cursor == null
                    ? as(manager).queryParam("limit", 7).get(CUSTOMERS + "/" + customerId + "/timeline")
                    : as(manager).queryParam("limit", 7).queryParam("cursor", cursor)
                            .get(CUSTOMERS + "/" + customerId + "/timeline");
            page.then().statusCode(200);
            List<String> pageIds = page.path("events.id");
            assertThat(pageIds).hasSizeLessThanOrEqualTo(7);
            ids.addAll(pageIds);
            times.addAll(page.<List<String>>path("events.occurredAt"));
            cursor = page.path("nextCursor");
            pages++;
        } while (cursor != null && pages < 20);

        assertThat(ids).hasSize(30).doesNotHaveDuplicates(); // 23 interactions + 6 invoices + "Customer created"
        assertThat(pages).isEqualTo(5);
        assertThat(times.stream().map(Instant::parse).toList())
                .isSortedAccordingTo(java.util.Comparator.<Instant>naturalOrder().reversed());

        as(manager).queryParam("cursor", "not-a-cursor").get(CUSTOMERS + "/" + customerId + "/timeline")
                .then().statusCode(400).body("code", equalTo("MALFORMED_REQUEST"));
        as(manager).queryParam("limit", 0).get(CUSTOMERS + "/" + customerId + "/timeline").then().statusCode(400);
        as(manager).queryParam("limit", 101).get(CUSTOMERS + "/" + customerId + "/timeline").then().statusCode(400);
    }

    // ------------------------------------------------------------------ health and summary

    @Test
    void overviewSummarisesTheCustomerFromCurrentRecords() {
        as(manager).get(CUSTOMERS + "/" + customerId + "/overview").then().statusCode(200)
                .body("summary.contactCount", equalTo(0))
                .body("summary.interactionCount", equalTo(0))
                .body("summary.lastContactAt", nullValue())
                .body("summary.primaryContact", nullValue())
                .body("summary.billingAddress", nullValue())
                .body("summary.customerSince", notNullValue());

        as(manager).body(Map.of("name", "Asha Verma", "designation", "Owner", "phone", "9000000001"))
                .post(CUSTOMERS + "/" + customerId + "/contacts").then().statusCode(201);
        as(manager).body(Map.of("type", "BILLING", "line1", "12 MG Road", "city", "Lucknow",
                        "state", "Uttar Pradesh", "postalCode", "226001"))
                .post(CUSTOMERS + "/" + customerId + "/addresses").then().statusCode(201);
        as(manager).body(Map.of("type", "SERVICE", "line1", "Plot 4", "line2", "Industrial Area", "city", "Kanpur",
                        "state", "Uttar Pradesh"))
                .post(CUSTOMERS + "/" + customerId + "/addresses").then().statusCode(201);
        as(manager).body(Map.of("body", "A note.")).post(CUSTOMERS + "/" + customerId + "/notes").then().statusCode(201);
        logInteraction(Map.of("type", "CALL", "subject", "Older call",
                "occurredAt", Instant.now().minus(Duration.ofDays(5)).toString()));
        logInteraction(Map.of("type", "MEETING", "subject", "Latest meeting",
                "occurredAt", Instant.now().minus(Duration.ofDays(2)).toString()));

        as(manager).get(CUSTOMERS + "/" + customerId + "/overview").then().statusCode(200)
                .body("summary.contactCount", equalTo(1))
                .body("summary.addressCount", equalTo(2))
                .body("summary.interactionCount", equalTo(2))
                .body("summary.noteCount", equalTo(1))
                .body("summary.lastContactSubject", equalTo("Latest meeting"))
                .body("summary.lastContactAt", notNullValue())
                .body("summary.primaryContact.name", equalTo("Asha Verma"))
                .body("summary.primaryContact.designation", equalTo("Owner"))
                .body("summary.billingAddress", equalTo("12 MG Road, Lucknow, Uttar Pradesh 226001"))
                .body("summary.serviceAddress", equalTo("Plot 4, Industrial Area, Kanpur, Uttar Pradesh"))
                // no sales, finance or service figures exist yet, and none are invented
                .body("summary", not(hasKey("totalRevenue")))
                .body("summary", not(hasKey("openTickets")));
    }

    @Test
    void healthExplainsItselfAndFollowsWhatIsOnRecord() {
        // A customer added a moment ago with no history: nothing to judge by.
        as(manager).get(CUSTOMERS + "/" + customerId + "/overview").then()
                .body("health.level", equalTo("UNKNOWN")).body("health.signals", empty());

        logInteraction(Map.of("type", "CALL", "subject", "Intro call"));
        as(manager).get(CUSTOMERS + "/" + customerId + "/overview").then()
                .body("health.level", equalTo("GOOD"))
                .body("health.signals.code", contains("CONTACT_RECENT"))
                .body("health.signals[0].message", equalTo("In contact today"))
                .body("health.signals[0].impact", equalTo("POSITIVE"));

        setStatus("INACTIVE", 0);
        as(manager).get(CUSTOMERS + "/" + customerId + "/overview").then()
                .body("health.level", equalTo("NEEDS_ATTENTION"))
                .body("health.signals.code", contains("CUSTOMER_INACTIVE", "CONTACT_RECENT")); // most serious first

        setStatus("BLOCKED", 1);
        as(manager).get(CUSTOMERS + "/" + customerId + "/overview").then()
                .body("health.level", equalTo("AT_RISK"))
                .body("health.signals[0].code", equalTo("CUSTOMER_BLOCKED"));
    }

    @Test
    void oldSilenceNeedsAttention() {
        logInteraction(Map.of("type", "CALL", "subject", "Last spoke long ago",
                "occurredAt", Instant.now().minus(Duration.ofDays(120)).toString()));

        as(manager).get(CUSTOMERS + "/" + customerId + "/overview").then()
                .body("health.level", equalTo("NEEDS_ATTENTION"))
                .body("health.signals.code", contains("CONTACT_STALE"))
                .body("health.signals[0].message", equalTo("Last contact was 120 days ago"));
    }

    @Test
    void customerWithNoWayToReachThemNeedsAttention() {
        String unreachable = as(manager).body(Map.of("type", "INDIVIDUAL", "firstName", "Ravi"))
                .post(CUSTOMERS).then().statusCode(201).extract().path("id");

        as(manager).get(CUSTOMERS + "/" + unreachable + "/overview").then()
                .body("health.level", equalTo("NEEDS_ATTENTION"))
                .body("health.signals.code", contains("NOT_REACHABLE"));

        // A contact's phone number counts.
        as(manager).body(Map.of("name", "Ravi's office", "phone", "9000000002"))
                .post(CUSTOMERS + "/" + unreachable + "/contacts").then().statusCode(201);
        as(manager).get(CUSTOMERS + "/" + unreachable + "/overview").then()
                .body("health.level", equalTo("UNKNOWN"));
    }

    @Test
    void aFutureModuleCanAddHealthSignals() {
        logInteraction(Map.of("type", "CALL", "subject", "Intro call"));
        futureModules.addSignal(UUID.fromString(customerId),
                new Signal("OPEN_TICKETS", Impact.WARNING, "3 open tickets"));
        futureModules.addSignal(UUID.fromString(customerId),
                new Signal("INVOICES_OVERDUE", Impact.CRITICAL, "2 invoices overdue by more than 60 days"));

        as(manager).get(CUSTOMERS + "/" + customerId + "/overview").then()
                .body("health.level", equalTo("AT_RISK"))
                .body("health.signals.code", contains("INVOICES_OVERDUE", "OPEN_TICKETS", "CONTACT_RECENT"))
                .body("health.signals.message", hasItem("2 invoices overdue by more than 60 days"))
                .body("health.signals.impact", everyItem(notNullValue()))
                .body("health.signals", hasSize(3));
    }

    // ------------------------------------------------------------------ helpers

    private Response logInteraction(Map<String, Object> body) {
        return as(manager).body(body).post(CUSTOMERS + "/" + customerId + "/interactions");
    }

    private void setStatus(String status, int version) {
        Map<String, Object> update = new HashMap<>(Map.of("type", "BUSINESS", "companyName", "Acme Solar",
                "phone", "9876543210", "status", status, "version", version));
        as(manager).body(update).put(CUSTOMERS + "/" + customerId).then().statusCode(200);
    }
}
