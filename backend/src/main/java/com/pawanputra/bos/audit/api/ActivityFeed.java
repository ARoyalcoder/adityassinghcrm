package com.pawanputra.bos.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Lets a module show the history of its own records to its own users. The audit screen needs
 * {@code AUDIT_VIEW}; a module that exposes a record's timeline through its own endpoint decides
 * for itself who may see it (usually whoever may view the record).
 */
public interface ActivityFeed {

    /**
     * @param changes field-level changes, each with {@code field}, {@code from} and {@code to}; empty if none
     */
    record ActivityItem(
            UUID id, Instant occurredAt, AuditAction action, String actorLabel, String message,
            List<Map<String, Object>> changes) {
    }

    /** The timeline of one record, newest first, limited to the given organization. */
    Page<ActivityItem> forEntity(UUID organizationId, String entityType, String entityId, Pageable pageable);

    /**
     * One recorded change in a record's history.
     *
     * @param changedEntityType  what actually changed. For a record with parts this may differ from
     *                           the record asked about: a customer's history contains changes to its
     *                           {@code Contact}s and {@code Address}es
     * @param changedEntityLabel the name of the thing that changed, as it was at the time
     */
    record RecordedChange(
            UUID id, Instant occurredAt, AuditAction action, String actorLabel, String message,
            String changedEntityType, String changedEntityLabel) {
    }

    /**
     * A record's history as a stream that can be merged with other time-ordered sources: the newest
     * {@code limit} changes at or before {@code notAfter} ({@code null} for "up to now").
     *
     * @param excludingChangedTypes changes to these kinds of part are left out, for a caller that
     *                              shows them from a better source. Applied before {@code limit}, so
     *                              fewer than {@code limit} results always means "no more"
     */
    List<RecordedChange> changesUpTo(
            UUID organizationId, String entityType, String entityId, Instant notAfter, int limit,
            Set<String> excludingChangedTypes);
}
