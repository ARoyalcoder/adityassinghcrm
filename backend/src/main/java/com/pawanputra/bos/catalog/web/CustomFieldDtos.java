package com.pawanputra.bos.catalog.web;

import com.pawanputra.bos.catalog.api.CustomFieldType;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FieldDefinition;
import com.pawanputra.bos.catalog.api.LeadCustomFields.Option;
import com.pawanputra.bos.catalog.internal.ServiceCustomFieldService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Requests and responses of the custom-field endpoints. */
final class CustomFieldDtos {

    static final String KEY_PATTERN = "^[a-z][a-z0-9_]*$";
    static final String KEY_MESSAGE = "must be lower_snake_case: start with a letter, then letters, digits or underscores";

    private CustomFieldDtos() {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A field with what is needed to edit it. */
    record FieldResponse(FieldDefinition field, long version, Instant updatedAt) {
    }

    record OptionRequest(
            @NotBlank @Size(max = 60)
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain only letters, digits, underscores and hyphens")
            String value,
            @NotBlank @Size(max = 100) String label) {
    }

    /**
     * Create a field. The key and the type cannot be changed afterwards.
     *
     * @param displayOrder leave out to put the field last
     */
    record CreateFieldRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String key,
            @NotNull CustomFieldType type,
            @NotBlank @Size(max = 120) String label,
            @Size(max = 300) String helpText,
            Boolean required,
            @Size(max = 100) List<@NotNull @Valid OptionRequest> options,
            @Digits(integer = 14, fraction = 4) BigDecimal minValue,
            @Digits(integer = 14, fraction = 4) BigDecimal maxValue,
            @Min(1) @Max(10000) Integer maxLength,
            @Size(max = 30) String unit,
            @Size(max = 20) List<@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{1,10}$",
                    message = "must be a file extension such as pdf") String> allowedFileTypes,
            @Min(1) @Max(25) Integer maxFileSizeMb,
            @Min(0) @Max(9999) Integer displayOrder) {

        ServiceCustomFieldService.Details details() {
            return CustomFieldDtos.details(label, helpText, required, options, minValue, maxValue, maxLength, unit,
                    allowedFileTypes, maxFileSizeMb, displayOrder);
        }
    }

    /** Same as create without the key and type, and with the version being edited. */
    record UpdateFieldRequest(
            @NotBlank @Size(max = 120) String label,
            @Size(max = 300) String helpText,
            Boolean required,
            @Size(max = 100) List<@NotNull @Valid OptionRequest> options,
            @Digits(integer = 14, fraction = 4) BigDecimal minValue,
            @Digits(integer = 14, fraction = 4) BigDecimal maxValue,
            @Min(1) @Max(10000) Integer maxLength,
            @Size(max = 30) String unit,
            @Size(max = 20) List<@NotBlank @Pattern(regexp = "^[A-Za-z0-9]{1,10}$",
                    message = "must be a file extension such as pdf") String> allowedFileTypes,
            @Min(1) @Max(25) Integer maxFileSizeMb,
            @Min(0) @Max(9999) Integer displayOrder,
            @NotNull Long version) {

        ServiceCustomFieldService.Details details() {
            return CustomFieldDtos.details(label, helpText, required, options, minValue, maxValue, maxLength, unit,
                    allowedFileTypes, maxFileSizeMb, displayOrder);
        }
    }

    /** Answers to check: field key to value. */
    record ValidateRequest(Map<String, Object> values) {
    }

    /** The accepted answers, normalised. */
    record ValidateResponse(Map<String, Object> values) {
    }

    private static ServiceCustomFieldService.Details details(
            String label, String helpText, Boolean required, List<OptionRequest> options, BigDecimal minValue,
            BigDecimal maxValue, Integer maxLength, String unit, List<String> allowedFileTypes, Integer maxFileSizeMb,
            Integer displayOrder) {
        return new ServiceCustomFieldService.Details(
                label.trim(), trimToNull(helpText), Boolean.TRUE.equals(required),
                options == null ? null : options.stream().map(o -> new Option(o.value(), o.label().trim())).toList(),
                minValue, maxValue, maxLength, trimToNull(unit), allowedFileTypes, maxFileSizeMb, displayOrder);
    }
}
