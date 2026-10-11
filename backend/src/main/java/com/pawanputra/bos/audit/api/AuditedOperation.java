package com.pawanputra.bos.audit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records one audit event each time the annotated method completes successfully. For operations
 * that are not a change to a single row and so are not caught by {@link Audited}: an export, an
 * import, an approval, a payment.
 *
 * <pre>{@code
 * @AuditedOperation(action = AuditAction.EXPORT, module = "customers", entity = "Customer",
 *                   summary = "Exported customers")
 * public ExportFile exportCustomers(CustomerFilter filter) { ... }
 * }</pre>
 *
 * <p>The method's arguments are stored as metadata by parameter name, so the event says what was
 * exported or approved. Do not put secrets or large objects in the parameters of an annotated
 * method. If the method throws, nothing is recorded.
 *
 * <p>The method must be called through its Spring bean (not {@code this.method()}), like
 * {@code @Transactional}. When the details are only known inside the method, call
 * {@link AuditRecorder#record(AuditEvent)} instead.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedOperation {

    AuditAction action();

    /** The module that owns the operation, lower-case: {@code "customers"}. */
    String module();

    /** What the operation is about: {@code "Customer"}. Empty if it is not about one kind of entity. */
    String entity() default "";

    /** One sentence in the past tense: {@code "Exported customers"}. */
    String summary();
}
