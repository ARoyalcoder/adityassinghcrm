package com.pawanputra.bos.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawanputra.bos.catalog.api.CustomFieldType;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FieldDefinition;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FileReference;
import com.pawanputra.bos.catalog.api.LeadCustomFields.Option;
import com.pawanputra.bos.catalog.internal.CustomFieldValidator.Result;
import com.pawanputra.bos.catalog.internal.ServiceCustomFieldService.Details;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.FieldValidationException.Violation;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CustomFieldValidatorTest {

    private static final List<Option> PLACES =
            List.of(new Option("INDOOR", "Indoor"), new Option("OUTDOOR", "Outdoor"), new Option("BOTH", "Both"));

    private static FieldDefinition field(String key, CustomFieldType type) {
        return new FieldDefinition(UUID.randomUUID(), key, key, type, null, false,
                type.hasOptions() ? PLACES : null, null, null, null, null, null, null, 0, true);
    }

    private static FieldDefinition required(FieldDefinition f) {
        return new FieldDefinition(f.id(), f.key(), f.label(), f.type(), f.helpText(), true, f.options(),
                f.minValue(), f.maxValue(), f.maxLength(), f.unit(), f.allowedFileTypes(), f.maxFileSizeMb(), 0, true);
    }

    private static FieldDefinition ranged(String key, CustomFieldType type, String min, String max) {
        return new FieldDefinition(UUID.randomUUID(), key, key, type, null, false, null,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max),
                null, null, null, null, 0, true);
    }

    private static FieldDefinition file(String key, List<String> types, Integer maxMb) {
        return new FieldDefinition(UUID.randomUUID(), key, key, CustomFieldType.FILE, null, false, null, null, null,
                null, null, types, maxMb, 0, true);
    }

    private static Object accepted(FieldDefinition field, Object answer) {
        Result result = CustomFieldValidator.validate(List.of(field), Map.of(field.key(), answer));
        assertThat(result.violations()).isEmpty();
        return result.values().get(field.key());
    }

    private static String rejected(FieldDefinition field, Object answer) {
        Map<String, Object> answers = new HashMap<>();
        answers.put(field.key(), answer);
        Result result = CustomFieldValidator.validate(List.of(field), answers);
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().getFirst().field()).isEqualTo(field.key());
        assertThat(result.values()).doesNotContainKey(field.key());
        return result.violations().getFirst().message();
    }

    // ------------------------------------------------------------------ answers, per type

    @Test
    void textIsTrimmedAndLimited() {
        FieldDefinition site = field("site_type", CustomFieldType.TEXT);
        assertThat(accepted(site, "  Warehouse ")).isEqualTo("Warehouse");
        assertThat(rejected(site, "x".repeat(256))).isEqualTo("must be at most 255 characters");
        assertThat(rejected(site, 42)).isEqualTo("must be text");

        FieldDefinition notes = field("design_requirement", CustomFieldType.TEXTAREA);
        assertThat(accepted(notes, "x".repeat(4000))).isEqualTo("x".repeat(4000));
        assertThat(rejected(notes, "x".repeat(4001))).isEqualTo("must be at most 4000 characters");

        FieldDefinition shortText = new FieldDefinition(UUID.randomUUID(), "code", "Code", CustomFieldType.TEXT, null,
                false, null, null, null, 5, null, null, null, 0, true);
        assertThat(rejected(shortText, "123456")).isEqualTo("must be at most 5 characters");
    }

    @Test
    void numberAcceptsNumbersAndNumericTextAndHonoursTheRange() {
        FieldDefinition cameras = ranged("camera_count", CustomFieldType.NUMBER, "1", "500");
        assertThat(accepted(cameras, 16)).isEqualTo(new BigDecimal("16"));
        assertThat(accepted(cameras, 16.0)).isEqualTo(new BigDecimal("16"));
        assertThat(accepted(cameras, " 16 ")).isEqualTo(new BigDecimal("16"));
        assertThat(accepted(cameras, 12.5)).isEqualTo(new BigDecimal("12.5"));
        assertThat(rejected(cameras, 0)).isEqualTo("must be at least 1");
        assertThat(rejected(cameras, 501)).isEqualTo("must be at most 500");
        assertThat(rejected(cameras, "many")).isEqualTo("must be a number");
        assertThat(rejected(cameras, true)).isEqualTo("must be a number");
        assertThat(rejected(cameras, 1.23456)).isEqualTo("must have at most 4 decimal places");

        // Without a range, a number may be negative.
        assertThat(accepted(field("offset", CustomFieldType.NUMBER), -3)).isEqualTo(new BigDecimal("-3"));
        assertThat(rejected(field("big", CustomFieldType.NUMBER), "1000000000000000")).isEqualTo("is too large");
    }

    @Test
    void currencyIsNeverNegativeByDefaultAndHasTwoDecimals() {
        FieldDefinition bill = field("monthly_bill", CustomFieldType.CURRENCY);
        assertThat(accepted(bill, 4500)).isEqualTo(new BigDecimal("4500"));
        assertThat(accepted(bill, "4500.50")).isEqualTo(new BigDecimal("4500.5"));
        assertThat(accepted(bill, 0)).isEqualTo(new BigDecimal("0"));
        assertThat(rejected(bill, -1)).isEqualTo("must be at least 0");
        assertThat(rejected(bill, 10.123)).isEqualTo("must have at most 2 decimal places");

        FieldDefinition budget = ranged("monthly_budget", CustomFieldType.CURRENCY, "5000", null);
        assertThat(rejected(budget, 4999.99)).isEqualTo("must be at least 5000");
    }

    @Test
    void selectTakesExactlyOneOfTheOptions() {
        FieldDefinition place = field("placement", CustomFieldType.SELECT);
        assertThat(accepted(place, "OUTDOOR")).isEqualTo("OUTDOOR");
        assertThat(rejected(place, "Outdoor")).isEqualTo("must be one of the available options"); // the value, not the label
        assertThat(rejected(place, "UNDERWATER")).isEqualTo("must be one of the available options");
        assertThat(rejected(place, List.of("INDOOR"))).isEqualTo("must be one of the available options");
    }

    @Test
    void multiSelectTakesDistinctOptions() {
        FieldDefinition channels = field("channels", CustomFieldType.MULTI_SELECT);
        assertThat(accepted(channels, List.of("OUTDOOR", "INDOOR"))).isEqualTo(List.of("OUTDOOR", "INDOOR"));
        assertThat(rejected(channels, "INDOOR")).isEqualTo("must be a list of options");
        assertThat(rejected(channels, List.of("INDOOR", "ROOF"))).isEqualTo("must contain only the available options");
        assertThat(rejected(channels, List.of("INDOOR", "INDOOR"))).isEqualTo("must not contain the same option twice");
        assertThat(rejected(channels, Arrays.asList("INDOOR", null))).isEqualTo("must contain only the available options");
    }

    @Test
    void booleanMustBeAnActualYesOrNo() {
        FieldDefinition available = field("site_available", CustomFieldType.BOOLEAN);
        assertThat(accepted(available, true)).isEqualTo(true);
        assertThat(accepted(available, false)).isEqualTo(false);
        assertThat(rejected(available, "yes")).isEqualTo("must be yes or no");
        assertThat(rejected(available, 1)).isEqualTo("must be yes or no");
    }

    @Test
    void dateMustBeARealCalendarDate() {
        FieldDefinition visit = field("preferred_visit", CustomFieldType.DATE);
        assertThat(accepted(visit, "2026-11-05")).isEqualTo("2026-11-05");
        assertThat(rejected(visit, "05/11/2026")).isEqualTo("must be a date (YYYY-MM-DD)");
        assertThat(rejected(visit, "2026-02-30")).isEqualTo("must be a date (YYYY-MM-DD)");
        assertThat(rejected(visit, 20261105)).isEqualTo("must be a date (YYYY-MM-DD)");
    }

    @Test
    void fileMustBeAReferenceToAnUploadForThatFieldWithinItsRules() {
        FieldDefinition plan = file("floor_plan", List.of("pdf", "jpg"), 2);
        String key = "lead-fields/" + plan.id() + "/abc.pdf";

        assertThat(accepted(plan, Map.of("storageKey", key, "fileName", "Plan.PDF", "sizeBytes", 1024,
                "contentType", "application/pdf")))
                .isEqualTo(new FileReference(key, "Plan.PDF", "application/pdf", 1024));

        assertThat(rejected(plan, "plan.pdf")).isEqualTo("must be an uploaded file");
        assertThat(rejected(plan, Map.of("fileName", "plan.pdf", "sizeBytes", 10))).isEqualTo("must be an uploaded file");
        assertThat(rejected(plan, Map.of("storageKey", "lead-fields/" + UUID.randomUUID() + "/abc.pdf",
                "fileName", "plan.pdf", "sizeBytes", 10))).isEqualTo("must be a file uploaded for this field");
        assertThat(rejected(plan, Map.of("storageKey", key + "/../../secrets", "fileName", "plan.pdf", "sizeBytes", 10)))
                .isEqualTo("must be a file uploaded for this field");
        assertThat(rejected(plan, Map.of("storageKey", key, "fileName", "plan.exe", "sizeBytes", 10)))
                .isEqualTo("must be a file of type pdf, jpg");
        assertThat(rejected(plan, Map.of("storageKey", key, "fileName", "plan.pdf", "sizeBytes", 3 * 1024 * 1024)))
                .isEqualTo("must be at most 2 MB");
        assertThat(rejected(plan, Map.of("storageKey", key, "fileName", "plan.pdf", "sizeBytes", 0)))
                .isEqualTo("must not be an empty file");

        // No list of types means any type; no size means the default limit.
        FieldDefinition any = file("attachment", null, null);
        String anyKey = "lead-fields/" + any.id() + "/x";
        assertThat(accepted(any, Map.of("storageKey", anyKey, "fileName", "notes", "sizeBytes", 5)))
                .isEqualTo(new FileReference(anyKey, "notes", null, 5));
        assertThat(rejected(any, Map.of("storageKey", anyKey, "fileName", "big.zip", "sizeBytes", 11L * 1024 * 1024)))
                .isEqualTo("must be at most 10 MB");
    }

    // ------------------------------------------------------------------ the set of answers

    @ParameterizedTest
    @EnumSource(CustomFieldType.class)
    void everyTypeCanBeLeftEmptyUnlessRequired(CustomFieldType type) {
        FieldDefinition optional = field("answer", type);
        for (Object empty : Arrays.asList(null, "", "   ", List.of())) {
            Map<String, Object> answers = new HashMap<>();
            answers.put("answer", empty);
            Result result = CustomFieldValidator.validate(List.of(optional), answers);
            assertThat(result.violations()).as("%s left as %s", type, empty).isEmpty();
            assertThat(result.values()).isEmpty();

            assertThat(CustomFieldValidator.validate(List.of(required(optional)), answers).violations())
                    .containsExactly(new Violation("answer", "is required"));
        }
        assertThat(CustomFieldValidator.validate(List.of(required(optional)), Map.of()).violations())
                .containsExactly(new Violation("answer", "is required"));
        assertThat(CustomFieldValidator.validate(List.of(required(optional)), null).violations())
                .containsExactly(new Violation("answer", "is required"));
    }

    @Test
    void aRequiredYesNoFieldAcceptsNo() {
        FieldDefinition available = required(field("site_available", CustomFieldType.BOOLEAN));
        assertThat(accepted(available, false)).isEqualTo(false);
    }

    @Test
    void everyProblemIsReportedAtOnceAndGoodAnswersAreKept() {
        List<FieldDefinition> fields = List.of(
                required(field("roof_area", CustomFieldType.NUMBER)),
                required(field("placement", CustomFieldType.SELECT)),
                field("notes", CustomFieldType.TEXTAREA),
                field("site_available", CustomFieldType.BOOLEAN));
        Map<String, Object> answers = new HashMap<>();
        answers.put("placement", "MOON");
        answers.put("notes", " South-facing roof ");
        answers.put("site_available", "maybe");
        answers.put("favourite_colour", "blue");

        Result result = CustomFieldValidator.validate(fields, answers);

        assertThat(result.violations()).containsExactlyInAnyOrder(
                new Violation("favourite_colour", "is not a field of this service"),
                new Violation("roof_area", "is required"),
                new Violation("placement", "must be one of the available options"),
                new Violation("site_available", "must be yes or no"));
        assertThat(result.values()).containsExactly(Map.entry("notes", "South-facing roof"));
    }

    @Test
    void aServiceWithNoFieldsAcceptsNoAnswersOnly() {
        assertThat(CustomFieldValidator.validate(List.of(), Map.of()).violations()).isEmpty();
        assertThat(CustomFieldValidator.validate(List.of(), Map.of("anything", 1)).violations())
                .containsExactly(new Violation("anything", "is not a field of this service"));
    }

    // ------------------------------------------------------------------ definitions

    private static Details details(List<Option> options, String min, String max, Integer maxLength, String unit,
            List<String> fileTypes, Integer maxFileSizeMb) {
        return new Details("Label", null, false, options, min == null ? null : new BigDecimal(min),
                max == null ? null : new BigDecimal(max), maxLength, unit, fileTypes, maxFileSizeMb, null);
    }

    private static List<String> definitionProblems(CustomFieldType type, Details details) {
        try {
            ServiceCustomFieldService.validate(type, details);
            return List.of();
        } catch (FieldValidationException e) {
            return e.getViolations().stream().map(v -> v.field() + ": " + v.message()).toList();
        }
    }

    @Test
    void aDefinitionMayOnlyUseTheSettingsOfItsType() {
        Details plain = details(null, null, null, null, null, null, null);
        for (CustomFieldType type : CustomFieldType.values()) {
            if (!type.hasOptions()) {
                assertThat(definitionProblems(type, plain)).as(type.name()).isEmpty();
            }
        }
        assertThat(definitionProblems(CustomFieldType.SELECT, plain)).containsExactly("options: Add at least one option");
        assertThat(definitionProblems(CustomFieldType.MULTI_SELECT, details(List.of(), null, null, null, null, null, null)))
                .containsExactly("options: Add at least one option");
        assertThat(definitionProblems(CustomFieldType.SELECT, details(PLACES, null, null, null, null, null, null))).isEmpty();

        assertThat(definitionProblems(CustomFieldType.TEXT, details(PLACES, "1", null, null, "kW", List.of("pdf"), 5)))
                .containsExactly(
                        "options: Only select and multi-select fields have options",
                        "minValue: Only number and currency fields have a range",
                        "unit: Only number fields have a unit",
                        "allowedFileTypes: Only file fields have allowed file types",
                        "maxFileSizeMb: Only file fields have a maximum file size");
        assertThat(definitionProblems(CustomFieldType.NUMBER, details(null, null, null, 10, null, null, null)))
                .containsExactly("maxLength: Only text fields have a maximum length");
        assertThat(definitionProblems(CustomFieldType.CURRENCY, details(null, null, null, null, "kW", null, null)))
                .containsExactly("unit: Only number fields have a unit");
    }

    @Test
    void aDefinitionsLimitsMustMakeSense() {
        assertThat(definitionProblems(CustomFieldType.NUMBER, details(null, "10", "5", null, null, null, null)))
                .containsExactly("maxValue: Must not be less than the minimum");
        assertThat(definitionProblems(CustomFieldType.NUMBER, details(null, "5", "5", null, "kW", null, null))).isEmpty();
        assertThat(definitionProblems(CustomFieldType.TEXT, details(null, null, null, 1001, null, null, null)))
                .containsExactly("maxLength: At most 1000 for this type of field");
        assertThat(definitionProblems(CustomFieldType.TEXTAREA, details(null, null, null, 10000, null, null, null))).isEmpty();
        assertThat(definitionProblems(CustomFieldType.FILE, details(null, null, null, null, null, List.of("pdf"), 26)))
                .containsExactly("maxFileSizeMb: At most 25 MB");

        List<Option> repeated = List.of(new Option("A", "Indoor"), new Option("A", "Outdoor"), new Option("B", "indoor"));
        assertThat(definitionProblems(CustomFieldType.SELECT, details(repeated, null, null, null, null, null, null)))
                .containsExactly(
                        "options: The option value 'A' is used twice",
                        "options: The option 'indoor' is listed twice");
    }

    @Test
    void fileTypesAreStoredInLowerCaseWithoutRepeats() {
        Details checked = ServiceCustomFieldService.validate(CustomFieldType.FILE,
                details(null, null, null, null, null, List.of("PDF", "pdf", "Jpg"), 5));
        assertThat(checked.allowedFileTypes()).containsExactly("pdf", "jpg");
        assertThatThrownBy(() -> ServiceCustomFieldService.validate(CustomFieldType.FILE,
                details(PLACES, null, null, null, null, null, null))).isInstanceOf(FieldValidationException.class);
    }
}
