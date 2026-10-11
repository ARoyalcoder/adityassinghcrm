package com.pawanputra.bos.customer;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.TestAccounts;
import com.pawanputra.bos.support.TestAccounts.Account;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/** Customer management over real HTTP and PostgreSQL. Every test gets its own organization, so lists start empty. */
class CustomerIT extends AbstractIntegrationTest {

    private static final String CUSTOMERS = "/api/v1/customers";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;

    Account managerAccount;
    /** A sales manager: may view, create, update, delete and export customers. */
    String manager;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        managerAccount = accounts.activeUser(RoleCodes.SALES_MANAGER);
        manager = tokenFor(managerAccount);
    }

    // ------------------------------------------------------------------ access control

    @Test
    void customersRequireAuthenticationAndPermissions() {
        String customerId = createBusiness("Acme Solar", "9876543210", "info@acme.example").path("id");
        given().get(CUSTOMERS).then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));

        // No customer permission at all.
        String technician = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.TECHNICIAN));
        as(technician).get(CUSTOMERS).then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        as(technician).get(CUSTOMERS + "/" + customerId).then().statusCode(403);

        // View only.
        String support = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.SUPPORT_AGENT));
        as(support).get(CUSTOMERS).then().statusCode(200).body("totalItems", equalTo(1));
        as(support).get(CUSTOMERS + "/" + customerId + "/contacts").then().statusCode(200);
        as(support).get(CUSTOMERS + "/" + customerId + "/activity").then().statusCode(200);
        as(support).body(individual("Ravi", "Kumar", null, null)).post(CUSTOMERS).then().statusCode(403);
        as(support).body(Map.of("body", "hello")).post(CUSTOMERS + "/" + customerId + "/notes").then().statusCode(403);
        as(support).body(Map.of("phone", "9876543210")).post(CUSTOMERS + "/duplicate-check").then().statusCode(403);
        as(support).get(CUSTOMERS + "/export").then().statusCode(403);

        // Create and update, but not delete or export.
        String executive = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.SALES_EXECUTIVE));
        as(executive).body(individual("Ravi", "Kumar", null, null)).post(CUSTOMERS).then().statusCode(201);
        as(executive).delete(CUSTOMERS + "/" + customerId).then().statusCode(403);
        as(executive).get(CUSTOMERS + "/export").then().statusCode(403);
    }

    @Test
    void customersOfAnotherOrganizationAreInvisible() {
        String customerId = createBusiness("Acme Solar", "9876543210", "info@acme.example").path("id");
        String outsider = tokenFor(accounts.activeUser(RoleCodes.SALES_MANAGER));

        as(outsider).get(CUSTOMERS).then().body("totalItems", equalTo(0));
        as(outsider).get(CUSTOMERS + "/" + customerId).then().statusCode(404);
        as(outsider).get(CUSTOMERS + "/" + customerId + "/contacts").then().statusCode(404);
        as(outsider).body(Map.of("name", "Spy")).post(CUSTOMERS + "/" + customerId + "/contacts").then().statusCode(404);
        as(outsider).delete(CUSTOMERS + "/" + customerId).then().statusCode(404);
        // The same phone, email and company are not "duplicates" across organizations.
        as(outsider).body(business("Acme Solar", "9876543210", "info@acme.example")).post(CUSTOMERS).then().statusCode(201);
    }

    // ------------------------------------------------------------------ create, read, update, delete

    @Test
    void businessAndIndividualCustomersAreCreatedWithANumberAndADisplayName() {
        Map<String, Object> body = business("Acme Solar Pvt. Ltd.", "+91 98765 43210", "Info@Acme.Example");
        body.put("taxId", "09ABCDE1234F1Z5");
        body.put("source", "REFERRAL");
        body.put("status", "PROSPECT");
        body.put("assignedUserId", managerAccount.id().toString());
        body.put("tags", List.of("VIP", "Builder", "vip"));
        Response created = as(manager).body(body).post(CUSTOMERS);
        created.then().statusCode(201)
                .header("Location", endsWith("/customers/" + created.path("id")))
                .body("customerNumber", matchesPattern("CUS-\\d{6}"))
                .body("type", equalTo("BUSINESS"))
                .body("displayName", equalTo("Acme Solar Pvt. Ltd."))
                .body("email", equalTo("info@acme.example")) // stored lower-case
                .body("phone", equalTo("+91 98765 43210"))   // shown as typed
                .body("status", equalTo("PROSPECT"))
                .body("source", equalTo("REFERRAL"))
                .body("assignedTo.id", equalTo(managerAccount.id().toString()))
                .body("assignedTo.name", equalTo("Test User"))
                .body("tags", contains("Builder", "VIP")) // de-duplicated ignoring case
                .body("contactCount", equalTo(0))
                .body("addressCount", equalTo(0))
                .body("version", equalTo(0));

        as(manager).body(individual("Ravi", "Kumar", "9000000001", null)).post(CUSTOMERS)
                .then().statusCode(201)
                .body("type", equalTo("INDIVIDUAL"))
                .body("displayName", equalTo("Ravi Kumar"))
                .body("status", equalTo("ACTIVE")) // the default
                .body("assignedTo", nullValue());
        as(manager).body(individual("Madonna", null, null, null)).post(CUSTOMERS)
                .then().statusCode(201).body("displayName", equalTo("Madonna"));
    }

    @Test
    void customerRequestIsValidated() {
        as(manager).body(Map.of("firstName", "Ravi", "email", "not-an-email", "phone", "call me",
                        "tags", List.of("x".repeat(60), " ")))
                .post(CUSTOMERS)
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", hasItems("type", "email", "phone", "tags[0]", "tags[1]"));
        as(manager).body(Map.of("type", "MARTIAN", "firstName", "X")).post(CUSTOMERS)
                .then().statusCode(400).body("code", equalTo("MALFORMED_REQUEST"));

        // Which name is required depends on the type.
        as(manager).body(Map.of("type", "INDIVIDUAL", "companyName", "Acme")).post(CUSTOMERS)
                .then().statusCode(422).body("detail", containsString("first name"));
        as(manager).body(Map.of("type", "BUSINESS", "firstName", "Ravi")).post(CUSTOMERS)
                .then().statusCode(422).body("detail", containsString("company name"));
    }

    @Test
    void assignedEmployeeMustBeAnActiveUserOfTheSameOrganization() {
        Account outsider = accounts.activeUser(RoleCodes.SALES_EXECUTIVE);
        Account suspended = accounts.activeColleagueOf(managerAccount, RoleCodes.SALES_EXECUTIVE);
        String admin = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.ADMIN));
        as(admin).post("/api/v1/users/" + suspended.id() + "/deactivate").then().statusCode(200);

        Map<String, Object> body = individual("Ravi", "Kumar", null, null);
        body.put("assignedUserId", outsider.id().toString());
        as(manager).body(body).post(CUSTOMERS).then().statusCode(422).body("detail", containsString("does not exist"));
        body.put("assignedUserId", suspended.id().toString());
        as(manager).body(body).post(CUSTOMERS).then().statusCode(422).body("detail", containsString("not active"));

        // Only active colleagues are offered in the picker.
        as(manager).get(CUSTOMERS + "/assignees").then().statusCode(200)
                .body("id", hasItem(managerAccount.id().toString()))
                .body("id", org.hamcrest.Matchers.not(hasItem(suspended.id().toString())))
                .body("id", org.hamcrest.Matchers.not(hasItem(outsider.id().toString())));
    }

    @Test
    void customerCanBeEditedAndStaleEditsAreRejected() {
        String id = createBusiness("Acme Solar", "9876543210", null).path("id");

        Map<String, Object> update = business("Acme Solar Energy", "9876543210", "hello@acme.example");
        update.put("status", "INACTIVE");
        update.put("tags", List.of("Builder"));
        update.put("version", 0);
        as(manager).body(update).put(CUSTOMERS + "/" + id)
                .then().statusCode(200)
                .body("displayName", equalTo("Acme Solar Energy"))
                .body("status", equalTo("INACTIVE"))
                .body("tags", contains("Builder"))
                .body("customerNumber", notNullValue())
                .body("version", equalTo(1));

        as(manager).body(update).put(CUSTOMERS + "/" + id).then().statusCode(409).body("code", equalTo("CONFLICT"));
        update.remove("version");
        as(manager).body(update).put(CUSTOMERS + "/" + id)
                .then().statusCode(400).body("detail", containsString("version"));
    }

    @Test
    void deletedCustomerDisappearsButItsDetailsCanBeReused() {
        String id = createBusiness("Acme Solar", "9876543210", "info@acme.example").path("id");

        as(manager).delete(CUSTOMERS + "/" + id).then().statusCode(204);

        as(manager).get(CUSTOMERS + "/" + id).then().statusCode(404);
        as(manager).get(CUSTOMERS).then().body("totalItems", equalTo(0));
        // A deleted customer is not a duplicate of anything.
        createBusiness("Acme Solar", "9876543210", "info@acme.example").then().statusCode(201);
    }

    // ------------------------------------------------------------------ duplicate detection

    @Test
    void duplicateIsDetectedByPhoneHoweverItIsWritten() {
        String existing = createBusiness("Acme Solar", "+91 98765-43210", null).path("id");

        as(manager).body(individual("Ravi", "Kumar", "09876543210", null)).post(CUSTOMERS)
                .then().statusCode(409).body("code", equalTo("POSSIBLE_DUPLICATE"))
                .body("detail", containsString("Acme Solar"));
        as(manager).body(Map.of("phone", "98765 43210")).post(CUSTOMERS + "/duplicate-check")
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].id", equalTo(existing))
                .body("[0].displayName", equalTo("Acme Solar"))
                .body("[0].customerNumber", notNullValue())
                .body("[0].matchedOn", contains("PHONE"));

        // A different number is not a duplicate.
        as(manager).body(individual("Ravi", "Kumar", "9876543211", null)).post(CUSTOMERS).then().statusCode(201);
    }

    @Test
    void duplicateIsDetectedByEmailIgnoringCase() {
        createBusiness("Acme Solar", null, "info@acme.example");

        as(manager).body(individual("Ravi", "Kumar", null, "INFO@Acme.Example")).post(CUSTOMERS)
                .then().statusCode(409).body("code", equalTo("POSSIBLE_DUPLICATE"));
        as(manager).body(Map.of("email", " Info@ACME.example ")).post(CUSTOMERS + "/duplicate-check")
                .then().body("[0].matchedOn", contains("EMAIL"));
    }

    @Test
    void duplicateIsDetectedByCompanyNameIgnoringPunctuationAndLegalForm() {
        createBusiness("Acme Solar Pvt. Ltd.", null, null);

        for (String variant : List.of("ACME SOLAR", "acme-solar private limited", "Acme Solar LLP")) {
            as(manager).body(business(variant, null, null)).post(CUSTOMERS)
                    .then().statusCode(409).body("code", equalTo("POSSIBLE_DUPLICATE"));
        }
        as(manager).body(Map.of("companyName", "Acme Solar")).post(CUSTOMERS + "/duplicate-check")
                .then().body("[0].matchedOn", contains("COMPANY_NAME"));
        // A different company is fine.
        as(manager).body(business("Acme Interiors", null, null)).post(CUSTOMERS).then().statusCode(201);
    }

    @Test
    void duplicateCheckLooksAtContactsToo() {
        String id = createBusiness("Acme Solar", null, null).path("id");
        as(manager).body(Map.of("name", "Asha Verma", "phone", "9876543210", "email", "asha@acme.example"))
                .post(CUSTOMERS + "/" + id + "/contacts").then().statusCode(201);

        // Asha calls from her own number and is about to be entered as a new customer.
        as(manager).body(Map.of("phone", "+91 9876543210", "email", "ASHA@acme.example"))
                .post(CUSTOMERS + "/duplicate-check")
                .then().body("$", hasSize(1)).body("[0].id", equalTo(id))
                .body("[0].matchedOn", containsInAnyOrder("PHONE", "EMAIL"));
        as(manager).body(individual("Asha", "Verma", "9876543210", null)).post(CUSTOMERS).then().statusCode(409);
    }

    @Test
    void everyReasonAMatchWasFoundIsReported() {
        createBusiness("Acme Solar", "9876543210", "info@acme.example");
        createBusiness("Bright Interiors", "9000000002", "hello@bright.example");

        as(manager).body(Map.of("phone", "9876543210", "email", "hello@bright.example", "companyName", "ACME SOLAR LTD"))
                .post(CUSTOMERS + "/duplicate-check")
                .then().body("$", hasSize(2))
                .body("find { it.displayName == 'Acme Solar' }.matchedOn", containsInAnyOrder("PHONE", "COMPANY_NAME"))
                .body("find { it.displayName == 'Bright Interiors' }.matchedOn", contains("EMAIL"));
        // Nothing to compare means nothing found.
        as(manager).body(Map.of()).post(CUSTOMERS + "/duplicate-check").then().body("$", empty());
    }

    @Test
    void userCanConfirmAndCreateTheDuplicateAnyway() {
        createBusiness("Acme Solar", "9876543210", null);

        Map<String, Object> second = business("Acme Solar (Kanpur branch)", "9876543210", null);
        as(manager).body(second).post(CUSTOMERS).then().statusCode(409);
        second.put("confirmDuplicates", true);
        as(manager).body(second).post(CUSTOMERS).then().statusCode(201);

        as(manager).get(CUSTOMERS).then().body("totalItems", equalTo(2));
    }

    @Test
    void editingChecksForDuplicatesOnlyWhenIdentifyingDetailsChange() {
        createBusiness("Acme Solar", "9876543210", null);
        String id = createBusiness("Bright Interiors", "9000000002", null).path("id");

        // Changing something else: no duplicate check, and the customer does not match itself.
        Map<String, Object> harmless = business("Bright Interiors", "9000000002", null);
        harmless.put("status", "INACTIVE");
        harmless.put("version", 0);
        as(manager).body(harmless).put(CUSTOMERS + "/" + id).then().statusCode(200);
        as(manager).body(Map.of("phone", "9000000002", "excludeCustomerId", id)).post(CUSTOMERS + "/duplicate-check")
                .then().body("$", empty());

        // Changing the phone to another customer's number: stopped, unless confirmed.
        Map<String, Object> clash = business("Bright Interiors", "98765 43210", null);
        clash.put("version", 1);
        as(manager).body(clash).put(CUSTOMERS + "/" + id).then().statusCode(409).body("code", equalTo("POSSIBLE_DUPLICATE"));
        clash.put("confirmDuplicates", true);
        as(manager).body(clash).put(CUSTOMERS + "/" + id).then().statusCode(200).body("phone", equalTo("98765 43210"));
    }

    // ------------------------------------------------------------------ list

    @Test
    void listCanBeSearchedFilteredSortedAndPaged() {
        Account colleague = accounts.activeColleagueOf(managerAccount, RoleCodes.SALES_EXECUTIVE);
        create(with(business("Acme Solar", "9876543210", "info@acme.example"),
                "status", "ACTIVE", "source", "REFERRAL", "tags", List.of("VIP"),
                "assignedUserId", colleague.id().toString()));
        create(with(business("Bright Interiors", "9000000002", "hello@bright.example"),
                "status", "PROSPECT", "source", "WEBSITE", "tags", List.of("VIP", "Builder")));
        create(with(individual("Ravi", "Kumar", "9000000003", "ravi@example.com"),
                "status", "ACTIVE", "source", "WALK_IN"));
        create(with(individual("Zoya", "Khan", "9000000004", null), "status", "BLOCKED"));
        String number = as(manager).queryParam("search", "ravi").get(CUSTOMERS).path("items[0].customerNumber");

        // default order: by name
        as(manager).get(CUSTOMERS).then().statusCode(200).body("totalItems", equalTo(4))
                .body("items.displayName", contains("Acme Solar", "Bright Interiors", "Ravi Kumar", "Zoya Khan"))
                .body("items[0].assignedTo.name", equalTo("Test User"))
                .body("items[1].tags", contains("Builder", "VIP"));

        // search: name, customer number, email, phone in any format
        as(manager).queryParam("search", "bright").get(CUSTOMERS).then().body("items.displayName", contains("Bright Interiors"));
        as(manager).queryParam("search", number.toLowerCase()).get(CUSTOMERS).then().body("items.displayName", contains("Ravi Kumar"));
        as(manager).queryParam("search", "HELLO@BRIGHT").get(CUSTOMERS).then().body("totalItems", equalTo(1));
        as(manager).queryParam("search", "98765-43210").get(CUSTOMERS).then().body("items.displayName", contains("Acme Solar"));
        as(manager).queryParam("search", "nobody").get(CUSTOMERS).then().body("totalItems", equalTo(0));

        // filters
        as(manager).queryParam("type", "INDIVIDUAL").get(CUSTOMERS).then().body("items.displayName", contains("Ravi Kumar", "Zoya Khan"));
        as(manager).queryParam("status", "ACTIVE").get(CUSTOMERS).then().body("items.displayName", contains("Acme Solar", "Ravi Kumar"));
        as(manager).queryParam("source", "WEBSITE").get(CUSTOMERS).then().body("items.displayName", contains("Bright Interiors"));
        as(manager).queryParam("assignedTo", colleague.id().toString()).get(CUSTOMERS).then().body("items.displayName", contains("Acme Solar"));
        String vipTag = as(manager).get(CUSTOMERS + "/tags").then().body("name", contains("Builder", "VIP"))
                .extract().path("find { it.name == 'VIP' }.id");
        as(manager).queryParam("tagId", vipTag).get(CUSTOMERS).then().body("items.displayName", contains("Acme Solar", "Bright Interiors"));
        as(manager).queryParam("tagId", vipTag).queryParam("status", "PROSPECT").get(CUSTOMERS)
                .then().body("items.displayName", contains("Bright Interiors"));

        // sort and pagination
        as(manager).queryParam("sort", "name,desc").queryParam("size", 3).get(CUSTOMERS)
                .then().body("items.displayName", contains("Zoya Khan", "Ravi Kumar", "Bright Interiors"))
                .body("totalPages", equalTo(2));
        as(manager).queryParam("sort", "name,desc").queryParam("size", 3).queryParam("page", 1).get(CUSTOMERS)
                .then().body("items.displayName", contains("Acme Solar"));
        as(manager).queryParam("sort", "createdAt,desc").get(CUSTOMERS).then().body("items[0].displayName", equalTo("Zoya Khan"));
        as(manager).queryParam("sort", "phoneKey").get(CUSTOMERS).then().statusCode(400);
        as(manager).queryParam("status", "VIP").get(CUSTOMERS).then().statusCode(400);
    }

    // ------------------------------------------------------------------ contacts, addresses, notes

    @Test
    void contactsCanBeManagedAndExactlyOneIsPrimary() {
        String id = createBusiness("Acme Solar", null, null).path("id");
        String contacts = CUSTOMERS + "/" + id + "/contacts";

        String asha = as(manager).body(Map.of("name", "Asha Verma", "designation", "Owner", "phone", "9876543210",
                        "email", "Asha@Acme.Example"))
                .post(contacts).then().statusCode(201)
                .body("primaryContact", equalTo(true)) // the first contact is primary
                .body("email", equalTo("asha@acme.example"))
                .extract().path("id");
        String ravi = as(manager).body(Map.of("name", "Ravi Kumar")).post(contacts)
                .then().statusCode(201).body("primaryContact", equalTo(false)).extract().path("id");

        // Making Ravi primary takes the title from Asha.
        as(manager).body(Map.of("name", "Ravi Kumar", "designation", "Manager", "primaryContact", true, "version", 0))
                .put(contacts + "/" + ravi).then().statusCode(200).body("primaryContact", equalTo(true))
                .body("designation", equalTo("Manager")).body("version", equalTo(1));
        as(manager).get(contacts).then().body("name", contains("Ravi Kumar", "Asha Verma")) // primary first
                .body("primaryContact", contains(true, false));
        as(manager).get(CUSTOMERS + "/" + id).then().body("contactCount", equalTo(2));

        as(manager).body(Map.of("name", "Stale", "version", 0)).put(contacts + "/" + ravi).then().statusCode(409);
        as(manager).body(Map.of("name", "", "email", "nope", "phone", "x")).post(contacts)
                .then().statusCode(400).body("errors.field", containsInAnyOrder("name", "email", "phone"));

        as(manager).delete(contacts + "/" + asha).then().statusCode(204);
        as(manager).delete(contacts + "/" + asha).then().statusCode(404);
        as(manager).get(contacts).then().body("$", hasSize(1));
    }

    @Test
    void billingAndServiceAddressesEachHaveTheirOwnDefault() {
        String id = createBusiness("Acme Solar", null, null).path("id");
        String addresses = CUSTOMERS + "/" + id + "/addresses";

        String billing = addAddress(addresses, "BILLING", "Head office", "12 MG Road", "Lucknow", false)
                .then().statusCode(201).body("defaultAddress", equalTo(true)).body("countryCode", equalTo("IN"))
                .extract().path("id");
        String site1 = addAddress(addresses, "SERVICE", "Warehouse", "Plot 4, Industrial Area", "Kanpur", false)
                .then().statusCode(201).body("defaultAddress", equalTo(true)).extract().path("id");
        String site2 = addAddress(addresses, "SERVICE", "Farmhouse", "Village Road", "Unnao", true)
                .then().statusCode(201).body("defaultAddress", equalTo(true)).extract().path("id");

        // One default per type: the new service default replaced the old one, billing is untouched.
        Response all = as(manager).get(addresses);
        all.then().body("$", hasSize(3))
                .body("find { it.id == '" + billing + "' }.defaultAddress", equalTo(true))
                .body("find { it.id == '" + site1 + "' }.defaultAddress", equalTo(false))
                .body("find { it.id == '" + site2 + "' }.defaultAddress", equalTo(true));
        as(manager).get(CUSTOMERS + "/" + id).then().body("addressCount", equalTo(3));

        Map<String, Object> update = addressBody("SERVICE", "Main warehouse", "Plot 4, Industrial Area", "Kanpur", true);
        update.put("version", 0);
        // Losing the default was itself a change to this address, so version 0 is stale.
        as(manager).body(update).put(addresses + "/" + site1).then().statusCode(409);
        update.put("version", 1);
        as(manager).body(update).put(addresses + "/" + site1).then().statusCode(200)
                .body("label", equalTo("Main warehouse")).body("defaultAddress", equalTo(true));
        as(manager).get(addresses).then().body("find { it.id == '" + site2 + "' }.defaultAddress", equalTo(false));

        as(manager).body(Map.of("type", "BILLING")).post(addresses)
                .then().statusCode(400).body("errors.field", containsInAnyOrder("line1", "city", "state"));
        as(manager).body(addressBody("HOLIDAY", null, "x", "y", false)).post(addresses).then().statusCode(400);

        as(manager).delete(addresses + "/" + site2).then().statusCode(204);
        as(manager).get(addresses).then().body("$", hasSize(2));
    }

    @Test
    void notesRecordTheirAuthorAndAreListedNewestFirst() {
        String id = createBusiness("Acme Solar", null, null).path("id");
        String notes = CUSTOMERS + "/" + id + "/notes";

        as(manager).body(Map.of("body", "Prefers calls after 5 pm.")).post(notes).then().statusCode(201)
                .body("authorName", equalTo("Test User")).body("createdAt", notNullValue());
        String second = as(manager).body(Map.of("body", "  Asked for a rooftop quote.  ")).post(notes)
                .then().statusCode(201).body("body", equalTo("Asked for a rooftop quote.")).extract().path("id");

        as(manager).get(notes).then().statusCode(200).body("totalItems", equalTo(2))
                .body("items.body", contains("Asked for a rooftop quote.", "Prefers calls after 5 pm."));
        as(manager).body(Map.of("body", " ")).post(notes).then().statusCode(400);

        as(manager).delete(notes + "/" + second).then().statusCode(204);
        as(manager).get(notes).then().body("items.body", contains("Prefers calls after 5 pm."));
    }

    // ------------------------------------------------------------------ activity and audit

    @Test
    void customerTimelineIncludesChangesToItsContactsAddressesNotesAndTags() {
        String id = createBusiness("Acme Solar", "9876543210", null).path("id");
        Map<String, Object> update = business("Acme Solar", "9876543210", null);
        update.put("status", "INACTIVE");
        update.put("tags", List.of("VIP"));
        update.put("version", 0);
        as(manager).body(update).put(CUSTOMERS + "/" + id).then().statusCode(200);
        as(manager).body(Map.of("name", "Asha Verma")).post(CUSTOMERS + "/" + id + "/contacts").then().statusCode(201);
        addAddress(CUSTOMERS + "/" + id + "/addresses", "BILLING", null, "12 MG Road", "Lucknow", false);
        as(manager).body(Map.of("body", "First visit done.")).post(CUSTOMERS + "/" + id + "/notes").then().statusCode(201);

        // Visible with CUSTOMER_VIEW alone: no audit permission needed to see a customer's own history.
        String support = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.SUPPORT_AGENT));
        as(support).get(CUSTOMERS + "/" + id + "/activity").then().statusCode(200)
                .body("items.message", contains(
                        "Created Note",
                        "Created Address 'Lucknow'",
                        "Created Contact 'Asha Verma'",
                        "Updated Customer 'Acme Solar': tags",
                        "Changed status to INACTIVE for Customer 'Acme Solar'",
                        "Created Customer 'Acme Solar'"))
                .body("items[0].actorLabel", equalTo(managerAccount.email()))
                .body("items[3].changes[0].field", equalTo("tags"))
                .body("items[4].changes[0].from", equalTo("ACTIVE"))
                .body("items[4].changes[0].to", equalTo("INACTIVE"));

        // The audit log keeps each event against the thing that actually changed.
        String admin = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.ADMIN));
        as(admin).queryParam("module", "customers").get("/api/v1/audit/logs")
                .then().body("items.entityType", hasItems("Customer", "Contact", "Address", "Note", "Tag"));
    }

    // ------------------------------------------------------------------ export

    @Test
    void exportDownloadsTheFilteredListAsCsvAndIsAudited() {
        create(with(business("Acme Solar", "+91 98765 43210", "info@acme.example"), "tags", List.of("VIP"),
                "assignedUserId", managerAccount.id().toString()));
        create(with(business("=cmd|' /C calc'!A0", "9000000002", null), "status", "PROSPECT"));
        create(with(individual("Ravi", "Kumar", "9000000003", null), "status", "PROSPECT"));

        Response export = as(manager).queryParam("status", "PROSPECT").queryParam("sort", "name,desc")
                .get(CUSTOMERS + "/export");
        export.then().statusCode(200)
                .header("Content-Type", startsWith("text/csv"))
                .header("Content-Disposition", matchesPattern("attachment; filename=\"customers-\\d{4}-\\d{2}-\\d{2}\\.csv\""));
        String csv = new String(export.asByteArray(), StandardCharsets.UTF_8);
        String[] lines = csv.substring(1).split("\r\n"); // after the byte-order mark
        assertThat(csv).startsWith("﻿");
        assertThat(lines).hasSize(3); // header + the two prospects, not Acme
        assertThat(lines[0]).startsWith("Customer no.,Name,Type");
        assertThat(lines[1]).contains(",Ravi Kumar,INDIVIDUAL,");
        // A name that is a spreadsheet formula is neutralised.
        assertThat(lines[2]).contains(",'=cmd|' /C calc'!A0,BUSINESS,");

        String all = new String(as(manager).get(CUSTOMERS + "/export").asByteArray(), StandardCharsets.UTF_8);
        assertThat(all).contains("Acme Solar,BUSINESS,info@acme.example,'+91 98765 43210,ACTIVE,,Test User,VIP,");

        String admin = tokenFor(accounts.activeColleagueOf(managerAccount, RoleCodes.ADMIN));
        Response audit = as(admin).queryParam("action", "EXPORT").get("/api/v1/audit/logs");
        audit.then().body("totalItems", equalTo(2))
                .body("items.summary", contains("Exported 3 customers", "Exported 2 customers"))
                .body("items.actorLabel", contains(managerAccount.email(), managerAccount.email()));
        as(admin).get("/api/v1/audit/logs/" + audit.path("items[1].id")).then()
                .body("metadata.rowCount", equalTo(2))
                .body("metadata.format", equalTo("CSV"))
                .body("metadata.filters.status", equalTo("PROSPECT"));
    }

    // ------------------------------------------------------------------ helpers

    private Response create(Map<String, Object> body) {
        Response response = as(manager).body(body).post(CUSTOMERS);
        response.then().statusCode(201);
        return response;
    }

    private Response createBusiness(String companyName, String phone, String email) {
        return as(manager).body(business(companyName, phone, email)).post(CUSTOMERS);
    }

    private static Map<String, Object> business(String companyName, String phone, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "BUSINESS");
        body.put("companyName", companyName);
        body.put("phone", phone);
        body.put("email", email);
        return body;
    }

    private static Map<String, Object> individual(String firstName, String lastName, String phone, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "INDIVIDUAL");
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("phone", phone);
        body.put("email", email);
        return body;
    }

    private static Map<String, Object> with(Map<String, Object> body, Object... keysAndValues) {
        for (int i = 0; i < keysAndValues.length; i += 2) {
            body.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return body;
    }

    private Response addAddress(String url, String type, String label, String line1, String city, boolean makeDefault) {
        return as(manager).body(addressBody(type, label, line1, city, makeDefault)).post(url);
    }

    private static Map<String, Object> addressBody(String type, String label, String line1, String city, boolean makeDefault) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("label", label);
        body.put("line1", line1);
        body.put("city", city);
        body.put("state", "Uttar Pradesh");
        body.put("defaultAddress", makeDefault);
        return body;
    }
}
