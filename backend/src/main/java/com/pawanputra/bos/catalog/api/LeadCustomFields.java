package com.pawanputra.bos.catalog.api;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The service-specific part of a lead: which extra fields a service asks for, whether a set of
 * answers is acceptable, and the answers stored for a lead.
 *
 * <p>This is what the lead module uses. It passes the answers through as it received them
 * ({@code field key -> value}) and never needs to know what any field is: the fields are
 * configuration, read at run time.
 */
public interface LeadCustomFields {

    /** One choice of a SELECT or MULTI_SELECT field. {@code value} is what is stored. */
    record Option(String value, String label) {
    }

    /**
     * A field as configured by an administrator. Properties that do not apply to the field's type
     * are {@code null}.
     *
     * @param key              the field's name in the answers map
     * @param allowedFileTypes lower-case extensions; empty means any
     */
    record FieldDefinition(
            UUID id,
            String key,
            String label,
            CustomFieldType type,
            String helpText,
            boolean required,
            List<Option> options,
            BigDecimal minValue,
            BigDecimal maxValue,
            Integer maxLength,
            String unit,
            List<String> allowedFileTypes,
            Integer maxFileSizeMb,
            int displayOrder,
            boolean active) {
    }

    /** Where an uploaded file was stored. This is the answer to a FILE field. */
    record FileReference(String storageKey, String fileName, String contentType, long sizeBytes) {
    }

    /**
     * A stored answer.
     *
     * @param value a String (TEXT, TEXTAREA, SELECT, and DATE as {@code yyyy-MM-dd}), BigDecimal
     *              (NUMBER, CURRENCY), Boolean, a list of Strings (MULTI_SELECT) or a
     *              {@link FileReference}
     */
    record FieldValue(FieldDefinition field, Object value) {
    }

    /** The active fields of a service, in display order. */
    List<FieldDefinition> fields(UUID serviceId);

    /**
     * Checks answers against the service's active fields.
     *
     * @param answers field key to value, as parsed from JSON; missing, {@code null}, blank and empty
     *                all mean "not answered"
     * @return the accepted answers in normalised form (trimmed, numbers as BigDecimal), without the
     *         unanswered ones
     * @throws com.pawanputra.bos.platform.error.FieldValidationException naming every field that is wrong
     */
    Map<String, Object> validate(UUID serviceId, Map<String, Object> answers);

    /** Validates the answers, then stores them as the complete set of answers for the lead. */
    void save(UUID leadId, UUID serviceId, Map<String, Object> answers);

    /** The lead's stored answers, in the fields' display order. Answers to removed fields are left out. */
    List<FieldValue> values(UUID leadId);

    /** Removes the lead's answers (for when the lead itself is removed). */
    void delete(UUID leadId);

    /**
     * Stores an uploaded file for a FILE field after checking it against the field's rules, and
     * returns the reference to use as the field's answer.
     */
    FileReference storeFile(
            UUID serviceId, String fieldKey, String fileName, String contentType, long sizeBytes, InputStream content);
}
