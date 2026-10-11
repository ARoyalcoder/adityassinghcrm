package com.pawanputra.bos.lead;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.lead.internal.LeadRescoringJob;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
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
import org.springframework.jdbc.core.JdbcTemplate;

/** Leads and their qualification over real HTTP and PostgreSQL, with the real rules engine. */
class LeadQualificationIT extends AbstractIntegrationTest {

    private static final String LEADS = "/api/v1/leads";
    private static final String QUESTIONS = "/api/v1/lead-qualification/questions";
    private static final String CATALOG = "/api/v1/catalog";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired LeadRescoringJob rescoringJob;
    @Autowired JdbcTemplate jdbc;

    Account managerAccount;
    String manager;
    String admin;
    String serviceId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // Each test gets its own organization, so its questions and leads are its own.
        managerAccount = accounts.activeUser(RoleCodes.SALES_MANAGER);
        manager = tokenFor(managerAccount);
        admin = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.ADMIN));
        serviceId = service();
    }

    // ------------------------------------------------------------------ the score, explained

    @Test
    void aLeadsScoreIsExplainedAndMovesAsThingsHappen() {
        String decisionMaker = question("decision_maker", "Decision maker identified", true,
                option("YES", "Yes", 15, false), option("NO", "No", 0, false));
        assertThat(decisionMaker).isNotNull();
        String lead = lead(Map.of("contactName", "Asha Verma", "phone", "9876543210", "estimatedValue", 750000));

        // Just created: only the value speaks for it.
        why(lead).then().statusCode(200)
                .body("score", equalTo(20))
                .body("total", equalTo(20))
                .body("engine", equalTo("rules-v1"))
                .body("factors.label", contains("High estimated value (₹5,00,000 or more)"))
                .body("factors.points", contains(20))
                .body("factors.code", contains("HIGH_VALUE"))
                .body("factors.category", contains("VALUE"))
                .body("priority", equalTo("LOW")).body("priorityReason", equalTo("Score 20 is below 30"))
                .body("health", equalTo("GOOD")).body("healthReason", equalTo("The lead is new"))
                .body("qualificationStatus", equalTo("NOT_STARTED"))
                .body("qualificationReason", equalTo("No qualification question has been answered yet"))
                .body("computedAt", notNullValue());

        // The decision maker is identified.
        answer(lead, Map.of("decision_maker", "YES")).then().statusCode(200)
                .body("score", equalTo(35))
                .body("factors.label", contains(
                        "High estimated value (₹5,00,000 or more)", "Decision maker identified: Yes"))
                .body("factors.points", contains(20, 15))
                .body("priority", equalTo("MEDIUM"))
                .body("qualificationStatus", equalTo("QUALIFIED"))
                .body("questions[0].answer", equalTo("YES"));

        // Someone calls them.
        as(manager).body(Map.of("type", "CALL", "subject", "Intro call")).post(LEADS + "/" + lead + "/activities")
                .then().statusCode(201).body("loggedByName", equalTo("Test User"));
        why(lead).then()
                .body("score", equalTo(45))
                .body("factors.label", contains(
                        "High estimated value (₹5,00,000 or more)", "Decision maker identified: Yes",
                        "Recent activity (today)"))
                .body("factors.points", contains(20, 15, 10))
                .body("healthReason", equalTo("Last activity was today"));

        // The stored assessment (what lists show) agrees with the explanation.
        as(manager).get(LEADS + "/" + lead).then()
                .body("score", equalTo(45)).body("priority", equalTo("MEDIUM")).body("health", equalTo("GOOD"))
                .body("qualificationStatus", equalTo("QUALIFIED"));
        // Each change of assessment was recorded.
        as(manager).get(LEADS + "/" + lead + "/score-history").then().statusCode(200)
                .body("score", contains(45, 35, 20))
                .body("engine", contains("rules-v1", "rules-v1", "rules-v1"));
        assertThat(jdbc.queryForObject("SELECT factors -> 2 ->> 'label' FROM lead_score_history WHERE lead_id = ? "
                + "ORDER BY computed_at DESC LIMIT 1", String.class, UUID.fromString(lead))).isEqualTo("Recent activity (today)");
    }

    @Test
    void timeAloneChangesAScoreAndTheScheduledJobCatchesIt() {
        question("decision_maker", "Decision maker identified", false,
                option("YES", "Yes", 15, false), option("NO", "No", 0, false));
        String lead = lead(Map.of("contactName", "Asha Verma", "phone", "9876543210", "estimatedValue", 750000));
        answer(lead, Map.of("decision_maker", "YES"));
        as(manager).body(Map.of("type", "CALL", "subject", "Intro call")).post(LEADS + "/" + lead + "/activities");
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(45)).body("health", equalTo("GOOD"));
        long versionBefore = as(manager).get(LEADS + "/" + lead).then().extract().jsonPath().getLong("version");

        // Three days go by and nobody touches the lead. (The test moves the records back instead of waiting.)
        jdbc.update("UPDATE lead_activities SET occurred_at = occurred_at - interval '3 days' WHERE lead_id = ?", UUID.fromString(lead));
        jdbc.update("UPDATE leads SET created_at = created_at - interval '3 days' WHERE id = ?", UUID.fromString(lead));

        // The explanation is always worked out as of now...
        why(lead).then()
                .body("score", equalTo(25))
                .body("factors.label", contains(
                        "High estimated value (₹5,00,000 or more)", "Decision maker identified: Yes",
                        "No activity for 3 days"))
                .body("factors.points", contains(20, 15, -10))
                .body("health", equalTo("NEEDS_ATTENTION"))
                .body("healthReason", equalTo("No activity for 3 days (3 or more needs a follow-up)"))
                .body("priority", equalTo("LOW"));
        // ...while the stored score waits for the job.
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(45));

        assertThat(rescoringJob.rescoreOpenLeads()).isPositive();

        as(manager).get(LEADS + "/" + lead).then()
                .body("score", equalTo(25)).body("health", equalTo("NEEDS_ATTENTION")).body("priority", equalTo("LOW"))
                // Rescoring is not an edit: someone with the lead open can still save it.
                .body("version", equalTo((int) versionBefore));
        as(manager).get(LEADS + "/" + lead + "/score-history").then().body("score[0]", equalTo(25));
        // Nothing changed since: running again changes nothing for this lead.
        int historyRows = jdbc.queryForObject("SELECT count(*) FROM lead_score_history WHERE lead_id = ?", Integer.class, UUID.fromString(lead));
        rescoringJob.rescoreOpenLeads();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead_score_history WHERE lead_id = ?", Integer.class, UUID.fromString(lead)))
                .isEqualTo(historyRows);

        // A week of silence in all: colder still.
        jdbc.update("UPDATE lead_activities SET occurred_at = occurred_at - interval '4 days' WHERE lead_id = ?", UUID.fromString(lead));
        rescoringJob.rescoreOpenLeads();
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(15)).body("health", equalTo("AT_RISK"));
        // Following up brings it back.
        as(manager).body(Map.of("type", "MEETING", "subject", "Site visit")).post(LEADS + "/" + lead + "/activities");
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(45)).body("health", equalTo("GOOD"));
    }

    @Test
    void aClosedLeadKeepsTheAssessmentItClosedWith() {
        String lead = lead(Map.of("contactName", "Ravi", "phone", "9876543210", "estimatedValue", 750000));
        as(manager).body(Map.of("type", "CALL", "subject", "Closing call")).post(LEADS + "/" + lead + "/activities");
        update(lead, 0, Map.of("contactName", "Ravi", "phone", "9876543210", "estimatedValue", 750000, "status", "WON"))
                .then().statusCode(200).body("status", equalTo("WON")).body("closedAt", notNullValue()).body("score", equalTo(30));

        // Long after it was won...
        jdbc.update("UPDATE lead_activities SET occurred_at = occurred_at - interval '30 days' WHERE lead_id = ?", UUID.fromString(lead));
        jdbc.update("UPDATE leads SET created_at = created_at - interval '30 days', closed_at = closed_at - interval '30 days' "
                + "WHERE id = ?", UUID.fromString(lead));
        rescoringJob.rescoreOpenLeads();

        // ...it is still what it was on the day it closed.
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(30)).body("health", equalTo("GOOD"));
        why(lead).then().body("score", equalTo(30))
                .body("factors.label", contains("High estimated value (₹5,00,000 or more)", "Recent activity (today)"));

        // Reopened, time counts again.
        update(lead, 1, Map.of("contactName", "Ravi", "phone", "9876543210", "estimatedValue", 750000, "status", "OPEN"))
                .then().statusCode(200).body("closedAt", nullValue()).body("score", equalTo(0)).body("health", equalTo("AT_RISK"));
    }

    // ------------------------------------------------------------------ qualification questions

    @Test
    void qualificationStatusFollowsTheRequiredQuestionsAndDisqualifyingAnswers() {
        question("budget", "Budget", true, option("CONFIRMED", "Confirmed", 20, false),
                option("UNSURE", "Not sure yet", 5, false), option("NONE", "No budget", -10, true));
        question("timeline", "When do they want it", true, option("NOW", "This month", 15, false),
                option("LATER", "Later this year", 5, false));
        question("referral", "Came through a referral", false, option("YES", "Yes", 10, false), option("NO", "No", 0, false));
        String lead = lead(Map.of("contactName", "Asha", "phone", "9876543210"));

        why(lead).then()
                .body("questions.key", contains("budget", "timeline", "referral"))
                .body("questions.required", contains(true, true, false))
                .body("questions[0].options.label", contains("Confirmed", "Not sure yet", "No budget"))
                .body("questions.answer", contains(nullValue(), nullValue(), nullValue()))
                // People answering do not see what an answer is worth.
                .body("questions[0].options[0]", not(hasKey("points")))
                .body("questions[0].options[0]", not(hasKey("disqualifies")));

        answer(lead, Map.of("referral", "YES")).then().statusCode(200)
                .body("qualificationStatus", equalTo("IN_PROGRESS"))
                .body("qualificationReason", equalTo("0 of 2 required questions answered"))
                .body("score", equalTo(10));
        answer(lead, Map.of("budget", "CONFIRMED")).then()
                .body("qualificationReason", equalTo("1 of 2 required questions answered"))
                .body("questions.answer", contains("CONFIRMED", null, "YES")); // earlier answers are kept
        answer(lead, Map.of("timeline", "NOW")).then()
                .body("qualificationStatus", equalTo("QUALIFIED"))
                .body("qualificationReason", equalTo("All 2 required questions answered, and no answer rules the lead out"))
                .body("score", equalTo(45))
                .body("factors.label", contains("Budget: Confirmed", "When do they want it: This month",
                        "Came through a referral: Yes"))
                .body("priority", equalTo("MEDIUM"));

        // One answer rules the lead out, whatever else is true.
        answer(lead, Map.of("budget", "NONE")).then()
                .body("qualificationStatus", equalTo("DISQUALIFIED"))
                .body("qualificationReason", equalTo("Ruled out by the answer to \"Budget\": No budget"))
                .body("score", equalTo(15))
                .body("factors.points", contains(-10, 15, 10))
                .body("priority", equalTo("LOW")).body("priorityReason", equalTo("The lead is disqualified"));
        as(manager).get(LEADS + "/" + lead).then().body("qualificationStatus", equalTo("DISQUALIFIED"));

        // Clearing an answer.
        Map<String, String> clear = new HashMap<>();
        clear.put("budget", null);
        answer(lead, clear).then()
                .body("qualificationStatus", equalTo("IN_PROGRESS")).body("questions[0].answer", nullValue());
    }

    @Test
    void answersAreCheckedAgainstTheQuestions() {
        question("budget", "Budget", true, option("YES", "Yes", 10, false), option("NO", "No", 0, false));
        String lead = lead(Map.of("contactName", "Asha", "phone", "9876543210"));

        answer(lead, Map.of("budget", "MAYBE", "favourite_colour", "BLUE")).then().statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", containsInAnyOrder("budget", "favourite_colour"))
                .body("errors.find { it.field == 'budget' }.message", equalTo("must be one of the question's options"))
                .body("errors.find { it.field == 'favourite_colour' }.message", equalTo("is not a qualification question"));
        why(lead).then().body("questions[0].answer", nullValue()); // nothing was saved
        as(manager).body(Map.of()).put(LEADS + "/" + lead + "/qualification/answers").then().statusCode(400);
    }

    @Test
    void changingAQuestionRescoresTheOpenLeads() {
        String id = question("budget", "Budget", false, option("YES", "Confirmed", 10, false), option("NO", "None", 0, false));
        String lead = lead(Map.of("contactName", "Asha", "phone", "9876543210"));
        answer(lead, Map.of("budget", "YES"));
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(10));

        // The answer is made worth more.
        as(manager).body(Map.of("label", "Budget confirmed", "required", false, "version", 0,
                        "options", List.of(option("YES", "Confirmed", 40, false), option("NO", "None", 0, false))))
                .put(QUESTIONS + "/" + id).then().statusCode(200).body("options[0].points", equalTo(40)).body("version", equalTo(1));
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(40)).body("priority", equalTo("MEDIUM"));
        why(lead).then().body("factors.label", contains("Budget confirmed: Confirmed"));

        // Switched off: the answer no longer counts, and the question is no longer asked.
        as(manager).post(QUESTIONS + "/" + id + "/deactivate").then().statusCode(200).body("active", equalTo(false));
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(0)).body("qualificationStatus", equalTo("NOT_STARTED"));
        why(lead).then().body("questions", empty()).body("factors", empty());
        answer(lead, Map.of("budget", "YES")).then().statusCode(400);

        // Switched back on: the answer given earlier counts again.
        as(manager).post(QUESTIONS + "/" + id + "/activate").then().statusCode(200);
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(40));

        // The option that was chosen is withdrawn: the answer falls away.
        as(manager).body(Map.of("label", "Budget confirmed", "required", false, "version", 3,
                        "options", List.of(option("SURE", "Certain", 40, false), option("NO", "None", 0, false))))
                .put(QUESTIONS + "/" + id).then().statusCode(200);
        as(manager).get(LEADS + "/" + lead).then().body("score", equalTo(0));

        as(manager).delete(QUESTIONS + "/" + id).then().statusCode(204);
        as(manager).get(QUESTIONS).then().body("$", empty());
        // The key is free again.
        as(manager).body(questionBody("budget", "Budget", false, option("A", "A", 1, false), option("B", "B", 2, false)))
                .post(QUESTIONS).then().statusCode(201);
    }

    @Test
    void aQuestionDefinitionIsValidated() {
        as(manager).body(questionBody("Budget OK", "Budget", false, option("YES", "Yes", 10, false), option("NO", "No", 0, false)))
                .post(QUESTIONS).then().statusCode(400).body("errors.field", contains("key"));
        as(manager).body(questionBody("budget", "Budget", false, option("YES", "Yes", 10, false)))
                .post(QUESTIONS).then().statusCode(400)
                .body("errors.message", contains("Give at least 2 options to choose from"));
        as(manager).body(questionBody("budget", "Budget", false, option("YES", "Yes", 10, false), option("YES", "yes", 0, false)))
                .post(QUESTIONS).then().statusCode(400)
                .body("errors.message", containsInAnyOrder("The option value 'YES' is used twice", "The option 'yes' is listed twice"));
        as(manager).body(questionBody("budget", "Budget", false, option("YES", "Yes", 101, false), option("NO", "No", 0, false)))
                .post(QUESTIONS).then().statusCode(400).body("errors.field", contains("options[0].points"));
        as(manager).body(questionBody("budget", "Budget", false, option("A", "A", 0, true), option("B", "B", 0, true)))
                .post(QUESTIONS).then().statusCode(400)
                .body("errors.message", contains("At least one option must not rule the lead out"));
        as(manager).get(QUESTIONS).then().body("$", empty());

        String id = question("budget", "Budget", true, option("YES", "Yes", 10, false), option("NO", "No", -5, true));
        as(manager).get(QUESTIONS).then()
                .body("key", contains("budget")).body("[0].required", equalTo(true)).body("[0].displayOrder", equalTo(10))
                .body("[0].options.points", contains(10, -5)).body("[0].options.disqualifies", contains(false, true));
        as(manager).body(questionBody("budget", "Budget again", false, option("A", "A", 1, false), option("B", "B", 2, false)))
                .post(QUESTIONS).then().statusCode(409).body("detail", containsString("budget"));
        as(manager).body(Map.of("label", "Budget", "version", 7,
                        "options", List.of(option("YES", "Yes", 10, false), option("NO", "No", 0, false))))
                .put(QUESTIONS + "/" + id).then().statusCode(409);
    }

    // ------------------------------------------------------------------ leads

    @Test
    void aLeadIsCreatedEditedListedAndDeleted() {
        as(manager).body(Map.of("contactName", " ", "serviceId", serviceId)).post(LEADS).then().statusCode(400);
        as(manager).body(Map.of("contactName", "Asha", "serviceId", serviceId)).post(LEADS).then().statusCode(400)
                .body("errors[0].field", equalTo("phone"))
                .body("errors[0].message", equalTo("Enter a phone number or an email address"));
        as(manager).body(Map.of("contactName", "Asha", "phone", "9876543210", "serviceId", UUID.randomUUID().toString()))
                .post(LEADS).then().statusCode(400).body("errors[0].field", equalTo("serviceId"));
        as(manager).body(Map.of("contactName", "Asha", "phone", "9876543210", "serviceId", serviceId, "estimatedValue", -1))
                .post(LEADS).then().statusCode(400);

        Response created = as(manager).body(Map.of("contactName", " Asha Verma ", "companyName", "Acme Solar",
                "email", "Asha@Acme.Example", "phone", "9876543210", "serviceId", serviceId, "estimatedValue", 150000))
                .post(LEADS);
        created.then().statusCode(201)
                .body("leadNumber", startsWith("LD-"))
                .body("contactName", equalTo("Asha Verma"))
                .body("email", equalTo("asha@acme.example"))
                .body("service.id", equalTo(serviceId)).body("service.verticalName", equalTo("Solar"))
                .body("status", equalTo("OPEN"))
                .body("score", equalTo(15)) // medium value + phone and email
                .body("priority", equalTo("LOW")).body("health", equalTo("GOOD"))
                .body("qualificationStatus", equalTo("NOT_STARTED"))
                .body("version", equalTo(0));
        String id = created.path("id");

        update(id, 0, Map.of("contactName", "Asha Verma", "phone", "9876543210", "estimatedValue", 800000))
                .then().statusCode(200).body("email", nullValue()).body("score", equalTo(20)).body("version", equalTo(1));
        update(id, 0, Map.of("contactName", "Asha", "phone", "9876543210")).then().statusCode(409);
        as(manager).body(Map.of("contactName", "Asha", "phone", "9876543210", "serviceId", serviceId))
                .put(LEADS + "/" + id).then().statusCode(400); // no version

        String other = lead(Map.of("contactName", "Ravi Kumar", "phone", "9000000001"));
        as(manager).get(LEADS).then().statusCode(200)
                .body("totalItems", equalTo(2))
                .body("items.contactName", contains("Asha Verma", "Ravi Kumar")) // highest score first
                .body("items.score", contains(20, 0))
                .body("items[0].service.name", notNullValue());
        as(manager).queryParam("search", "ravi").get(LEADS).then().body("items.id", contains(other));
        as(manager).queryParam("sort", "score,asc").get(LEADS).then().body("items.contactName", contains("Ravi Kumar", "Asha Verma"));
        as(manager).queryParam("priority", "HIGH").get(LEADS).then().body("items", empty());
        as(manager).queryParam("priority", "LOW").queryParam("qualificationStatus", "NOT_STARTED").queryParam("health", "GOOD")
                .queryParam("status", "OPEN").queryParam("serviceId", serviceId).get(LEADS).then().body("totalItems", equalTo(2));
        as(manager).queryParam("sort", "password").get(LEADS).then().statusCode(400);

        as(manager).body(Map.of("type", "CALL", "subject", " ")).post(LEADS + "/" + id + "/activities").then().statusCode(400);
        as(manager).body(Map.of("type", "CALL", "subject", "Tomorrow", "occurredAt", "2099-01-01T00:00:00Z"))
                .post(LEADS + "/" + id + "/activities").then().statusCode(422);
        as(manager).body(Map.of("type", "EMAIL", "subject", "Sent brochure")).post(LEADS + "/" + id + "/activities").then().statusCode(201);
        as(manager).get(LEADS + "/" + id + "/activities").then().body("subject", contains("Sent brochure")).body("type", contains("EMAIL"));

        as(manager).delete(LEADS + "/" + id).then().statusCode(204);
        as(manager).get(LEADS + "/" + id).then().statusCode(404);
        as(manager).get(LEADS).then().body("totalItems", equalTo(1));
    }

    @Test
    void aLeadCarriesItsServicesOwnFields() {
        String roofArea = as(admin).body(Map.of("key", "roof_area", "type", "NUMBER", "label", "Roof area", "unit", "sq ft",
                "minValue", 100, "required", true)).post(CATALOG + "/services/" + serviceId + "/custom-fields")
                .then().statusCode(201).extract().path("field.id");
        assertThat(roofArea).isNotNull();
        as(admin).body(Map.of("key", "site_available", "type", "BOOLEAN", "label", "Site available"))
                .post(CATALOG + "/services/" + serviceId + "/custom-fields").then().statusCode(201);

        // The service's rules apply to the lead, and are reported under customFields.
        as(manager).body(Map.of("contactName", "Asha", "phone", "9876543210", "serviceId", serviceId,
                        "customFields", Map.of("roof_area", 50, "camera_count", 4)))
                .post(LEADS).then().statusCode(400)
                .body("errors.field", containsInAnyOrder("customFields.roof_area", "customFields.camera_count"));
        as(manager).body(Map.of("contactName", "Asha", "phone", "9876543210", "serviceId", serviceId))
                .post(LEADS).then().statusCode(400).body("errors.field", contains("customFields.roof_area"));
        as(manager).get(LEADS).then().body("totalItems", equalTo(0)); // an invalid lead is not half-saved

        String id = as(manager).body(Map.of("contactName", "Asha", "phone", "9876543210", "serviceId", serviceId,
                        "customFields", Map.of("roof_area", 1200, "site_available", true)))
                .post(LEADS).then().statusCode(201)
                .body("customFields.field.key", contains("roof_area", "site_available"))
                .body("customFields.value", contains(1200, true))
                .body("customFields[0].field.unit", equalTo("sq ft"))
                .extract().path("id");

        update(id, 0, Map.of("contactName", "Asha", "phone", "9876543210", "customFields", Map.of("roof_area", 900)))
                .then().statusCode(200).body("customFields.value", contains(900))
                // Only the service's own answers changed, and that still counts as a change to the lead.
                .body("version", equalTo(1));
        update(id, 0, Map.of("contactName", "Asha", "phone", "9876543210", "customFields", Map.of("roof_area", 700)))
                .then().statusCode(409);

        // Moved to another service: the old service's answers do not follow it.
        String otherService = service();
        Map<String, Object> moved = new HashMap<>(Map.of("contactName", "Asha", "phone", "9876543210",
                "serviceId", otherService, "version", 1));
        as(manager).body(moved).put(LEADS + "/" + id).then().statusCode(200)
                .body("service.id", equalTo(otherService)).body("customFields", empty());
    }

    @Test
    void salesStaffAttachAFileToALeadWithoutBeingCatalogAdministrators() {
        String planField = as(admin).body(Map.of("key", "floor_plan", "type", "FILE", "label", "Floor plan",
                        "allowedFileTypes", List.of("pdf"), "maxFileSizeMb", 1))
                .post(CATALOG + "/services/" + serviceId + "/custom-fields").then().statusCode(201).extract().path("field.id");
        String executive = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.SALES_EXECUTIVE));
        byte[] pdf = "%PDF-1.7 plan".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // The upload in the catalog is for those who configure it...
        given().header("Authorization", "Bearer " + executive).multiPart("file", "plan.pdf", pdf, "application/pdf")
                .post(CATALOG + "/services/" + serviceId + "/custom-fields/floor_plan/files").then().statusCode(403);
        // ...the upload on leads is for those who enter leads, and applies the same field rules.
        given().header("Authorization", "Bearer " + executive).multiPart("file", "plan.exe", pdf, "application/octet-stream")
                .queryParam("serviceId", serviceId).queryParam("fieldKey", "floor_plan")
                .post(LEADS + "/custom-field-files").then().statusCode(400)
                .body("errors[0].message", equalTo("must be a file of type pdf"));
        Response upload = given().header("Authorization", "Bearer " + executive)
                .multiPart("file", "plan.pdf", pdf, "application/pdf")
                .queryParam("serviceId", serviceId).queryParam("fieldKey", "floor_plan").post(LEADS + "/custom-field-files");
        upload.then().statusCode(201).body("fileName", equalTo("plan.pdf"))
                .body("storageKey", startsWith("lead-fields/" + planField + "/"));

        Map<String, Object> reference = Map.of("storageKey", upload.path("storageKey"), "fileName", "plan.pdf",
                "contentType", "application/pdf", "sizeBytes", pdf.length);
        as(executive).body(Map.of("contactName", "Asha", "phone", "9876543210", "serviceId", serviceId,
                        "customFields", Map.of("floor_plan", reference)))
                .post(LEADS).then().statusCode(201)
                .body("customFields[0].field.key", equalTo("floor_plan"))
                .body("customFields[0].value.fileName", equalTo("plan.pdf"));

        // Someone who may only look at leads cannot upload.
        String director = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.DIRECTOR));
        given().header("Authorization", "Bearer " + director).multiPart("file", "plan.pdf", pdf, "application/pdf")
                .queryParam("serviceId", serviceId).queryParam("fieldKey", "floor_plan")
                .post(LEADS + "/custom-field-files").then().statusCode(403);
    }

    // ------------------------------------------------------------------ access

    @Test
    void leadsAndTheirConfigurationFollowPermissionsAndOrganization() {
        String questionId = question("budget", "Budget", false, option("YES", "Yes", 10, false), option("NO", "No", 0, false));
        String lead = lead(Map.of("contactName", "Asha", "phone", "9876543210"));

        given().get(LEADS).then().statusCode(401);
        given().get(QUESTIONS).then().statusCode(401);

        // A sales executive works leads but does not decide what answers are worth.
        String executive = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.SALES_EXECUTIVE));
        as(executive).get(LEADS).then().statusCode(200).body("totalItems", equalTo(1));
        as(executive).get(LEADS + "/" + lead + "/qualification").then().statusCode(200);
        as(executive).body(Map.of("answers", Map.of("budget", "YES"))).put(LEADS + "/" + lead + "/qualification/answers")
                .then().statusCode(200).body("score", equalTo(10));
        as(executive).body(Map.of("type", "CALL", "subject", "Hello")).post(LEADS + "/" + lead + "/activities").then().statusCode(201);
        as(executive).body(Map.of("contactName", "New", "phone", "9000000002", "serviceId", serviceId)).post(LEADS).then().statusCode(201);
        as(executive).delete(LEADS + "/" + lead).then().statusCode(403);
        as(executive).get(QUESTIONS).then().statusCode(403);
        as(executive).body(questionBody("x", "X", false, option("A", "A", 100, false), option("B", "B", 0, false)))
                .post(QUESTIONS).then().statusCode(403);
        as(executive).post(QUESTIONS + "/" + questionId + "/deactivate").then().statusCode(403);

        // A director reads; a technician has nothing to do with leads.
        String director = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.DIRECTOR));
        as(director).get(LEADS + "/" + lead + "/qualification").then().statusCode(200);
        as(director).body(Map.of("answers", Map.of("budget", "NO"))).put(LEADS + "/" + lead + "/qualification/answers").then().statusCode(403);
        as(director).body(Map.of("type", "CALL", "subject", "x")).post(LEADS + "/" + lead + "/activities").then().statusCode(403);
        String technician = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.TECHNICIAN));
        as(technician).get(LEADS).then().statusCode(403);
        as(technician).get(LEADS + "/" + lead + "/qualification").then().statusCode(403);

        // Another organization sees neither the lead nor the questions, and its own leads are not asked them.
        String outsider = tokenFor(accounts.activeUser(RoleCodes.SALES_MANAGER));
        as(outsider).get(LEADS).then().body("totalItems", equalTo(0));
        as(outsider).get(LEADS + "/" + lead).then().statusCode(404);
        as(outsider).get(LEADS + "/" + lead + "/qualification").then().statusCode(404);
        as(outsider).body(Map.of("answers", Map.of("budget", "YES"))).put(LEADS + "/" + lead + "/qualification/answers").then().statusCode(404);
        as(outsider).get(LEADS + "/" + lead + "/score-history").then().statusCode(404);
        as(outsider).get(QUESTIONS).then().body("$", empty());
        as(outsider).post(QUESTIONS + "/" + questionId + "/deactivate").then().statusCode(404);
        String theirLead = as(outsider).body(Map.of("contactName", "Theirs", "phone", "9000000003", "serviceId", serviceId))
                .post(LEADS).then().statusCode(201).extract().path("id");
        as(outsider).get(LEADS + "/" + theirLead + "/qualification").then().body("questions", hasSize(0));
    }

    // ------------------------------------------------------------------ helpers

    /** A new service in the Solar vertical; returns its id. */
    private String service() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String categoryId = as(admin).body(Map.of("vertical", "SOLAR", "code", "LQ_CAT_" + suffix, "name", "Lead category " + suffix))
                .post(CATALOG + "/categories").then().statusCode(201).extract().path("id");
        return as(admin).body(Map.of("categoryId", categoryId, "code", "LQ_SVC_" + suffix, "name", "Rooftop solar " + suffix,
                        "billingType", "QUOTE_BASED"))
                .post(CATALOG + "/services").then().statusCode(201).extract().path("id");
    }

    private static Map<String, Object> option(String value, String label, int points, boolean disqualifies) {
        return Map.of("value", value, "label", label, "points", points, "disqualifies", disqualifies);
    }

    @SafeVarargs
    private static Map<String, Object> questionBody(String key, String label, boolean required, Map<String, Object>... options) {
        return Map.of("key", key, "label", label, "required", required, "options", List.of(options));
    }

    /** Creates a question and returns its id. */
    @SafeVarargs
    private String question(String key, String label, boolean required, Map<String, Object>... options) {
        return as(manager).body(questionBody(key, label, required, options)).post(QUESTIONS)
                .then().statusCode(201).extract().path("id");
    }

    /** Creates a lead for the test's service and returns its id. */
    private String lead(Map<String, Object> fields) {
        Map<String, Object> body = new HashMap<>(fields);
        body.put("serviceId", serviceId);
        return as(manager).body(body).post(LEADS).then().statusCode(201).extract().path("id");
    }

    private Response update(String id, int version, Map<String, Object> fields) {
        Map<String, Object> body = new HashMap<>(fields);
        body.putIfAbsent("serviceId", serviceId);
        body.put("version", version);
        return as(manager).body(body).put(LEADS + "/" + id);
    }

    private Response why(String leadId) {
        return as(manager).get(LEADS + "/" + leadId + "/qualification");
    }

    private Response answer(String leadId, Map<String, String> answers) {
        Map<String, Object> body = new HashMap<>();
        body.put("answers", answers);
        return as(manager).body(body).put(LEADS + "/" + leadId + "/qualification/answers");
    }
}
