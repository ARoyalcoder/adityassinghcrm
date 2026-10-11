package com.pawanputra.bos.audit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a JPA entity and every create, update, delete and status change of it is recorded in
 * the audit log and in the entity's activity timeline, with before and after values, in the same
 * transaction as the change. Nothing else is needed: no code in the service or the controller.
 *
 * <pre>{@code
 * @Entity
 * @Audited(module = "customers", entity = "Customer", label = "name")
 * public class Customer extends SoftDeletableEntity { ... }
 * }</pre>
 *
 * <p>What is recorded: every mapped field except collections, the technical columns (timestamps,
 * actor columns, version) and fields marked {@link AuditExclude}. A reference to another entity is
 * recorded as that entity's id.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** The module that owns the entity, lower-case: {@code "catalog"}. Used to filter the audit log. */
    String module();

    /** The name administrators know the entity by: {@code "Service"}. Stable; it is stored in every row. */
    String entity();

    /**
     * The property whose value names one instance to a human ({@code "name"}, {@code "email"}).
     * Empty if there is none.
     */
    String label() default "";
}
