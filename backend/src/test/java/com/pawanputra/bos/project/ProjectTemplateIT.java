package com.pawanputra.bos.project;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.project.api.ProjectTemplates;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import io.restassured.response.Response;
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
 * Project templates over real HTTP and PostgreSQL. Every template here is created through the API,
 * the way an administrator would: no stage exists in code or in a migration.
 */
class ProjectTemplateIT extends AbstractIntegrationTest {

    private static final String TEMPLATES = "/api/v1/project-templates";
    private static final String CATALOG = "/api/v1/catalog";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired ProjectTemplates projectTemplates;
    @Autowired JdbcTemplate jdbc;

    Account adminAccount;
    String admin;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // Each test gets its own organization, so its templates are its own.
        adminAccount = accounts.activeUser(RoleCodes.ADMIN);
        admin = tokenFor(adminAccount);
    }

    // ------------------------------------------------------------------ the examples from the brief

    @Test
    void theInteriorDesignTemplateIsConfiguredAndScheduled() {
        String service = service("INTERIOR_DESIGN", "Home interiors");
        List<Map<String, Object>> stages = chain(
                stage("lead", "Lead", 2, "SALES_EXECUTIVE"),
                stage("requirement", "Requirement", 5, "DESIGNER"),
                stage("concept", "Concept", 7, "DESIGNER"),
                stage("design_3d", "3D Design", 10, "DESIGNER"),
                stage("revision", "Revision", 5, "DESIGNER"),
                stage("working_drawings", "Working Drawings", 10, "DESIGNER"),
                stage("final_delivery", "Final Delivery", 3, "PROJECT_MANAGER"));
        stages.get(1).put("documents", List.of(
                Map.of("name", "Floor plan", "required", true, "description", "From the customer"),
                Map.of("name", "Reference photos", "required", false)));
        stages.get(3).put("milestones", List.of("3D design approved by the customer"));
        stages.get(3).put("tasks", List.of(
                Map.of("title", "Model the rooms", "estimatedHours", 24, "checklist", List.of("Living room", "Kitchen", "Bedrooms")),
                Map.of("title", "Render and present", "responsibleRole", "PROJECT_MANAGER", "description", "Walk the customer through it")));

        Response created = create(service, "Interior design project", stages);

        created.then().statusCode(201)
                .body("name", equalTo("Interior design project"))
                .body("service.id", equalTo(service)).body("service.verticalName", equalTo("Interior Design"))
                .body("active", equalTo(true)).body("revision", equalTo(1)).body("version", equalTo(0))
                .body("plan.stages.stage.name", contains(
                        "Lead", "Requirement", "Concept", "3D Design", "Revision", "Working Drawings", "Final Delivery"))
                .body("plan.stages.startDay", contains(0, 2, 7, 14, 24, 29, 39))
                .body("plan.stages.endDay", contains(2, 7, 14, 24, 29, 39, 42))
                .body("plan.totalDays", equalTo(42))
                .body("plan.stages[0].stage.dependsOn", empty())
                .body("plan.stages[3].stage.dependsOn", contains("concept"))
                .body("plan.stages[3].stage.responsibleRole", equalTo("DESIGNER"))
                .body("plan.stages[3].stage.milestones", contains("3D design approved by the customer"))
                .body("plan.stages[3].stage.tasks.title", contains("Model the rooms", "Render and present"))
                .body("plan.stages[3].stage.tasks[0].checklist", contains("Living room", "Kitchen", "Bedrooms"))
                .body("plan.stages[3].stage.tasks[0].estimatedHours", equalTo(24))
                .body("plan.stages[3].stage.tasks[1].responsibleRole", equalTo("PROJECT_MANAGER"))
                .body("plan.stages[1].stage.documents.name", contains("Floor plan", "Reference photos"))
                .body("plan.stages[1].stage.documents.required", contains(true, false));

        // Read back exactly as saved.
        as(admin).get(TEMPLATES + "/" + created.path("id")).then().statusCode(200)
                .body("plan.stages", hasSize(7)).body("plan.totalDays", equalTo(42));
    }

    @Test
    void solarAndCctvTemplatesAreOtherDataForOtherServices() {
        String solar = service("SOLAR", "Rooftop solar");
        String cctv = service("CCTV_SECURITY", "CCTV installation");

        create(solar, "Solar project", chain(
                stage("site_survey", "Site Survey", 2, "TECHNICIAN"), stage("design", "Design", 4, "DESIGNER"),
                stage("quotation", "Quotation", 2, "SALES_EXECUTIVE"), stage("procurement", "Procurement", 14, null),
                stage("installation", "Installation", 5, "TECHNICIAN"), stage("testing", "Testing", 1, "TECHNICIAN"),
                stage("handover", "Handover", 1, "PROJECT_MANAGER")))
                .then().statusCode(201).body("plan.totalDays", equalTo(29));
        create(cctv, "CCTV project", chain(
                stage("site_survey", "Site Survey", 1, "TECHNICIAN"), stage("design", "Design", 2, null),
                stage("procurement", "Procurement", 7, null), stage("installation", "Installation", 3, "TECHNICIAN"),
                stage("configuration", "Configuration", 1, "TECHNICIAN"), stage("testing", "Testing", 1, "TECHNICIAN"),
                stage("handover", "Handover", 1, "PROJECT_MANAGER")))
                .then().statusCode(201)
                .body("plan.stages.stage.name", contains(
                        "Site Survey", "Design", "Procurement", "Installation", "Configuration", "Testing", "Handover"))
                .body("plan.totalDays", equalTo(16));

        as(admin).get(TEMPLATES).then().statusCode(200)
                .body("name", contains("CCTV project", "Solar project"))
                .body("stageCount", contains(7, 7))
                .body("totalDays", contains(16, 29))
                .body("service.verticalName", contains("CCTV & Security", "Solar"));

        // What a project of each service will follow.
        assertThat(projectTemplates.activeForService(adminAccount.organizationId(), UUID.fromString(solar)))
                .hasValueSatisfying(template -> {
                    assertThat(template.name()).isEqualTo("Solar project");
                    assertThat(template.plan().stages()).extracting(s -> s.stage().key()).containsExactly(
                            "site_survey", "design", "quotation", "procurement", "installation", "testing", "handover");
                });
        assertThat(projectTemplates.activeForService(adminAccount.organizationId(), UUID.fromString(service("SOLAR", "Other"))))
                .isEmpty();
    }

    @Test
    void stagesMayRunSideBySide() {
        String service = service("SOLAR", "Solar with permit");
        List<Map<String, Object>> stages = new ArrayList<>(List.of(
                stage("design", "Design", 4, null),
                stage("procurement", "Procurement", 14, null, "design"),
                stage("permit", "Net-metering permit", 20, null, "design"),
                stage("installation", "Installation", 5, null, "procurement", "permit")));

        create(service, "Solar", stages).then().statusCode(201)
                .body("plan.stages.startDay", contains(0, 4, 4, 24))
                .body("plan.stages.endDay", contains(4, 18, 24, 29))
                .body("plan.totalDays", equalTo(29));
    }

    // ------------------------------------------------------------------ administering templates

    @Test
    void aTemplateIsValidatedAndEveryProblemIsPlaced() {
        String service = service("SOLAR", "Solar");

        create(service, "Solar", List.of()).then().statusCode(400)
                .body("errors.field", contains("stages")).body("errors.message", contains("Add at least one stage"));
        create(UUID.randomUUID().toString(), "Solar", chain(stage("survey", "Survey", 1, null))).then().statusCode(400)
                .body("errors.field", contains("serviceId"));
        create(service, " ", chain(stage("survey", "Survey", 1, null))).then().statusCode(400)
                .body("errors.field", contains("name"));

        // One value at a time...
        create(service, "Solar", List.of(stage("Site Survey", "Survey", 1, null))).then().statusCode(400)
                .body("errors.field", contains("stages[0].key"));
        create(service, "Solar", List.of(stage("survey", "Survey", -1, null))).then().statusCode(400)
                .body("errors.field", contains("stages[0].durationDays"));
        Map<String, Object> blankTask = stage("survey", "Survey", 1, null);
        blankTask.put("tasks", List.of(Map.of("title", " ")));
        create(service, "Solar", List.of(blankTask)).then().statusCode(400)
                .body("errors.field", contains("stages[0].tasks[0].title"));

        // ...and the stages as a set.
        create(service, "Solar", List.of(
                stage("survey", "Survey", 1, "PLUMBER"),
                stage("design", "Design", 2, null, "survey", "testing"),
                stage("installation", "Installation", 5, null, "design"),
                stage("testing", "Testing", 1, null, "installation"),
                stage("handover", "Handover", 1, null, "nowhere")))
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", containsInAnyOrder("stages[0].responsibleRole", "stages[4].dependsOn"))
                .body("errors.find { it.field == 'stages[4].dependsOn' }.message", equalTo("There is no stage with the key 'nowhere'"));
        create(service, "Solar", List.of(
                stage("survey", "Survey", 1, null),
                stage("design", "Design", 2, null, "survey", "testing"),
                stage("installation", "Installation", 5, null, "design"),
                stage("testing", "Testing", 1, null, "installation")))
                .then().statusCode(400)
                .body("errors.field", contains("stages[1].dependsOn", "stages[2].dependsOn", "stages[3].dependsOn"))
                .body("errors[0].message", equalTo("This stage depends, through other stages, on itself"));

        as(admin).get(TEMPLATES).then().body("$", empty()); // nothing was half-saved
    }

    @Test
    void aServiceHasOneTemplateWhichIsEditedAsAWhole() {
        String service = service("CCTV_SECURITY", "CCTV");
        String id = create(service, "CCTV project", chain(
                stage("site_survey", "Site Survey", 1, null), stage("installation", "Installation", 3, "TECHNICIAN")))
                .then().statusCode(201).extract().path("id");

        create(service, "Another", chain(stage("survey", "Survey", 1, null))).then().statusCode(409)
                .body("detail", containsString("already has a project template"));

        // Stages are inserted, reordered, renamed and dropped in one save.
        Map<String, Object> edit = new HashMap<>(Map.of("name", "CCTV installation project", "version", 0, "stages", chain(
                stage("site_survey", "Site Survey", 2, "TECHNICIAN"),
                stage("design", "Design", 2, "DESIGNER"),
                stage("fitting", "Fitting", 4, "TECHNICIAN"),
                stage("handover", "Handover", 1, null))));
        as(admin).body(edit).put(TEMPLATES + "/" + id).then().statusCode(200)
                .body("name", equalTo("CCTV installation project"))
                .body("plan.stages.stage.key", contains("site_survey", "design", "fitting", "handover"))
                .body("plan.totalDays", equalTo(9))
                .body("revision", equalTo(2)).body("version", equalTo(1))
                .body("service.id", equalTo(service));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_template_stages WHERE template_id = ?", Integer.class,
                UUID.fromString(id))).isEqualTo(4);

        as(admin).body(edit).put(TEMPLATES + "/" + id).then().statusCode(409); // stale version
        edit.put("version", 1);
        edit.put("stages", List.of(stage("a", "A", 1, null, "a")));
        as(admin).body(edit).put(TEMPLATES + "/" + id).then().statusCode(400);
        as(admin).get(TEMPLATES + "/" + id).then().body("plan.stages", hasSize(4)); // an invalid edit changes nothing

        // The change is in the audit trail.
        as(admin).queryParam("entityType", "ProjectTemplate").queryParam("entityId", id).get("/api/v1/audit/logs")
                .then().statusCode(200).body("items.action", hasItems("CREATE", "UPDATE"));
    }

    @Test
    void aTemplateThatIsSwitchedOffOrRemovedIsNoLongerOffered() {
        String service = service("SOLAR", "Solar");
        UUID serviceId = UUID.fromString(service);
        String id = create(service, "Solar project", chain(stage("survey", "Survey", 1, null)))
                .then().statusCode(201).extract().path("id");
        assertThat(projectTemplates.activeForService(adminAccount.organizationId(), serviceId)).isPresent();

        as(admin).post(TEMPLATES + "/" + id + "/deactivate").then().statusCode(200).body("active", equalTo(false));
        assertThat(projectTemplates.activeForService(adminAccount.organizationId(), serviceId)).isEmpty();
        as(admin).get(TEMPLATES).then().body("active", contains(false)); // still listed
        as(admin).post(TEMPLATES + "/" + id + "/activate").then().statusCode(200).body("active", equalTo(true));

        as(admin).delete(TEMPLATES + "/" + id).then().statusCode(204);
        as(admin).get(TEMPLATES + "/" + id).then().statusCode(404);
        as(admin).get(TEMPLATES).then().body("$", empty());
        assertThat(projectTemplates.activeForService(adminAccount.organizationId(), serviceId)).isEmpty();
        // The service can be given a new template.
        create(service, "Solar project v2", chain(stage("survey", "Survey", 1, null))).then().statusCode(201);
    }

    @Test
    void templatesFollowPermissionsAndOrganization() {
        String service = service("SOLAR", "Solar");
        String id = create(service, "Solar project", chain(stage("survey", "Survey", 1, null)))
                .then().statusCode(201).extract().path("id");
        Map<String, Object> body = Map.of("serviceId", service, "name", "X", "stages", chain(stage("a", "A", 1, null)));

        given().get(TEMPLATES).then().statusCode(401);

        // A project manager runs projects from templates but does not define them.
        String projectManager = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.PROJECT_MANAGER));
        as(projectManager).get(TEMPLATES).then().statusCode(200).body("name", contains("Solar project"));
        as(projectManager).get(TEMPLATES + "/" + id).then().statusCode(200);
        as(projectManager).get(TEMPLATES + "/options").then().statusCode(200)
                .body("roles", hasItems("DESIGNER", "PROJECT_MANAGER", "TECHNICIAN")).body("roles", hasSize(13));
        as(projectManager).body(body).post(TEMPLATES).then().statusCode(403);
        as(projectManager).body(Map.of("name", "X", "version", 0, "stages", chain(stage("a", "A", 1, null))))
                .put(TEMPLATES + "/" + id).then().statusCode(403);
        as(projectManager).post(TEMPLATES + "/" + id + "/deactivate").then().statusCode(403);
        as(projectManager).delete(TEMPLATES + "/" + id).then().statusCode(403);

        String salesExecutive = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.SALES_EXECUTIVE));
        as(salesExecutive).get(TEMPLATES).then().statusCode(403);

        // Another organization has its own templates, also for the same service.
        String outsider = tokenFor(accounts.activeUser(RoleCodes.ADMIN));
        as(outsider).get(TEMPLATES).then().body("$", empty());
        as(outsider).get(TEMPLATES + "/" + id).then().statusCode(404);
        as(outsider).delete(TEMPLATES + "/" + id).then().statusCode(404);
        as(outsider).body(body).post(TEMPLATES).then().statusCode(201);
        as(admin).get(TEMPLATES).then().body("name", contains("Solar project"));
    }

    // ------------------------------------------------------------------ planning a project from a template

    @Test
    void aProjectIsPlannedFromItsServicesTemplate() {
        String service = service("SOLAR", "Rooftop solar");
        List<Map<String, Object>> stages = new ArrayList<>(List.of(
                stage("site_survey", "Site Survey", 2, "TECHNICIAN"),
                stage("design", "Design", 4, "DESIGNER", "site_survey"),
                stage("procurement", "Procurement", 14, null, "design"),
                stage("installation", "Installation", 5, "TECHNICIAN", "procurement")));
        stages.get(0).put("tasks", List.of(
                Map.of("title", "Measure the roof", "estimatedHours", 3, "checklist", List.of("Usable area", "Shading", "Roof type")),
                Map.of("title", "Check the meter and wiring", "responsibleRole", "PROJECT_MANAGER")));
        stages.get(0).put("documents", List.of(Map.of("name", "Latest electricity bill", "required", true)));
        stages.get(1).put("milestones", List.of("Design approved by the customer"));
        stages.get(1).put("tasks", List.of(Map.of("title", "Draw the layout")));
        stages.get(3).put("tasks", List.of(Map.of("title", "Mount the panels", "checklist", List.of("Earthing"))));
        stages.get(3).put("documents", List.of(Map.of("name", "Commissioning report", "required", false)));
        String templateId = create(service, "Solar project", stages).then().statusCode(201).extract().path("id");

        String projectId = as(admin).body(Map.of("title", "Acme rooftop", "customerId", UUID.randomUUID().toString(),
                        "customerName", "Acme Solar", "serviceId", service, "startDate", "2026-11-02"))
                .post("/api/v1/projects").then().statusCode(201).extract().path("id");
        UUID project = UUID.fromString(projectId);

        as(admin).post("/api/v1/projects/" + projectId + "/apply-template").then().statusCode(200)
                .body("templateId", equalTo(templateId))
                .body("templateName", equalTo("Solar project"))
                .body("milestones", equalTo(5)) // four stages and one checkpoint
                .body("tasks", equalTo(4))
                .body("checklistItems", equalTo(4))
                .body("deliverables", equalTo(2))
                .body("expectedCompletionDate", equalTo("2026-11-27")); // 25 days after the start

        // The stages became the project's own milestones, dated from its start date.
        assertThat(jdbc.queryForList("SELECT title || ' ' || due_date FROM project_milestones WHERE project_id = ? "
                + "ORDER BY display_order", String.class, project)).containsExactly(
                "Site Survey 2026-11-04", "Design 2026-11-08", "Design approved by the customer 2026-11-08",
                "Procurement 2026-11-22", "Installation 2026-11-27");
        // Tasks sit under their stage, within its dates, and carry the responsible role.
        assertThat(jdbc.queryForList("SELECT t.title || ' | ' || m.title || ' | ' || t.start_date || '..' || t.due_date "
                + "FROM project_tasks t JOIN project_milestones m ON m.id = t.milestone_id WHERE t.project_id = ? "
                + "ORDER BY t.display_order", String.class, project)).containsExactly(
                "Measure the roof | Site Survey | 2026-11-02..2026-11-04",
                "Check the meter and wiring | Site Survey | 2026-11-02..2026-11-04",
                "Draw the layout | Design | 2026-11-04..2026-11-08",
                "Mount the panels | Installation | 2026-11-22..2026-11-27");
        assertThat(jdbc.queryForList("SELECT description FROM project_tasks WHERE project_id = ? ORDER BY display_order",
                String.class, project)).containsExactly(
                "Responsible role: TECHNICIAN", "Responsible role: PROJECT_MANAGER", "Responsible role: DESIGNER",
                "Responsible role: TECHNICIAN");
        assertThat(jdbc.queryForList("SELECT c.item_text FROM project_checklist_items c JOIN project_tasks t ON t.id = c.task_id "
                + "WHERE t.project_id = ? ORDER BY t.display_order, c.display_order", String.class, project))
                .containsExactly("Usable area", "Shading", "Roof type", "Earthing");
        // A stage's first task waits for the last task of the stage before it, across a stage with no tasks.
        assertThat(jdbc.queryForList("SELECT t.title || ' waits for ' || d.title FROM project_task_dependencies x "
                + "JOIN project_tasks t ON t.id = x.task_id JOIN project_tasks d ON d.id = x.depends_on_task_id "
                + "WHERE t.project_id = ? ORDER BY t.display_order", String.class, project)).containsExactly(
                "Draw the layout waits for Check the meter and wiring", "Mount the panels waits for Draw the layout");
        assertThat(jdbc.queryForList("SELECT title || ' | ' || acceptance_criteria || ' | ' || due_date FROM project_deliverables "
                + "WHERE project_id = ? ORDER BY due_date", String.class, project)).containsExactly(
                "Latest electricity bill | Required document | 2026-11-04",
                "Commissioning report | Optional document | 2026-11-27");

        // Applying again would duplicate the plan.
        as(admin).post("/api/v1/projects/" + projectId + "/apply-template").then().statusCode(409);

        // The project has its own copy: changing the template afterwards does not touch it.
        as(admin).body(Map.of("name", "Solar project", "version", 0, "stages", chain(stage("only", "Only stage", 1, null))))
                .put(TEMPLATES + "/" + templateId).then().statusCode(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_milestones WHERE project_id = ?", Integer.class, project))
                .isEqualTo(5);
    }

    @Test
    void aTemplateCanOnlyBeAppliedWhereThereIsOne() {
        String withTemplate = service("SOLAR", "Solar");
        String withoutTemplate = service("IT_SUPPORT", "Annual maintenance");
        String templateId = create(withTemplate, "Solar project", chain(stage("survey", "Survey", 1, null)))
                .then().statusCode(201).extract().path("id");

        String noService = project(null);
        as(admin).post("/api/v1/projects/" + noService + "/apply-template").then().statusCode(422)
                .body("detail", containsString("not for a service"));
        String noTemplate = project(withoutTemplate);
        as(admin).post("/api/v1/projects/" + noTemplate + "/apply-template").then().statusCode(422)
                .body("detail", containsString("no active project template"));

        as(admin).post(TEMPLATES + "/" + templateId + "/deactivate").then().statusCode(200);
        String project = project(withTemplate);
        as(admin).post("/api/v1/projects/" + project + "/apply-template").then().statusCode(422);
        as(admin).post(TEMPLATES + "/" + templateId + "/activate").then().statusCode(200);

        as(admin).post("/api/v1/projects/" + UUID.randomUUID() + "/apply-template").then().statusCode(404);
        String outsider = tokenFor(accounts.activeUser(RoleCodes.ADMIN));
        as(outsider).post("/api/v1/projects/" + project + "/apply-template").then().statusCode(404);
        String technician = tokenFor(accounts.activeColleagueOf(adminAccount, RoleCodes.TECHNICIAN));
        as(technician).post("/api/v1/projects/" + project + "/apply-template").then().statusCode(403);

        as(admin).post("/api/v1/projects/" + project + "/apply-template").then().statusCode(200)
                .body("milestones", equalTo(1)).body("tasks", equalTo(0));
        assertThat(jdbc.queryForObject("SELECT description FROM project_milestones WHERE project_id = ?", String.class,
                UUID.fromString(project))).isNull();
    }

    @Test
    void theApiAnswersEmptyListsRatherThanNothing() {
        String service = service("SOLAR", "Solar");

        create(service, "Solar project", chain(stage("survey", "Survey", 1, null))).then().statusCode(201)
                .body("plan.stages[0].stage.milestones", empty())
                .body("plan.stages[0].stage.tasks", empty())
                .body("plan.stages[0].stage.documents", empty())
                .body("plan.stages[0].stage.responsibleRole", nullValue())
                .body("description", nullValue());
    }

    // ------------------------------------------------------------------ helpers

    /** A new service in the given vertical; returns its id. */
    private String service(String vertical, String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String categoryId = as(admin).body(Map.of("vertical", vertical, "code", "PT_CAT_" + suffix, "name", name + " category " + suffix))
                .post(CATALOG + "/categories").then().statusCode(201).extract().path("id");
        return as(admin).body(Map.of("categoryId", categoryId, "code", "PT_SVC_" + suffix, "name", name + " " + suffix,
                        "billingType", "QUOTE_BASED"))
                .post(CATALOG + "/services").then().statusCode(201).extract().path("id");
    }

    private String project(String serviceId) {
        Map<String, Object> body = new HashMap<>(Map.of("title", "Test project", "customerId", UUID.randomUUID().toString(),
                "customerName", "Acme", "startDate", "2026-11-02"));
        if (serviceId != null) {
            body.put("serviceId", serviceId);
        }
        return as(admin).body(body).post("/api/v1/projects").then().statusCode(201).extract().path("id");
    }

    private static Map<String, Object> stage(String key, String name, int days, String role, String... dependsOn) {
        Map<String, Object> stage = new HashMap<>(Map.of("key", key, "name", name, "durationDays", days,
                "dependsOn", List.of(dependsOn)));
        if (role != null) {
            stage.put("responsibleRole", role);
        }
        return stage;
    }

    /** Makes each stage wait for the one before it: "A → B → C". */
    @SafeVarargs
    private static List<Map<String, Object>> chain(Map<String, Object>... stages) {
        List<Map<String, Object>> chained = new ArrayList<>();
        for (int i = 0; i < stages.length; i++) {
            if (i > 0) {
                stages[i].put("dependsOn", List.of(stages[i - 1].get("key")));
            }
            chained.add(stages[i]);
        }
        return chained;
    }

    private Response create(String serviceId, String name, List<Map<String, Object>> stages) {
        return as(admin).body(Map.of("serviceId", serviceId, "name", name, "stages", stages)).post(TEMPLATES);
    }
}
