package com.pawanputra.bos.project;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import java.time.LocalDate;
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

/**
 * Project analytics over real HTTP and PostgreSQL. Each test builds a small, known set of
 * projects and checks every figure against what a person would work out by hand.
 */
class ProjectAnalyticsIT extends AbstractIntegrationTest {

    private static final String ANALYTICS = "/api/v1/project-analytics";
    private static final String PROJECTS = "/api/v1/projects";
    private static final String CATALOG = "/api/v1/catalog";

    /** The pipeline from the brief. Here it is test data: an administrator's template, not code. */
    private static final List<String> PIPELINE = List.of(
            "Received", "Presentation", "Drafting/Modeling", "Finishing/Rendering", "Test Render/Draft", "Delivered",
            "Under Revision", "Revised & Closed", "On Hold", "Dead");

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;

    Account adminAccount;
    String admin;
    String service;
    LocalDate today;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // Each test gets its own organization, so its figures are its own.
        adminAccount = accounts.activeUser(RoleCodes.ADMIN);
        admin = tokenFor(adminAccount);
        service = service();
        today = LocalDate.now();
    }

    // ------------------------------------------------------------------ dashboard cards

    @Test
    void anOrganizationWithNoProjectsHasZeroCountsAndNoAverages() {
        as(admin).get(ANALYTICS).then().statusCode(200)
                .body("asOf", equalTo(today.toString()))
                .body("cards.activeProjects", equalTo(0))
                .body("cards.delayedProjects", equalTo(0))
                .body("cards.revenueInProgress", equalTo(0))
                // Nothing to average is "not known", never zero.
                .body("cards.averageProjectDurationDays", nullValue())
                .body("cards.averageDeliveryDelayDays", nullValue())
                .body("cards.clientSatisfaction", nullValue())
                .body("pipeline", empty())
                .body("delivery.deliveredOnTime", equalTo(0))
                .body("delivery.onTimePercentage", nullValue())
                .body("delivery.fastestDelivery", nullValue())
                .body("productivity.projectsCompleted", equalTo(0))
                .body("productivity.averageHours", nullValue())
                .body("productivity.employees", empty());
    }

    @Test
    void theCardsCountProjectsByWhereTheyStandToday() {
        project("Starts today", today, today.plusDays(30), 100000);
        project("Due today", today.minusDays(20), today, 250000);
        project("Running late", today.minusDays(40), today.minusDays(5), 400000);
        project("Also late", today.minusDays(60), today.minusDays(1), 50000);
        String onTime = project("Done on time", today.minusDays(30), today.minusDays(2), 300000);
        String late = project("Done late", today.minusDays(50), today.minusDays(10), 600000);
        String lastYear = project("Done long ago", today.minusDays(400), today.minusDays(380), 90000);
        String cancelled = project("Cancelled", today.minusDays(10), today.minusDays(3), 999999);
        String deleted = project("Deleted", today, today.plusDays(5), 777777);
        complete(onTime, today.minusDays(4)); // 26 days, 2 days early
        complete(late, today.minusDays(2)); // 48 days, 8 days late
        complete(lastYear, today.minusDays(370)); // 30 days, 10 days late
        setStatus(cancelled, "CANCELLED");
        jdbc.update("UPDATE projects SET deleted_at = now() WHERE id = ?", UUID.fromString(deleted));

        rate(onTime, 5, "Loved it");
        rate(late, 3, null);
        rate(late, 4, "Better after the revision"); // a second rating replaces the first

        boolean lastMonthBoundary = today.getDayOfMonth() <= 4; // completions a few days ago may fall in last month
        as(admin).get(ANALYTICS).then().statusCode(200)
                .body("cards.activeProjects", equalTo(4))
                .body("cards.startingToday", equalTo(1))
                .body("cards.dueToday", equalTo(1))
                .body("cards.delayedProjects", equalTo(2))
                .body("cards.revenueInProgress", equalTo(800000.0f)) // cancelled and deleted projects are not counted
                .body("cards.completedRevenue", equalTo(990000.0f))
                .body("cards.averageProjectDurationDays", equalTo(34.7f)) // (26 + 48 + 30) / 3
                .body("cards.averageDeliveryDelayDays", equalTo(9.0f)) // (8 + 10) / 2: only the late ones
                .body("cards.clientSatisfaction", equalTo(4.5f)) // (5 + 4) / 2
                .body("cards.ratedProjects", equalTo(2))
                .body("delivery.deliveredOnTime", equalTo(1))
                .body("delivery.deliveredLate", equalTo(2))
                .body("delivery.onTimePercentage", equalTo(33.3f))
                .body("delivery.averageDelayDays", equalTo(9.0f))
                .body("delivery.averageCompletionDays", equalTo(34.7f))
                .body("delivery.fastestDelivery.title", equalTo("Done on time"))
                .body("delivery.fastestDelivery.days", equalTo(26))
                .body("delivery.longestDelivery.title", equalTo("Done late"))
                .body("delivery.longestDelivery.days", equalTo(48))
                .body("productivity.projectsCompleted", equalTo(3));
        if (!lastMonthBoundary) {
            as(admin).get(ANALYTICS).then()
                    .body("cards.completedThisMonth", equalTo(2))
                    .body("cards.completedRevenueThisMonth", equalTo(900000.0f))
                    .body("productivity.projectsCompletedThisMonth", equalTo(2));
        }
    }

    // ------------------------------------------------------------------ pipeline

    @Test
    void thePipelineIsTheTemplatesStagesAndProjectsMoveThroughThem() {
        template(service, PIPELINE);
        String first = project("First", today, today.plusDays(30), 100000);
        String second = project("Second", today, today.plusDays(30), 200000);
        String third = project("Third", today, today.plusDays(30), 400000);
        String unplanned = project("Unplanned", today, today.plusDays(30), 50000);

        // A project has no stage until it is planned from its template; then it is in the first one.
        as(admin).get(PROJECTS + "/" + first + "/stage").then().statusCode(200)
                .body("stageKey", nullValue())
                .body("available.name", equalTo(PIPELINE));
        for (String id : List.of(first, second, third)) {
            as(admin).post(PROJECTS + "/" + id + "/apply-template").then().statusCode(200);
        }
        as(admin).get(PROJECTS + "/" + first + "/stage").then().body("stageName", equalTo("Received"));

        move(second, "presentation").then().statusCode(200).body("stageName", equalTo("Presentation"));
        move(third, "drafting_modeling").then().statusCode(200);
        move(third, "under_revision").then().statusCode(200).body("stageName", equalTo("Under Revision"));
        move(third, "under_revision").then().statusCode(200); // already there: not a move

        as(admin).get(ANALYTICS).then().statusCode(200)
                // In the template's order, whatever the stages are called; empty stages are not listed.
                .body("pipeline.name", contains("Received", "Presentation", "Under Revision"))
                .body("pipeline.projects", contains(1, 1, 1))
                .body("pipeline.revenue", contains(100000.0f, 200000.0f, 400000.0f))
                .body("unstagedProjects", equalTo(1));

        // The history says how long each stage really took.
        assertThat(jdbc.queryForList("SELECT stage_name || ' ' || (left_at IS NULL) FROM project_stage_history "
                + "WHERE project_id = ? ORDER BY entered_at, stage_name", String.class, UUID.fromString(third)))
                .containsExactly("Received false", "Drafting/Modeling false", "Under Revision true");

        // The projects to act on, with the stages each could move to.
        as(admin).get(ANALYTICS + "/pipeline-projects").then().statusCode(200)
                .body("title", contains("First", "Second", "Third", "Unplanned"))
                .body("stageName", contains("Received", "Presentation", "Under Revision", null))
                .body("[0].stages.name", equalTo(PIPELINE))
                .body("[0].delayed", equalTo(false));

        // Only the stages of the project's own template are accepted.
        move(first, "made_up_stage").then().statusCode(400).body("errors[0].field", equalTo("stageKey"));
        as(admin).body(Map.of()).put(PROJECTS + "/" + first + "/stage").then().statusCode(400);
        String otherService = service();
        String noTemplate = projectFor(otherService, "No template", today, today.plusDays(3), 1);
        move(noTemplate, "received").then().statusCode(422);
        assertThat(unplanned).isNotNull();
    }

    @Test
    void renamingAStageInTheTemplateRenamesItForProjectsThatEnterItAfterwards() {
        String templateId = template(service, List.of("Received", "Delivered"));
        String project = project("First", today, today.plusDays(10), 1000);
        as(admin).post(PROJECTS + "/" + project + "/apply-template").then().statusCode(200);

        // The administrator reworks the pipeline: no code changes, the analytics follow.
        as(admin).body(Map.of("name", "Pipeline", "version", 0, "stages", stages(List.of("Intake", "Drafting", "Delivered"))))
                .put("/api/v1/project-templates/" + templateId).then().statusCode(200);
        as(admin).get(PROJECTS + "/" + project + "/stage").then()
                .body("stageName", equalTo("Received")) // it entered under the old name
                .body("available.name", contains("Intake", "Drafting", "Delivered"));
        move(project, "drafting").then().statusCode(200);

        as(admin).get(ANALYTICS).then().body("pipeline.name", contains("Drafting")).body("pipeline.projects", contains(1));
    }

    // ------------------------------------------------------------------ productivity

    @Test
    void productivityIsWorkedOutFromTasksAndDeliverables() {
        Account designer = accounts.activeColleagueOf(adminAccount, RoleCodes.DESIGNER);
        Account technician = accounts.activeColleagueOf(adminAccount, RoleCodes.TECHNICIAN);
        String done = project("Done", today.minusDays(20), today.minusDays(5), 100000);
        String alsoDone = project("Also done", today.minusDays(20), today.minusDays(5), 100000);
        String running = project("Running", today.minusDays(3), today.plusDays(10), 100000);

        task(done, "Model", designer, "COMPLETED", 12.5);
        task(done, "Render", designer, "COMPLETED", 7.5);
        task(done, "Site check", technician, "COMPLETED", 4);
        task(alsoDone, "Model", designer, "COMPLETED", 10);
        task(running, "Model", designer, "IN_PROGRESS", 2);
        task(running, "Install", technician, "TODO", 0);
        task(running, "Unassigned", null, "TODO", 0);
        deliverable(done, "Floor plan");
        deliverable(done, "3D views");
        deliverable(done, "Working drawings");
        deliverable(alsoDone, "Floor plan");
        deliverable(running, "Draft");
        complete(done, today.minusDays(5));
        complete(alsoDone, today.minusDays(6));

        as(admin).get(ANALYTICS).then().statusCode(200)
                .body("productivity.projectsCompleted", equalTo(2))
                .body("productivity.averageHours", equalTo(17.0f)) // (24 + 10) / 2 completed projects
                .body("productivity.averageDeliverables", equalTo(2.0f)) // (3 + 1) / 2
                // Nothing records drawings separately, so this is not known rather than invented.
                .body("productivity.averageDrawings", nullValue())
                .body("productivity.employees.name", contains("Test User", "Test User"))
                .body("productivity.employees.userId", contains(designer.id().toString(), technician.id().toString()))
                .body("productivity.employees.tasksCompleted", contains(3, 1))
                .body("productivity.employees.openTasks", contains(1, 1))
                .body("productivity.employees.hoursLogged", contains(32.0f, 4.0f))
                .body("productivity.employees.projects", contains(3, 2));
    }

    // ------------------------------------------------------------------ satisfaction and access

    @Test
    void aRatingIsBetweenOneAndFive() {
        String project = project("Rated", today, today.plusDays(1), 1);

        as(admin).body(Map.of("rating", 0)).put(PROJECTS + "/" + project + "/satisfaction").then().statusCode(400);
        as(admin).body(Map.of("rating", 6)).put(PROJECTS + "/" + project + "/satisfaction").then().statusCode(400);
        as(admin).body(Map.of()).put(PROJECTS + "/" + project + "/satisfaction").then().statusCode(400);
        as(admin).body(Map.of("rating", 4, "comment", "  Good work ")).put(PROJECTS + "/" + project + "/satisfaction")
                .then().statusCode(200).body("rating", equalTo(4)).body("comment", equalTo("Good work"))
                .body("recordedBy", equalTo(adminAccount.email()));
        as(admin).body(Map.of("rating", 4)).put(PROJECTS + "/" + UUID.randomUUID() + "/satisfaction").then().statusCode(404);
    }

    @Test
    void analyticsFollowPermissionsAndOrganization() {
        template(service, List.of("Received", "Delivered"));
        String project = project("Ours", today, today.plusDays(5), 500000);
        as(admin).post(PROJECTS + "/" + project + "/apply-template").then().statusCode(200);

        given().get(ANALYTICS).then().statusCode(401);

        // A technician sees projects but does not move them or rate them.
        String technician = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.TECHNICIAN));
        as(technician).get(ANALYTICS).then().statusCode(200).body("cards.activeProjects", equalTo(1));
        as(technician).get(ANALYTICS + "/pipeline-projects").then().statusCode(200);
        as(technician).get(PROJECTS + "/" + project + "/stage").then().statusCode(200);
        as(technician).body(Map.of("stageKey", "delivered")).put(PROJECTS + "/" + project + "/stage").then().statusCode(403);
        as(technician).body(Map.of("rating", 5)).put(PROJECTS + "/" + project + "/satisfaction").then().statusCode(403);

        String salesExecutive = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.SALES_EXECUTIVE));
        as(salesExecutive).get(ANALYTICS).then().statusCode(403);

        // Another organization sees none of it.
        String outsider = tokenFor(accounts.activeUser(RoleCodes.ADMIN));
        as(outsider).get(ANALYTICS).then().statusCode(200)
                .body("cards.activeProjects", equalTo(0)).body("pipeline", empty());
        as(outsider).get(ANALYTICS + "/pipeline-projects").then().body("$", empty());
        as(outsider).get(PROJECTS + "/" + project + "/stage").then().statusCode(404);
        as(outsider).body(Map.of("stageKey", "delivered")).put(PROJECTS + "/" + project + "/stage").then().statusCode(404);
        as(outsider).body(Map.of("rating", 1)).put(PROJECTS + "/" + project + "/satisfaction").then().statusCode(404);
    }

    // ------------------------------------------------------------------ helpers

    private String service() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String categoryId = as(admin).body(Map.of("vertical", "ARCHITECTURE_TECH", "code", "PA_CAT_" + suffix, "name", "Analytics " + suffix))
                .post(CATALOG + "/categories").then().statusCode(201).extract().path("id");
        return as(admin).body(Map.of("categoryId", categoryId, "code", "PA_SVC_" + suffix, "name", "3D rendering " + suffix,
                        "billingType", "QUOTE_BASED"))
                .post(CATALOG + "/services").then().statusCode(201).extract().path("id");
    }

    private static String key(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Stages that do not wait for each other: a project can be moved to any of them. */
    private static List<Map<String, Object>> stages(List<String> names) {
        List<Map<String, Object>> stages = new ArrayList<>();
        for (String name : names) {
            stages.add(Map.of("key", key(name), "name", name, "durationDays", 1));
        }
        return stages;
    }

    private String template(String serviceId, List<String> stageNames) {
        return as(admin).body(Map.of("serviceId", serviceId, "name", "Pipeline", "stages", stages(stageNames)))
                .post("/api/v1/project-templates").then().statusCode(201).extract().path("id");
    }

    private String project(String title, LocalDate start, LocalDate expected, int revenue) {
        return projectFor(service, title, start, expected, revenue);
    }

    private String projectFor(String serviceId, String title, LocalDate start, LocalDate expected, int revenue) {
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "customerId", UUID.randomUUID().toString(),
                "customerName", "Acme", "serviceId", serviceId, "startDate", start.toString(),
                "expectedCompletionDate", expected.toString(), "revenue", revenue));
        return as(admin).body(body).post(PROJECTS).then().statusCode(201).extract().path("id");
    }

    private void setStatus(String projectId, String status) {
        as(admin).body(Map.of("status", status)).patch(PROJECTS + "/" + projectId + "/status").then().statusCode(200);
    }

    /** Completes the project through the API, then sets the day it was delivered (the API always uses today). */
    private void complete(String projectId, LocalDate deliveredOn) {
        setStatus(projectId, "COMPLETED");
        jdbc.update("UPDATE projects SET actual_completion_date = ? WHERE id = ?", deliveredOn, UUID.fromString(projectId));
    }

    private void rate(String projectId, int rating, String comment) {
        Map<String, Object> body = new HashMap<>(Map.of("rating", rating));
        if (comment != null) {
            body.put("comment", comment);
        }
        as(admin).body(body).put(PROJECTS + "/" + projectId + "/satisfaction").then().statusCode(200);
    }

    private io.restassured.response.Response move(String projectId, String stageKey) {
        return as(admin).body(Map.of("stageKey", stageKey)).put(PROJECTS + "/" + projectId + "/stage");
    }

    private void task(String projectId, String title, Account assignee, String status, double hours) {
        // The projects API refuses a task without a position; 0 means "put it last".
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "displayOrder", 0));
        if (assignee != null) {
            body.put("assignedToId", assignee.id().toString());
        }
        String id = as(admin).body(body).post(PROJECTS + "/" + projectId + "/tasks").then().log().ifValidationFails()
                .statusCode(org.hamcrest.Matchers.oneOf(200, 201)).extract().path("id");
        assertThat(id).as("task created").isNotNull();
        jdbc.update("UPDATE project_tasks SET status = ?, actual_hours = ? WHERE id = ?", status, hours, UUID.fromString(id));
    }

    private void deliverable(String projectId, String title) {
        String id = as(admin).body(Map.of("title", title)).post(PROJECTS + "/" + projectId + "/deliverables")
                .then().extract().path("id");
        assertThat(id).as("deliverable created").isNotNull();
    }
}
