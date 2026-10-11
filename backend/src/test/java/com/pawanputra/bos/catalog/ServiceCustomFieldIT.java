package com.pawanputra.bos.catalog;

import static com.pawanputra.bos.support.ApiClient.as;
import static com.pawanputra.bos.support.ApiClient.tokenFor;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import com.pawanputra.bos.catalog.api.CustomFieldType;
import com.pawanputra.bos.catalog.api.LeadCustomFields;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FieldValue;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FileReference;
import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.support.AbstractIntegrationTest;
import com.pawanputra.bos.support.InMemoryObjectStorage;
import com.pawanputra.bos.support.TestAccounts;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
 * Configurable lead fields over real HTTP and PostgreSQL. Every field in these tests is created
 * through the API, the way an administrator would: none exists in code or in a migration.
 */
class ServiceCustomFieldIT extends AbstractIntegrationTest {

    private static final String CATALOG = "/api/v1/catalog";

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired LeadCustomFields leadFields;
    @Autowired InMemoryObjectStorage storage;
    @Autowired JdbcTemplate jdbc;

    String admin;
    String tag;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        admin = tokenFor(accounts.activeUser(RoleCodes.ADMIN));
        tag = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    // ------------------------------------------------------------------ the four example services

    @Test
    void solarFieldsAreConfiguredAndTheirAnswersValidated() {
        String solar = service("SOLAR", "Rooftop solar");
        field(solar, "monthly_electricity_bill", "CURRENCY", "Monthly electricity bill", Map.of("required", true));
        field(solar, "roof_area", "NUMBER", "Roof area", Map.of("unit", "sq ft", "minValue", 100, "required", true));
        field(solar, "required_capacity", "NUMBER", "Required capacity", Map.of("unit", "kW", "minValue", 1, "maxValue", 1000));
        field(solar, "site_availability", "BOOLEAN", "Site available for survey", Map.of());

        as(admin).get(fieldsOf(solar)).then().statusCode(200)
                .body("field.key", contains("monthly_electricity_bill", "roof_area", "required_capacity", "site_availability"))
                .body("field.type", contains("CURRENCY", "NUMBER", "NUMBER", "BOOLEAN"))
                .body("field.required", contains(true, true, false, false))
                .body("field[1].unit", equalTo("sq ft"))
                .body("field[1].minValue", equalTo(100))
                .body("field[2].maxValue", equalTo(1000))
                .body("field.displayOrder", contains(10, 20, 30, 40));

        validate(solar, Map.of("monthly_electricity_bill", 8500.50, "roof_area", "1200", "required_capacity", 10,
                "site_availability", true))
                .then().statusCode(200)
                .body("values.monthly_electricity_bill", equalTo(8500.5f))
                .body("values.roof_area", equalTo(1200))
                .body("values.site_availability", equalTo(true));

        validate(solar, Map.of("roof_area", 50, "required_capacity", 5000, "site_availability", "yes"))
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", containsInAnyOrder(
                        "monthly_electricity_bill", "roof_area", "required_capacity", "site_availability"))
                .body("errors.find { it.field == 'monthly_electricity_bill' }.message", equalTo("is required"))
                .body("errors.find { it.field == 'roof_area' }.message", equalTo("must be at least 100"))
                .body("errors.find { it.field == 'required_capacity' }.message", equalTo("must be at most 1000"))
                .body("errors.find { it.field == 'site_availability' }.message", equalTo("must be yes or no"));
    }

    @Test
    void cctvFieldsAreConfiguredAndTheirAnswersValidated() {
        String cctv = service("CCTV_SECURITY", "CCTV installation");
        field(cctv, "camera_count", "NUMBER", "Camera count", Map.of("required", true, "minValue", 1));
        field(cctv, "site_type", "SELECT", "Site type", Map.of("options", options("Home", "Office", "Shop", "Warehouse")));
        field(cctv, "placement", "SELECT", "Indoor / outdoor", Map.of("options", options("Indoor", "Outdoor", "Both")));
        field(cctv, "storage_requirement", "NUMBER", "Storage requirement", Map.of("unit", "days"));

        as(admin).get(fieldsOf(cctv)).then()
                .body("field[1].options.value", contains("HOME", "OFFICE", "SHOP", "WAREHOUSE"))
                .body("field[1].options.label", contains("Home", "Office", "Shop", "Warehouse"))
                .body("field[0].options", nullValue());

        validate(cctv, Map.of("camera_count", 8, "site_type", "SHOP", "placement", "BOTH", "storage_requirement", 30))
                .then().statusCode(200).body("values.site_type", equalTo("SHOP"));
        validate(cctv, Map.of("camera_count", 0, "site_type", "Castle")).then().statusCode(400)
                .body("errors.field", containsInAnyOrder("camera_count", "site_type"));
    }

    @Test
    void interiorFieldsAreConfiguredAndTheirAnswersValidated() {
        String interior = service("INTERIOR_DESIGN", "Home interiors");
        field(interior, "property_type", "SELECT", "Property type", Map.of("options", options("Apartment", "Villa", "Office")));
        field(interior, "area", "NUMBER", "Area", Map.of("unit", "sq ft"));
        field(interior, "design_requirement", "TEXTAREA", "Design requirement", Map.of("maxLength", 500));
        field(interior, "rooms", "MULTI_SELECT", "Rooms", Map.of("options", options("Living room", "Kitchen", "Bedroom")));
        field(interior, "floor_plan", "FILE", "Floor plan", Map.of("allowedFileTypes", List.of("PDF", "jpg"), "maxFileSizeMb", 5));
        field(interior, "possession_date", "DATE", "Possession date", Map.of());

        as(admin).get(fieldsOf(interior)).then()
                .body("field[4].allowedFileTypes", contains("pdf", "jpg"))
                .body("field[3].options.value", contains("LIVING_ROOM", "KITCHEN", "BEDROOM"));

        validate(interior, Map.of("property_type", "VILLA", "rooms", List.of("KITCHEN", "BEDROOM"),
                "design_requirement", "  Modern, lots of light. ", "possession_date", "2027-01-15"))
                .then().statusCode(200)
                .body("values.rooms", contains("KITCHEN", "BEDROOM"))
                .body("values.design_requirement", equalTo("Modern, lots of light."))
                .body("values.possession_date", equalTo("2027-01-15"));
        validate(interior, Map.of("rooms", List.of("KITCHEN", "GARAGE"), "design_requirement", "x".repeat(501),
                "possession_date", "15/01/2027"))
                .then().statusCode(400)
                .body("errors.field", containsInAnyOrder("rooms", "design_requirement", "possession_date"))
                .body("errors.find { it.field == 'design_requirement' }.message", equalTo("must be at most 500 characters"));
    }

    @Test
    void digitalMarketingFieldsAreConfiguredAndTheirAnswersValidated() {
        String marketing = service("DIGITAL_MARKETING", "Social media management");
        field(marketing, "business_type", "TEXT", "Business type", Map.of("required", true));
        field(marketing, "website", "TEXT", "Website", Map.of("maxLength", 200, "helpText", "Leave empty if there is none"));
        field(marketing, "current_marketing_channels", "MULTI_SELECT", "Current marketing channels",
                Map.of("options", options("Facebook", "Instagram", "Google Ads", "None")));
        field(marketing, "monthly_budget", "CURRENCY", "Monthly budget", Map.of("minValue", 5000));

        as(admin).get(fieldsOf(marketing)).then()
                .body("field[1].helpText", equalTo("Leave empty if there is none"))
                .body("field[2].options.value", contains("FACEBOOK", "INSTAGRAM", "GOOGLE_ADS", "NONE"));

        validate(marketing, Map.of("business_type", "Restaurant", "website", "",
                "current_marketing_channels", List.of("INSTAGRAM"), "monthly_budget", 25000))
                .then().statusCode(200)
                .body("values", not(hasKey("website"))) // left empty: not an answer
                .body("values.monthly_budget", equalTo(25000));
        validate(marketing, Map.of("monthly_budget", 4999.999, "roof_area", 1200)).then().statusCode(400)
                .body("errors.field", containsInAnyOrder("business_type", "monthly_budget", "roof_area"))
                .body("errors.find { it.field == 'roof_area' }.message", equalTo("is not a field of this service"));
    }

    // ------------------------------------------------------------------ administering fields

    @Test
    void aFieldDefinitionIsValidated() {
        String service = service("IT_SUPPORT", "Annual maintenance");

        createField(service, fieldBody("Device Count", "NUMBER", "Devices", Map.of())).then().statusCode(400)
                .body("errors.field", contains("key"));
        createField(service, fieldBody("devices", "HOLOGRAM", "Devices", Map.of())).then().statusCode(400);
        createField(service, fieldBody("devices", "NUMBER", " ", Map.of())).then().statusCode(400)
                .body("errors.field", contains("label"));
        createField(service, fieldBody("os", "SELECT", "Operating system", Map.of())).then().statusCode(400)
                .body("errors.field", contains("options")).body("errors.message", contains("Add at least one option"));
        createField(service, fieldBody("devices", "NUMBER", "Devices", Map.of("minValue", 10, "maxValue", 5)))
                .then().statusCode(400).body("errors.field", contains("maxValue"));
        createField(service, fieldBody("devices", "TEXT", "Devices", Map.of("unit", "pcs", "options", options("A"))))
                .then().statusCode(400).body("errors.field", containsInAnyOrder("unit", "options"));
        createField(service, fieldBody("contract", "FILE", "Contract", Map.of("allowedFileTypes", List.of("p d f"))))
                .then().statusCode(400);

        createField(UUID.randomUUID().toString(), fieldBody("devices", "NUMBER", "Devices", Map.of()))
                .then().statusCode(404);
        as(admin).get(fieldsOf(service)).then().statusCode(200).body("$", empty());
    }

    @Test
    void aKeyIsUniqueWithinItsServiceOnly() {
        String first = service("SOLAR", "Solar A");
        String second = service("SOLAR", "Solar B");
        field(first, "roof_area", "NUMBER", "Roof area", Map.of());

        createField(first, fieldBody("roof_area", "TEXT", "Roof area again", Map.of())).then().statusCode(409)
                .body("detail", containsString("roof_area"));
        createField(second, fieldBody("roof_area", "NUMBER", "Roof area", Map.of())).then().statusCode(201);
    }

    @Test
    void aFieldIsEditedWithItsVersionButItsKeyAndTypeNeverChange() {
        String service = service("SOLAR", "Solar");
        String id = field(service, "roof_area", "NUMBER", "Roof area", Map.of("unit", "sq ft"));
        Map<String, Object> edit = new HashMap<>(Map.of("label", "Usable roof area", "required", true, "unit", "sq m",
                "maxValue", 5000, "displayOrder", 5, "version", 0,
                // sent by a client that tries to change them; they are not part of an edit
                "key", "something_else", "type", "TEXT"));

        as(admin).body(edit).put(fieldsOf(service) + "/" + id).then().statusCode(200)
                .body("field.label", equalTo("Usable roof area"))
                .body("field.key", equalTo("roof_area"))
                .body("field.type", equalTo("NUMBER"))
                .body("field.required", equalTo(true))
                .body("field.unit", equalTo("sq m"))
                .body("field.displayOrder", equalTo(5))
                .body("version", equalTo(1));

        as(admin).body(edit).put(fieldsOf(service) + "/" + id).then().statusCode(409); // stale version
        edit.put("version", 1);
        edit.put("maxLength", 10);
        as(admin).body(edit).put(fieldsOf(service) + "/" + id).then().statusCode(400)
                .body("errors.field", contains("maxLength"));
        edit.remove("version");
        as(admin).body(edit).put(fieldsOf(service) + "/" + id).then().statusCode(400);

        // A field is addressed through its own service only.
        String other = service("SOLAR", "Other solar");
        as(admin).get(fieldsOf(other) + "/" + id).then().statusCode(404);
    }

    @Test
    void anInactiveFieldIsNeitherAskedNorValidatedAndARemovedOnesKeyIsFreeAgain() {
        String service = service("CCTV_SECURITY", "CCTV");
        String cameras = field(service, "camera_count", "NUMBER", "Camera count", Map.of("required", true));
        field(service, "site_type", "TEXT", "Site type", Map.of());

        as(admin).post(fieldsOf(service) + "/" + cameras + "/deactivate").then().statusCode(200)
                .body("field.active", equalTo(false));

        as(admin).get(fieldsOf(service)).then().body("field.key", contains("camera_count", "site_type"));
        as(admin).queryParam("activeOnly", true).get(fieldsOf(service)).then().body("field.key", contains("site_type"));
        validate(service, Map.of("site_type", "Shop")).then().statusCode(200); // the required field is off
        validate(service, Map.of("camera_count", 4)).then().statusCode(400)
                .body("errors.message", contains("is not a field of this service"));

        as(admin).post(fieldsOf(service) + "/" + cameras + "/activate").then().statusCode(200);
        validate(service, Map.of("site_type", "Shop")).then().statusCode(400).body("errors.field", contains("camera_count"));

        as(admin).delete(fieldsOf(service) + "/" + cameras).then().statusCode(204);
        as(admin).get(fieldsOf(service) + "/" + cameras).then().statusCode(404);
        as(admin).get(fieldsOf(service)).then().body("field.key", contains("site_type"));
        // The same key can now be used for a field of another type.
        createField(service, fieldBody("camera_count", "SELECT", "Camera count", Map.of("options", options("1-4", "5-16"))))
                .then().statusCode(201).body("field.options.value", contains("1-4", "5-16"));
    }

    @Test
    void fieldsFollowCatalogPermissions() {
        String service = service("SOLAR", "Solar");
        String id = field(service, "roof_area", "NUMBER", "Roof area", Map.of());

        given().get(fieldsOf(service)).then().statusCode(401);

        // Sales staff read the fields (to fill in a lead) and check answers, but do not configure them.
        String sales = tokenFor(accounts.activeUser(RoleCodes.SALES_EXECUTIVE));
        as(sales).get(fieldsOf(service)).then().statusCode(200).body("field.key", contains("roof_area"));
        as(sales).body(Map.of("values", Map.of("roof_area", 900))).post(fieldsOf(service) + "/validate")
                .then().statusCode(200);
        as(sales).body(fieldBody("capacity", "NUMBER", "Capacity", Map.of())).post(fieldsOf(service))
                .then().statusCode(403);
        as(sales).body(Map.of("label", "x", "version", 0)).put(fieldsOf(service) + "/" + id).then().statusCode(403);
        as(sales).post(fieldsOf(service) + "/" + id + "/deactivate").then().statusCode(403);
        as(sales).delete(fieldsOf(service) + "/" + id).then().statusCode(403);

        String technician = tokenFor(accounts.activeUser(RoleCodes.TECHNICIAN));
        as(technician).get(fieldsOf(service)).then().statusCode(403);
        as(technician).body(Map.of("values", Map.of())).post(fieldsOf(service) + "/validate").then().statusCode(403);
    }

    @Test
    void configuringAFieldIsAudited() {
        String service = service("SOLAR", "Solar");
        String id = field(service, "roof_area", "NUMBER", "Roof area", Map.of());

        as(admin).queryParam("entityType", "ServiceField").queryParam("entityId", id).get("/api/v1/audit/logs")
                .then().statusCode(200)
                .body("items.action", hasItem("CREATE"))
                .body("items.module", hasItem("catalog"));
    }

    // ------------------------------------------------------------------ stored answers

    @Test
    void answersAreStoredByTypeAndReadBackInFormOrder() {
        String service = service("INTERIOR_DESIGN", "Interiors");
        field(service, "property_type", "SELECT", "Property type", Map.of("options", options("Apartment", "Villa")));
        field(service, "area", "NUMBER", "Area", Map.of("unit", "sq ft"));
        field(service, "budget", "CURRENCY", "Budget", Map.of());
        field(service, "rooms", "MULTI_SELECT", "Rooms", Map.of("options", options("Kitchen", "Bedroom")));
        field(service, "furnished", "BOOLEAN", "Already furnished", Map.of());
        field(service, "possession_date", "DATE", "Possession date", Map.of());
        field(service, "notes", "TEXTAREA", "Notes", Map.of());
        UUID lead = UUID.randomUUID();
        UUID serviceId = UUID.fromString(service);

        leadFields.save(lead, serviceId, Map.of("property_type", "VILLA", "area", 1850.5, "budget", "1200000",
                "rooms", List.of("KITCHEN", "BEDROOM"), "furnished", false, "possession_date", "2027-01-15",
                "notes", " Vastu compliant "));

        List<FieldValue> stored = leadFields.values(lead);
        assertThat(stored).extracting(value -> value.field().key()).containsExactly(
                "property_type", "area", "budget", "rooms", "furnished", "possession_date", "notes");
        assertThat(stored).extracting(FieldValue::value).containsExactly(
                "VILLA", new BigDecimal("1850.5"), new BigDecimal("1200000"), List.of("KITCHEN", "BEDROOM"), false,
                "2027-01-15", "Vastu compliant");
        assertThat(stored.get(1).field().unit()).isEqualTo("sq ft");
        assertThat(stored.get(1).field().type()).isEqualTo(CustomFieldType.NUMBER);

        // Each answer sits in the column of its type, so it can be queried as what it is.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead_custom_field_values v "
                + "JOIN service_custom_fields f ON f.id = v.field_id "
                + "WHERE v.lead_id = ? AND f.field_key = 'area' AND v.value_number > 1000", Long.class, lead)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT value_date::text FROM lead_custom_field_values v "
                + "JOIN service_custom_fields f ON f.id = v.field_id WHERE v.lead_id = ? AND f.field_key = 'possession_date'",
                String.class, lead)).isEqualTo("2027-01-15");
        assertThat(jdbc.queryForObject("SELECT value_json ->> 1 FROM lead_custom_field_values v "
                + "JOIN service_custom_fields f ON f.id = v.field_id WHERE v.lead_id = ? AND f.field_key = 'rooms'",
                String.class, lead)).isEqualTo("BEDROOM");

        // Saving again replaces the set: changed answers change, cleared ones go.
        leadFields.save(lead, serviceId, Map.of("property_type", "APARTMENT", "area", 900, "furnished", true, "notes", ""));
        assertThat(leadFields.values(lead)).extracting(value -> value.field().key() + "=" + value.value())
                .containsExactly("property_type=APARTMENT", "area=900", "furnished=true");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead_custom_field_values WHERE lead_id = ?", Long.class, lead))
                .isEqualTo(3);

        // Another lead's answers are its own.
        UUID otherLead = UUID.randomUUID();
        leadFields.save(otherLead, serviceId, Map.of("area", 400));
        assertThat(leadFields.values(otherLead)).hasSize(1);
        leadFields.delete(lead);
        assertThat(leadFields.values(lead)).isEmpty();
        assertThat(leadFields.values(otherLead)).hasSize(1);
    }

    @Test
    void invalidAnswersAreNotStoredAtAll() {
        String service = service("SOLAR", "Solar");
        field(service, "roof_area", "NUMBER", "Roof area", Map.of("required", true));
        field(service, "notes", "TEXT", "Notes", Map.of());
        UUID lead = UUID.randomUUID();

        assertThatThrownBy(() -> leadFields.save(lead, UUID.fromString(service), Map.of("notes", "Fine", "roof_area", "big")))
                .isInstanceOfSatisfying(FieldValidationException.class, e ->
                        assertThat(e.getViolations()).extracting(v -> v.field() + " " + v.message())
                                .containsExactly("roof_area must be a number"));
        assertThat(leadFields.values(lead)).isEmpty();
    }

    @Test
    void answersOutliveChangesToTheirField() {
        String service = service("CCTV_SECURITY", "CCTV");
        String siteType = field(service, "site_type", "SELECT", "Site type", Map.of("options", options("Home", "Shop")));
        String cameras = field(service, "camera_count", "NUMBER", "Cameras", Map.of());
        UUID lead = UUID.randomUUID();
        UUID serviceId = UUID.fromString(service);
        leadFields.save(lead, serviceId, Map.of("site_type", "SHOP", "camera_count", 8));

        // The option is withdrawn: the stored answer stays as it was given.
        as(admin).body(Map.of("label", "Site type", "options", options("Home", "Office"), "version", 0))
                .put(fieldsOf(service) + "/" + siteType).then().statusCode(200);
        assertThat(leadFields.values(lead)).extracting(FieldValue::value).contains("SHOP");
        validate(service, Map.of("site_type", "SHOP")).then().statusCode(400); // but it can no longer be chosen

        // The field is removed: its answer is no longer shown, but the row is kept.
        as(admin).delete(fieldsOf(service) + "/" + cameras).then().statusCode(204);
        assertThat(leadFields.values(lead)).extracting(value -> value.field().key()).containsExactly("site_type");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead_custom_field_values WHERE lead_id = ?", Long.class, lead))
                .isEqualTo(2);
        // Saving the lead again still works with a removed field's answer in the table.
        leadFields.save(lead, serviceId, Map.of("site_type", "HOME"));
        assertThat(leadFields.values(lead)).extracting(FieldValue::value).containsExactly("HOME");
    }

    // ------------------------------------------------------------------ files

    @Test
    void aFileIsUploadedCheckedAndThenAcceptedAsTheAnswer() {
        String service = service("INTERIOR_DESIGN", "Interiors");
        String planField = field(service, "floor_plan", "FILE", "Floor plan",
                Map.of("allowedFileTypes", List.of("pdf"), "maxFileSizeMb", 1, "required", true));
        field(service, "site_photo", "FILE", "Site photo", Map.of());
        field(service, "area", "NUMBER", "Area", Map.of());
        byte[] pdf = "%PDF-1.7 plan".getBytes(StandardCharsets.UTF_8);

        Response upload = upload(service, "floor_plan", "C:\\Users\\me\\My Plan.pdf", pdf, "application/pdf");
        upload.then().statusCode(201)
                .body("fileName", equalTo("My Plan.pdf"))
                .body("contentType", equalTo("application/pdf"))
                .body("sizeBytes", equalTo(pdf.length))
                .body("storageKey", startsWith("lead-fields/" + planField + "/"));
        String key = upload.path("storageKey");
        assertThat(key).endsWith(".pdf").doesNotContain("My Plan").doesNotContain("Users");
        assertThat(storage.bytes(key)).isEqualTo(pdf);

        Map<String, Object> reference = Map.of("storageKey", key, "fileName", "My Plan.pdf",
                "contentType", "application/pdf", "sizeBytes", pdf.length);
        validate(service, Map.of("floor_plan", reference)).then().statusCode(200)
                .body("values.floor_plan.storageKey", equalTo(key));

        UUID lead = UUID.randomUUID();
        leadFields.save(lead, UUID.fromString(service), Map.of("floor_plan", reference));
        assertThat(leadFields.values(lead)).extracting(FieldValue::value)
                .containsExactly(new FileReference(key, "My Plan.pdf", "application/pdf", pdf.length));

        // The field's rules apply to the upload itself.
        upload(service, "floor_plan", "plan.exe", pdf, "application/octet-stream").then().statusCode(400)
                .body("errors[0].field", equalTo("floor_plan")).body("errors[0].message", equalTo("must be a file of type pdf"));
        upload(service, "floor_plan", "big.pdf", new byte[1024 * 1024 + 1], "application/pdf").then().statusCode(400)
                .body("errors[0].message", equalTo("must be at most 1 MB"));
        upload(service, "floor_plan", "empty.pdf", new byte[0], "application/pdf").then().statusCode(400);
        upload(service, "area", "plan.pdf", pdf, "application/pdf").then().statusCode(404); // not a file field
        upload(service, "no_such_field", "plan.pdf", pdf, "application/pdf").then().statusCode(404);

        // A reference is accepted only for the field the file was uploaded for.
        validate(service, Map.of("floor_plan", reference, "site_photo", reference)).then().statusCode(400)
                .body("errors.field", contains("site_photo"))
                .body("errors.message", contains("must be a file uploaded for this field"));
        validate(service, Map.of("floor_plan", "My Plan.pdf")).then().statusCode(400)
                .body("errors.message", contains("must be an uploaded file"));

        String sales = tokenFor(accounts.activeUser(RoleCodes.SALES_EXECUTIVE));
        given().header("Authorization", "Bearer " + sales).multiPart("file", "plan.pdf", pdf, "application/pdf")
                .post(fieldsOf(service) + "/floor_plan/files").then().statusCode(403);
    }

    @Test
    void theApiAnswersNullForSettingsThatDoNotApply() {
        String service = service("SOLAR", "Solar");
        field(service, "notes", "TEXT", "Notes", Map.of());

        as(admin).get(fieldsOf(service)).then()
                .body("field[0].maxLength", nullValue())
                .body("field[0].options", nullValue())
                .body("field[0].active", equalTo(true));
    }

    // ------------------------------------------------------------------ helpers

    private static String fieldsOf(String serviceId) {
        return CATALOG + "/services/" + serviceId + "/custom-fields";
    }

    /** A new service in a new category of the given vertical; returns its id. */
    private String service(String vertical, String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String categoryId = as(admin)
                .body(Map.of("vertical", vertical, "code", "CAT_" + tag + "_" + suffix, "name", name + " category " + suffix))
                .post(CATALOG + "/categories").then().statusCode(201).extract().path("id");
        return as(admin)
                .body(Map.of("categoryId", categoryId, "code", "SVC_" + tag + "_" + suffix, "name", name + " " + suffix,
                        "billingType", "QUOTE_BASED"))
                .post(CATALOG + "/services").then().statusCode(201).extract().path("id");
    }

    private static Map<String, Object> fieldBody(String key, String type, String label, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>(Map.of("key", key, "type", type, "label", label));
        body.putAll(extra);
        return body;
    }

    private Response createField(String serviceId, Map<String, Object> body) {
        return as(admin).body(body).post(fieldsOf(serviceId));
    }

    /** Creates a field and returns its id. */
    private String field(String serviceId, String key, String type, String label, Map<String, Object> extra) {
        return createField(serviceId, fieldBody(key, type, label, extra)).then().statusCode(201).extract().path("field.id");
    }

    /** Options the way the admin screen builds them: the value is derived from the label. */
    private static List<Map<String, String>> options(String... labels) {
        return java.util.Arrays.stream(labels)
                .map(label -> Map.of("value", label.toUpperCase().replaceAll("[^A-Z0-9-]+", "_"), "label", label))
                .toList();
    }

    private Response validate(String serviceId, Map<String, Object> values) {
        return as(admin).body(Map.of("values", values)).post(fieldsOf(serviceId) + "/validate");
    }

    private Response upload(String serviceId, String fieldKey, String fileName, byte[] content, String contentType) {
        return given().header("Authorization", "Bearer " + admin)
                .multiPart("file", fileName, content, contentType)
                .post(fieldsOf(serviceId) + "/" + fieldKey + "/files");
    }
}
