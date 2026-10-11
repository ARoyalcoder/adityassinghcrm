package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.catalog.api.LeadCustomFields.FieldDefinition;
import com.pawanputra.bos.catalog.api.LeadCustomFields.FileReference;
import com.pawanputra.bos.catalog.api.LeadCustomFields.Option;
import com.pawanputra.bos.platform.error.FieldValidationException.Violation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks a set of answers against field definitions. Everything it knows about a field comes from
 * the definition it is given: there is no field, service or vertical named in this class.
 */
final class CustomFieldValidator {

    static final int DEFAULT_TEXT_LENGTH = 255;
    static final int DEFAULT_TEXTAREA_LENGTH = 4000;
    static final int DEFAULT_FILE_SIZE_MB = 10;
    /** Every stored object of a FILE field lives under this prefix followed by the field's id. */
    static final String FILE_KEY_PREFIX = "lead-fields/";

    /**
     * @param values     the accepted answers, normalised, keyed by field key; unanswered fields are absent
     * @param violations one per field that is wrong; empty when everything is acceptable
     */
    record Result(Map<String, Object> values, List<Violation> violations) {
    }

    private CustomFieldValidator() {
    }

    static Result validate(List<FieldDefinition> fields, Map<String, Object> answers) {
        Map<String, Object> given = answers == null ? Map.of() : answers;
        Map<String, Object> accepted = new LinkedHashMap<>();
        List<Violation> violations = new ArrayList<>();

        Set<String> known = fields.stream().map(FieldDefinition::key).collect(Collectors.toSet());
        for (String key : given.keySet()) {
            if (!known.contains(key)) {
                violations.add(new Violation(key, "is not a field of this service"));
            }
        }
        for (FieldDefinition field : fields) {
            Object raw = given.get(field.key());
            if (isEmpty(raw)) {
                if (field.required()) {
                    violations.add(new Violation(field.key(), "is required"));
                }
                continue;
            }
            try {
                accepted.put(field.key(), check(field, raw));
            } catch (Rejected rejected) {
                violations.add(new Violation(field.key(), rejected.getMessage()));
            }
        }
        return new Result(accepted, violations);
    }

    static String fileKeyPrefix(FieldDefinition field) {
        return FILE_KEY_PREFIX + field.id() + "/";
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Checks a file's name and size against the field's rules; used for uploads and for references. */
    static void checkFile(FieldDefinition field, String fileName, long sizeBytes) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
            throw new Rejected("must have a file name of at most 255 characters");
        }
        List<String> allowed = field.allowedFileTypes();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(extensionOf(fileName))) {
            throw new Rejected("must be a file of type " + String.join(", ", allowed));
        }
        int maxMb = field.maxFileSizeMb() == null ? DEFAULT_FILE_SIZE_MB : field.maxFileSizeMb();
        if (sizeBytes <= 0) {
            throw new Rejected("must not be an empty file");
        }
        if (sizeBytes > maxMb * 1024L * 1024L) {
            throw new Rejected("must be at most " + maxMb + " MB");
        }
    }

    /** Thrown inside this class only, to carry the message for one field. */
    static final class Rejected extends RuntimeException {
        Rejected(String message) {
            super(message, null, false, false);
        }
    }

    private static boolean isEmpty(Object raw) {
        return raw == null
                || (raw instanceof String text && text.isBlank())
                || (raw instanceof Collection<?> items && items.isEmpty());
    }

    private static Object check(FieldDefinition field, Object raw) {
        return switch (field.type()) {
            case TEXT -> text(raw, field.maxLength() == null ? DEFAULT_TEXT_LENGTH : field.maxLength());
            case TEXTAREA -> text(raw, field.maxLength() == null ? DEFAULT_TEXTAREA_LENGTH : field.maxLength());
            case NUMBER -> number(field, raw, 4);
            case CURRENCY -> number(field, raw, 2);
            case SELECT -> option(field, raw);
            case MULTI_SELECT -> options(field, raw);
            case BOOLEAN -> {
                if (raw instanceof Boolean answer) {
                    yield answer;
                }
                throw new Rejected("must be yes or no");
            }
            case DATE -> date(raw);
            case FILE -> file(field, raw);
        };
    }

    private static String text(Object raw, int maxLength) {
        if (!(raw instanceof String text)) {
            throw new Rejected("must be text");
        }
        String trimmed = text.trim();
        if (trimmed.length() > maxLength) {
            throw new Rejected("must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static BigDecimal number(FieldDefinition field, Object raw, int maxDecimals) {
        BigDecimal value;
        try {
            if (raw instanceof Number number) {
                value = new BigDecimal(number.toString());
            } else if (raw instanceof String text) {
                value = new BigDecimal(text.trim());
            } else {
                throw new Rejected("must be a number");
            }
        } catch (NumberFormatException notANumber) {
            throw new Rejected("must be a number");
        }
        value = CustomFieldJson.plain(value);
        if (value.scale() > maxDecimals) {
            throw new Rejected("must have at most " + maxDecimals + " decimal places");
        }
        if (value.precision() - value.scale() > 14) {
            throw new Rejected("is too large");
        }
        // Money is never negative unless the administrator set a lower limit that says otherwise.
        BigDecimal min = field.minValue() == null && maxDecimals == 2 ? BigDecimal.ZERO : field.minValue();
        if (min != null && value.compareTo(min) < 0) {
            throw new Rejected("must be at least " + min.toPlainString());
        }
        if (field.maxValue() != null && value.compareTo(field.maxValue()) > 0) {
            throw new Rejected("must be at most " + field.maxValue().toPlainString());
        }
        return value;
    }

    private static Set<String> optionValues(FieldDefinition field) {
        return field.options() == null
                ? Set.of()
                : field.options().stream().map(Option::value).collect(Collectors.toSet());
    }

    private static String option(FieldDefinition field, Object raw) {
        if (!(raw instanceof String choice) || !optionValues(field).contains(choice)) {
            throw new Rejected("must be one of the available options");
        }
        return choice;
    }

    private static List<String> options(FieldDefinition field, Object raw) {
        if (!(raw instanceof Collection<?> choices)) {
            throw new Rejected("must be a list of options");
        }
        Set<String> allowed = optionValues(field);
        Set<String> chosen = new LinkedHashSet<>();
        for (Object choice : choices) {
            if (!(choice instanceof String value) || !allowed.contains(value)) {
                throw new Rejected("must contain only the available options");
            }
            if (!chosen.add(value)) {
                throw new Rejected("must not contain the same option twice");
            }
        }
        return List.copyOf(chosen);
    }

    private static String date(Object raw) {
        if (raw instanceof String text) {
            try {
                return LocalDate.parse(text.trim()).toString();
            } catch (DateTimeParseException notADate) {
                // reported below
            }
        }
        throw new Rejected("must be a date (YYYY-MM-DD)");
    }

    private static FileReference file(FieldDefinition field, Object raw) {
        if (!(raw instanceof Map<?, ?> reference)
                || !(reference.get("storageKey") instanceof String storageKey)
                || !(reference.get("fileName") instanceof String fileName)
                || !(reference.get("sizeBytes") instanceof Number size)) {
            throw new Rejected("must be an uploaded file");
        }
        // A reference is only accepted for an object that was uploaded for this very field.
        if (!storageKey.startsWith(fileKeyPrefix(field)) || storageKey.contains("..")) {
            throw new Rejected("must be a file uploaded for this field");
        }
        checkFile(field, fileName, size.longValue());
        String contentType = reference.get("contentType") instanceof String type ? type : null;
        return new FileReference(storageKey, fileName, contentType, size.longValue());
    }
}
