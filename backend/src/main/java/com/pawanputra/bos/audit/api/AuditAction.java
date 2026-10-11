package com.pawanputra.bos.audit.api;

/**
 * What happened. Persisted by name; must match the CHECK constraints on {@code audit_logs.action}
 * and {@code entity_activity_logs.action}. Add a value here and in a migration together.
 */
public enum AuditAction {
    /** Recorded automatically for {@link Audited} entities. */
    CREATE,
    /** Recorded automatically for {@link Audited} entities. */
    UPDATE,
    /** Recorded automatically for {@link Audited} entities, for soft deletes as well as real ones. */
    DELETE,
    LOGIN,
    LOGOUT,
    /** Recorded automatically when only an entity's {@code active} / {@code status} field changes. */
    STATUS_CHANGE,
    APPROVAL,
    PAYMENT,
    EXPORT,
    IMPORT
}
