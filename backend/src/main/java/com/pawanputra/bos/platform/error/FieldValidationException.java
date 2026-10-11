package com.pawanputra.bos.platform.error;

import java.util.List;

/**
 * Validation that cannot be expressed as annotations on a request class, because what is valid is
 * decided at run time (for example by fields an administrator configured). Reported exactly like
 * annotation-based validation: {@code VALIDATION_FAILED} with one entry per offending field.
 */
public class FieldValidationException extends ApiException {

    /** @param field the name the client used for the value, so it can show the message next to it */
    public record Violation(String field, String message) {
    }

    private final transient List<Violation> violations;

    public FieldValidationException(List<Violation> violations) {
        super(ErrorCode.VALIDATION_FAILED, "One or more values are invalid");
        this.violations = List.copyOf(violations);
    }

    public FieldValidationException(String field, String message) {
        this(List.of(new Violation(field, message)));
    }

    public List<Violation> getViolations() {
        return violations;
    }
}
