package com.pawanputra.bos.fieldops;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.InMemoryObjectStorage;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** Field jobs over real HTTP and PostgreSQL: scheduling, the technician's workflow and who may do what. */
class FieldJobIT extends AbstractIntegrationTest {

    private static final String JOBS = "/api/v1/field-jobs";
    private static final String SIGNATURE = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired InMemoryObjectStorage storage;
    @Autowired JdbcTemplate jdbc;

    Account dispatcherAccount;
    Account technicianAccount;
    String dispatcher;
    String technician;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        dispatcherAccount = accounts.activeUser(RoleCodes.PROJECT_MANAGER); // views and manages jobs
        dispatcher = tokenFor(dispatcherAccount);
        technicianAccount = accounts.activeColleagueOf(dispatcherAccount, RoleCodes.TECHNICIAN); // works jobs
        technician = tokenFor(technicianAccount);
    }

    // ------------------------------------------------------------------ the whole workflow

    @Test
    void aJobGoesFromAssignedToCustomerConfirmed() {
        Response created = as(dispatcher).body(job("Install 8 cameras", hours(2), Map.of(
                "instructions", "Use the conduit on the east wall.", "siteContactName", "Meera Joshi",
                "siteContactPhone", "9876543210", "technicianId", technicianAccount.id().toString(),
                "checklist", List.of("Mount cameras", " Configure the recorder ", "Show the customer the app"))))
                .post(JOBS);
        created.then().statusCode(201)
                .body("job.jobNumber", startsWith("JOB-"))
                .body("job.jobType", equalTo("JOB"))
                .body("job.status", equalTo("ASSIGNED"))
                .body("job.technicianName", equalTo("Test User"))
                .body("job.customerName", equalTo("Acme Solar"))
                .body("job.siteAddress", equalTo("12 MG Road, Lucknow"))
                .body("siteContactPhone", equalTo("9876543210"))
                .body("instructions", equalTo("Use the conduit on the east wall."))
                .body("checklist.label", contains("Mount cameras", "Configure the recorder", "Show the customer the app"))
                .body("checklist.done", contains(false, false, false))
                .body("nextStatus", equalTo("ACCEPTED"))
                .body("progress.assignedAt", notNullValue());
        String id = created.path("job.id");
        List<String> items = created.path("checklist.id");

        // The technician sees it, with the customer, site and instructions.
        as(technician).get(JOBS + "/" + id).then().statusCode(200)
                .body("canAct", equalTo(true)).body("job.title", equalTo("Install 8 cameras"));

        // Work cannot be recorded on a job that has not been accepted, and steps cannot be skipped.
        tick(id, items.get(0), true).then().statusCode(422).body("detail", containsString("Accept the job first"));
        advance(id, "EN_ROUTE").then().statusCode(422)
                .body("detail", equalTo("A job that is assigned can only move to accepted, not to en route"));

        advance(id, "ACCEPTED").then().statusCode(200).body("job.status", equalTo("ACCEPTED")).body("nextStatus", equalTo("EN_ROUTE"));
        advance(id, "EN_ROUTE").then().statusCode(200).body("progress.enRouteAt", notNullValue());
        advance(id, "ARRIVED").then().statusCode(200);
        advance(id, "IN_PROGRESS").then().statusCode(200).body("job.status", equalTo("IN_PROGRESS"));

        // On site: checklist, materials, photos, notes.
        tick(id, items.get(0), true).then().statusCode(200)
                .body("checklist[0].done", equalTo(true)).body("checklist[0].doneByName", equalTo("Test User"));
        as(technician).body(Map.of("name", "Dome camera", "quantity", 8, "unit", "pcs")).post(JOBS + "/" + id + "/materials")
                .then().statusCode(200).body("materials.name", contains("Dome camera")).body("materials[0].quantity", equalTo(8));
        as(technician).body(Map.of("name", "CAT6 cable", "quantity", 62.5, "unit", "m", "notes", "One box opened"))
                .post(JOBS + "/" + id + "/materials").then().statusCode(200).body("materials[1].quantity", equalTo(62.5f));
        as(technician).body(Map.of("body", "Customer asked for one camera to be moved to the gate."))
                .post(JOBS + "/" + id + "/notes").then().statusCode(200)
                .body("notes[0].body", startsWith("Customer asked")).body("notes[0].authorName", equalTo("Test User"));
        byte[] image = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        Response withPhoto = upload(technician, id, "IMG_0042.jpg", image, "image/jpeg", "After installation");
        withPhoto.then().statusCode(200)
                .body("photos.fileName", contains("IMG_0042.jpg")).body("photos[0].caption", equalTo("After installation"));
        String photoId = withPhoto.path("photos[0].id");
        byte[] served = as(technician).get(JOBS + "/" + id + "/photos/" + photoId + "/content").then().statusCode(200)
                .header("Content-Type", startsWith("image/jpeg")).extract().asByteArray();
        assertThat(served).isEqualTo(image);

        // The job cannot be completed with the checklist unfinished.
        advance(id, "COMPLETED").then().statusCode(422)
                .body("detail", startsWith("2 checklist items are still open"));
        tick(id, items.get(1), true);
        tick(id, items.get(2), true);
        as(technician).body(Map.of("to", "COMPLETED", "completionNotes", " All eight cameras live. "))
                .post(JOBS + "/" + id + "/advance").then().statusCode(200)
                .body("job.status", equalTo("COMPLETED")).body("completionNotes", equalTo("All eight cameras live."))
                .body("nextStatus", equalTo("CUSTOMER_CONFIRMED"));

        // The customer confirms on the technician's screen: a name is required, a signature optional.
        advance(id, "CUSTOMER_CONFIRMED").then().statusCode(400).body("errors[0].field", equalTo("signedByName"));
        as(technician).body(Map.of("to", "CUSTOMER_CONFIRMED", "signedByName", "Meera Joshi", "signature", "not-an-image"))
                .post(JOBS + "/" + id + "/advance").then().statusCode(400).body("errors[0].field", equalTo("signature"));
        as(technician).body(Map.of("to", "CUSTOMER_CONFIRMED", "signedByName", "Meera Joshi",
                        "remarks", "Neat work.", "signature", SIGNATURE))
                .post(JOBS + "/" + id + "/advance").then().statusCode(200)
                .body("job.status", equalTo("CUSTOMER_CONFIRMED"))
                .body("signOff.signedByName", equalTo("Meera Joshi"))
                .body("signOff.remarks", equalTo("Neat work."))
                .body("signOff.signature", equalTo(SIGNATURE))
                .body("signOff.confirmedAt", notNullValue())
                .body("nextStatus", nullValue());

        // A confirmed job is closed: nothing more can be recorded or changed.
        advance(id, "CUSTOMER_CONFIRMED").then().statusCode(422);
        tick(id, items.get(0), false).then().statusCode(422);
        as(technician).body(Map.of("body", "Late note")).post(JOBS + "/" + id + "/notes").then().statusCode(422);
        as(dispatcher).post(JOBS + "/" + id + "/cancel").then().statusCode(422);
        as(dispatcher).body(Map.of("technicianId", technicianAccount.id().toString())).post(JOBS + "/" + id + "/assign")
                .then().statusCode(422);
    }

    // ------------------------------------------------------------------ the technician's dashboard

    @Test
    void theTechniciansListsSortJobsByDay() {
        String today = create("Today's job", hours(1));
        String alsoToday = create("Second job today", hours(3));
        String tomorrow = create("Tomorrow's visit", Duration.ofDays(1));
        String nextWeek = create("Next week", Duration.ofDays(7));
        String overdue = create("Missed yesterday", Duration.ofDays(-1));
        String finished = create("Finished", hours(-2));
        String cancelled = create("Cancelled", hours(2));
        String someoneElses = createFor(accounts.activeColleagueOf(dispatcherAccount, RoleCodes.TECHNICIAN), "Not mine", hours(1));
        // This test is not about the checklist: tick it off directly.
        jdbc.update("UPDATE field_job_checklist_items SET done = true WHERE job_id = ?", UUID.fromString(finished));
        for (String step : List.of("ACCEPTED", "EN_ROUTE", "ARRIVED", "IN_PROGRESS", "COMPLETED")) {
            advance(finished, step).then().statusCode(200);
        }
        as(dispatcher).post(JOBS + "/" + cancelled + "/cancel").then().statusCode(200).body("job.status", equalTo("CANCELLED"));
        assertThat(List.of(today, alsoToday, tomorrow, nextWeek, overdue, someoneElses)).doesNotContainNull();

        as(technician).get(JOBS + "/my/summary").then().statusCode(200)
                .body("today", equalTo(2)).body("upcoming", equalTo(2)).body("delayed", equalTo(1)).body("completed", equalTo(1));
        as(technician).get(JOBS + "/my").then().statusCode(200) // TODAY is the default
                .body("title", contains("Today's job", "Second job today")).body("delayed", contains(false, false));
        as(technician).queryParam("bucket", "UPCOMING").get(JOBS + "/my").then()
                .body("title", contains("Tomorrow's visit", "Next week"));
        as(technician).queryParam("bucket", "DELAYED").get(JOBS + "/my").then()
                .body("title", contains("Missed yesterday")).body("delayed", contains(true));
        as(technician).queryParam("bucket", "COMPLETED").get(JOBS + "/my").then()
                .body("title", contains("Finished")).body("status", contains("COMPLETED"));
        as(technician).queryParam("bucket", "SOMEDAY").get(JOBS + "/my").then().statusCode(400);

        // A delayed job is still done like any other.
        advance(overdue, "ACCEPTED").then().statusCode(200).body("job.delayed", equalTo(true));
    }

    // ------------------------------------------------------------------ scheduling and assigning

    @Test
    void aDispatcherSchedulesAssignsReassignsAndCancels() {
        as(dispatcher).body(Map.of("title", " ", "customerName", "Acme", "siteAddress", "x", "scheduledStart", at(hours(1))))
                .post(JOBS).then().statusCode(400);
        as(dispatcher).body(job("Survey", hours(2), Map.of("scheduledEnd", at(hours(1))))).post(JOBS).then().statusCode(400)
                .body("errors[0].field", equalTo("scheduledEnd"));
        as(dispatcher).body(job("Survey", hours(2), Map.of("technicianId", UUID.randomUUID().toString()))).post(JOBS)
                .then().statusCode(400).body("errors[0].field", equalTo("technicianId"));

        // A site visit, created without a technician.
        Response created = as(dispatcher).body(job("Roof survey", hours(5), Map.of("jobType", "SITE_VISIT"))).post(JOBS);
        created.then().statusCode(201).body("job.status", equalTo("UNASSIGNED")).body("job.jobType", equalTo("SITE_VISIT"))
                .body("job.technicianId", nullValue()).body("nextStatus", nullValue()).body("checklist", empty());
        String id = created.path("job.id");
        as(technician).get(JOBS + "/" + id).then().statusCode(404); // not theirs yet

        as(dispatcher).body(Map.of("technicianId", technicianAccount.id().toString())).post(JOBS + "/" + id + "/assign")
                .then().statusCode(200).body("job.status", equalTo("ASSIGNED"));
        advance(id, "ACCEPTED").then().statusCode(200);

        // Given to someone else before anyone sets off: theirs to accept, and no longer the first technician's.
        Account other = accounts.activeColleagueOf(dispatcherAccount, RoleCodes.TECHNICIAN);
        as(dispatcher).body(Map.of("technicianId", other.id().toString())).post(JOBS + "/" + id + "/assign")
                .then().statusCode(200).body("job.status", equalTo("ASSIGNED")).body("job.technicianId", equalTo(other.id().toString()))
                .body("progress.acceptedAt", nullValue());
        as(technician).get(JOBS + "/" + id).then().statusCode(404);

        // Edited with its version; the checklist is replaced while nobody has set off.
        Map<String, Object> edit = job("Roof survey and measurements", hours(6), Map.of("version", 3,
                "checklist", List.of("Measure usable area", "Photograph the meter")));
        as(dispatcher).body(edit).put(JOBS + "/" + id).then().statusCode(200)
                .body("job.title", equalTo("Roof survey and measurements")).body("checklist.label", hasSize(2));
        as(dispatcher).body(edit).put(JOBS + "/" + id).then().statusCode(409);

        String otherToken = tokenFor(other);
        as(otherToken).body(Map.of("to", "ACCEPTED")).post(JOBS + "/" + id + "/advance").then().statusCode(200);
        as(otherToken).body(Map.of("to", "EN_ROUTE")).post(JOBS + "/" + id + "/advance").then().statusCode(200);
        // Once someone is on the way, the job stays with them and its checklist is fixed.
        as(dispatcher).body(Map.of("technicianId", technicianAccount.id().toString())).post(JOBS + "/" + id + "/assign")
                .then().statusCode(422).body("detail", containsString("en route"));
        edit.put("version", 6);
        as(dispatcher).body(edit).put(JOBS + "/" + id).then().statusCode(422);

        as(dispatcher).post(JOBS + "/" + id + "/cancel").then().statusCode(200).body("job.status", equalTo("CANCELLED"));
        as(otherToken).body(Map.of("to", "ARRIVED")).post(JOBS + "/" + id + "/advance").then().statusCode(422);

        as(dispatcher).queryParam("search", "roof").get(JOBS).then().statusCode(200)
                .body("items.title", contains("Roof survey and measurements")).body("items.status", contains("CANCELLED"));
        as(dispatcher).queryParam("status", "ASSIGNED").get(JOBS).then().body("items", empty());
        as(dispatcher).get(JOBS + "/technicians").then().statusCode(200).body("id", hasSize(3));
    }

    // ------------------------------------------------------------------ photos and materials

    @Test
    void photosAndMaterialsAreCheckedAndCanBeRemoved() {
        String id = create("Install", hours(1));
        advance(id, "ACCEPTED");
        byte[] image = "png-bytes".getBytes(StandardCharsets.UTF_8);

        upload(technician, id, "notes.pdf", image, "application/pdf", null).then().statusCode(400)
                .body("errors[0].message", equalTo("must be a JPEG, PNG or WebP image"));
        upload(technician, id, "empty.png", new byte[0], "image/png", null).then().statusCode(400);
        Response uploaded = upload(technician, id, "C:\\DCIM\\before.png", image, "image/png", null);
        uploaded.then().statusCode(200).body("photos[0].fileName", equalTo("before.png")).body("photos[0].caption", nullValue());
        String photoId = uploaded.path("photos[0].id");
        String key = jdbc.queryForObject("SELECT storage_key FROM field_job_photos WHERE id = ?", String.class, UUID.fromString(photoId));
        assertThat(key).startsWith("field-jobs/" + id + "/").endsWith(".png").doesNotContain("before");
        assertThat(storage.exists(key)).isTrue();

        as(technician).delete(JOBS + "/" + id + "/photos/" + photoId).then().statusCode(200).body("photos", empty());
        assertThat(storage.exists(key)).isFalse();
        as(technician).get(JOBS + "/" + id + "/photos/" + photoId + "/content").then().statusCode(404);

        as(technician).body(Map.of("name", "Cable", "quantity", 0)).post(JOBS + "/" + id + "/materials").then().statusCode(400);
        as(technician).body(Map.of("name", " ", "quantity", 1)).post(JOBS + "/" + id + "/materials").then().statusCode(400);
        String materialId = as(technician).body(Map.of("name", "Cable", "quantity", 10)).post(JOBS + "/" + id + "/materials")
                .then().statusCode(200).extract().path("materials[0].id");
        as(technician).delete(JOBS + "/" + id + "/materials/" + materialId).then().statusCode(200).body("materials", empty());
        as(technician).delete(JOBS + "/" + id + "/materials/" + materialId).then().statusCode(404);
    }

    // ------------------------------------------------------------------ access

    @Test
    void whoMaySeeAndDoWhat() {
        String id = create("Install", hours(1));
        String items = as(dispatcher).get(JOBS + "/" + id).then().extract().path("checklist[0].id");

        given().get(JOBS).then().statusCode(401);
        given().get(JOBS + "/my").then().statusCode(401);

        // A technician works their own jobs and sees no others, nor the full list, nor scheduling.
        as(technician).get(JOBS).then().statusCode(403);
        as(technician).get(JOBS + "/technicians").then().statusCode(403);
        as(technician).body(job("Mine", hours(1), Map.of())).post(JOBS).then().statusCode(403);
        as(technician).post(JOBS + "/" + id + "/cancel").then().statusCode(403);
        as(technician).body(Map.of("technicianId", technicianAccount.id().toString())).post(JOBS + "/" + id + "/assign").then().statusCode(403);
        String colleague = tokenFor(accounts.activeColleagueOf(dispatcherAccount, RoleCodes.TECHNICIAN));
        as(colleague).get(JOBS + "/" + id).then().statusCode(404);
        as(colleague).body(Map.of("to", "ACCEPTED")).post(JOBS + "/" + id + "/advance").then().statusCode(404);
        as(colleague).get(JOBS + "/my").then().statusCode(200).body("$", empty());

        // A director sees every job but neither schedules nor works them.
        String director = tokenFor(accounts.activeColleagueOf(dispatcherAccount, RoleCodes.DIRECTOR));
        as(director).get(JOBS).then().statusCode(200).body("totalItems", equalTo(1));
        as(director).get(JOBS + "/" + id).then().statusCode(200).body("canAct", equalTo(false));
        as(director).get(JOBS + "/my").then().statusCode(403);
        as(director).body(Map.of("to", "ACCEPTED")).post(JOBS + "/" + id + "/advance").then().statusCode(403);
        as(director).body(job("Theirs", hours(1), Map.of())).post(JOBS).then().statusCode(403);

        // A dispatcher may step in and act on a job (the technician's phone is dead).
        as(dispatcher).body(Map.of("to", "ACCEPTED")).post(JOBS + "/" + id + "/advance").then().statusCode(200);
        as(dispatcher).body(Map.of("done", true)).put(JOBS + "/" + id + "/checklist/" + items).then().statusCode(200);

        String salesExecutive = tokenFor(accounts.activeColleagueOf(dispatcherAccount, RoleCodes.SALES_EXECUTIVE));
        as(salesExecutive).get(JOBS + "/" + id).then().statusCode(403);

        // Another organization sees nothing of it.
        Account outsiderAccount = accounts.activeUser(RoleCodes.PROJECT_MANAGER);
        String outsider = tokenFor(outsiderAccount);
        as(outsider).get(JOBS).then().body("totalItems", equalTo(0));
        as(outsider).get(JOBS + "/" + id).then().statusCode(404);
        as(outsider).post(JOBS + "/" + id + "/cancel").then().statusCode(404);
        // ...and cannot give its jobs to our technician.
        as(outsider).body(job("Theirs", hours(1), Map.of("technicianId", technicianAccount.id().toString()))).post(JOBS)
                .then().statusCode(400).body("errors[0].field", equalTo("technicianId"));
    }

    // ------------------------------------------------------------------ helpers

    private static Duration hours(long hours) {
        return Duration.ofHours(hours);
    }

    /** A time today at noon plus the offset, so "today" does not depend on when the test runs. */
    private static String at(Duration fromNoon) {
        return java.time.LocalDate.now().atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().plus(fromNoon).toString();
    }

    private static Map<String, Object> job(String title, Duration fromNoon, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "customerName", "Acme Solar",
                "siteAddress", "12 MG Road, Lucknow", "scheduledStart", at(fromNoon)));
        body.putAll(extra);
        return body;
    }

    /** A job with one checklist item, assigned to the test's technician; returns its id. */
    private String create(String title, Duration fromNoon) {
        return createFor(technicianAccount, title, fromNoon);
    }

    private String createFor(Account assignee, String title, Duration fromNoon) {
        return as(dispatcher).body(job(title, fromNoon, Map.of("technicianId", assignee.id().toString(),
                        "checklist", List.of("Do the work"))))
                .post(JOBS).then().statusCode(201).extract().path("job.id");
    }

    private Response advance(String id, String to) {
        return as(technician).body(Map.of("to", to)).post(JOBS + "/" + id + "/advance");
    }

    private Response tick(String id, String itemId, boolean done) {
        return as(technician).body(Map.of("done", done)).put(JOBS + "/" + id + "/checklist/" + itemId);
    }

    private Response upload(String token, String id, String fileName, byte[] content, String contentType, String caption) {
        var request = given().header("Authorization", "Bearer " + token).multiPart("file", fileName, content, contentType);
        if (caption != null) {
            request = request.multiPart("caption", caption);
        }
        return request.post(JOBS + "/" + id + "/photos");
    }
}
