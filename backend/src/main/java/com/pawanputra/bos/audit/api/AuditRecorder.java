package com.pawanputra.bos.audit.api;

import java.util.UUID;

/**
 * Writes to the audit log. Inject it wherever something worth auditing happens that is not a plain
 * change to an {@link Audited} entity (those are recorded automatically).
 *
 * <p>The event joins the caller's transaction: if the business change rolls back, so does its audit
 * row, and if the audit row cannot be written the business change fails. There is never one without
 * the other.
 */
public interface AuditRecorder {

    /** @return the id of the new audit row */
    UUID record(AuditEvent event);
}
