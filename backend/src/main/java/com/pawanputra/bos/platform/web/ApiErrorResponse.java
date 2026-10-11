package com.pawanputra.bos.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.logging.RequestCorrelationFilter;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

/**
 * The single error body returned by the API (RFC 9457 problem details plus a few extensions).
 * Served as {@code application/problem+json}.
 *
 * @param type      URI identifying the problem type
 * @param title     short, stable summary of the problem type
 * @param status    HTTP status code
 * @param code      machine-readable {@link ErrorCode}; the field clients should branch on
 * @param detail    human-readable explanation of this occurrence
 * @param instance  request path that produced the error
 * @param requestId correlation id, also returned in the X-Request-Id header and present in server logs
 * @param timestamp when the error was produced (UTC)
 * @param errors    per-field problems; only present for validation failures
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        String requestId,
        Instant timestamp,
        List<FieldViolation> errors) {

    public static final MediaType MEDIA_TYPE = MediaType.APPLICATION_PROBLEM_JSON;

    /**
     * @param field   property path of the offending input, e.g. {@code address.pinCode}
     * @param message human-readable reason
     */
    public record FieldViolation(String field, String message) {
    }

    public static ApiErrorResponse of(ErrorCode code, String detail, String path) {
        return of(code, detail, path, List.of());
    }

    public static ApiErrorResponse of(ErrorCode code, String detail, String path, List<FieldViolation> errors) {
        return new ApiErrorResponse(
                code.typeUri(),
                code.title(),
                code.status().value(),
                code.name(),
                detail,
                path,
                MDC.get(RequestCorrelationFilter.MDC_KEY),
                Instant.now(),
                errors);
    }
}
