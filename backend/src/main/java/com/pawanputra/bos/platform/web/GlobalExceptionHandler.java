package com.pawanputra.bos.platform.web;

import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.web.ApiErrorResponse.FieldViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception that escapes a controller into an {@link ApiErrorResponse}.
 * Feature modules throw {@link ApiException} subclasses and never build error bodies themselves.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_SERVER_ERROR =
            "An unexpected error occurred. Quote the requestId when contacting support.";

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<Object> handleFieldValidation(FieldValidationException ex, WebRequest request) {
        List<FieldViolation> violations = ex.getViolations().stream()
                .map(v -> new FieldViolation(v.field(), v.message()))
                .toList();
        return respond(ex.getErrorCode(), ex.getMessage(), violations, request);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        log.debug("Request rejected: {} - {}", ex.getErrorCode(), ex.getMessage());
        return respond(ex.getErrorCode(), ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "One or more values are invalid", violations, request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<Object> handleDataConflict(Exception ex, WebRequest request) {
        // The database message can leak schema details, so it is logged but not returned.
        log.warn("Data conflict: {}", ex.getMessage());
        return respond(ErrorCode.CONFLICT, "The request conflicts with the current state of the resource",
                List.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, "Authentication is required to access this resource",
                List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return respond(ErrorCode.FORBIDDEN, "You do not have permission to perform this action",
                List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, GENERIC_SERVER_ERROR, List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(e -> new FieldViolation(e.getField(), e.getDefaultMessage())),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(e -> new FieldViolation(e.getObjectName(), e.getDefaultMessage())))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid", violations, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "One or more values are invalid", violations, request);
    }

    /** Funnel for all Spring MVC framework exceptions (404, 405, 415, unreadable body, type mismatch...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = ErrorCode.fromStatus(statusCode.value());
        String detail;
        if (statusCode.is5xxServerError()) {
            log.error("Framework error", ex);
            detail = GENERIC_SERVER_ERROR;
        } else if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            detail = problem.getDetail();
        } else {
            detail = code.title();
        }
        ApiErrorResponse response = ApiErrorResponse.of(code, detail, path(request));
        // Keep the framework's status (it may be more specific than the code's default) and its headers (e.g. Allow).
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .contentType(ApiErrorResponse.MEDIA_TYPE)
                .body(response);
    }

    private ResponseEntity<Object> respond(
            ErrorCode code, String detail, List<FieldViolation> violations, WebRequest request) {
        return ResponseEntity.status(code.status())
                .contentType(ApiErrorResponse.MEDIA_TYPE)
                .body(ApiErrorResponse.of(code, detail, path(request), violations));
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
