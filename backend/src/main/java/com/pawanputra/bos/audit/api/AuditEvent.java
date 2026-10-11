package com.pawanputra.bos.audit.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One thing that happened, to be written to the audit log. Build it with {@link #of} and pass it to
 * {@link AuditRecorder#record(AuditEvent)}.
 *
 * <pre>{@code
 * auditRecorder.record(AuditEvent.of(AuditAction.APPROVAL, "finance", "Approved invoice")
 *         .entity("Invoice", invoice.getId(), invoice.getNumber())
 *         .before(Map.of("status", "PENDING"))
 *         .after(Map.of("status", "APPROVED"))
 *         .metadata("amount", invoice.getTotal()));
 * }</pre>
 *
 * <p>The actor, their organization, the IP address, the user agent and the request id are filled in
 * from the current request; set {@link #actor} only when the current request has no signed-in user
 * yet (signing in) or the event is about a different user.
 *
 * <p>Values in {@code before}, {@code after} and {@code metadata} are stored as JSON. Never put a
 * password, token or other secret in them.
 */
public final class AuditEvent {

    private final AuditAction action;
    private final String module;
    private final String summary;
    private String entityType;
    private String entityId;
    private String entityLabel;
    private Map<String, ?> before;
    private Map<String, ?> after;
    private final Map<String, Object> metadata = new LinkedHashMap<>();
    private String activityOwnerType;
    private String activityOwnerId;
    private boolean actorOverridden;
    private UUID actorId;
    private String actorLabel;
    private UUID organizationId;

    private AuditEvent(AuditAction action, String module, String summary) {
        this.action = Objects.requireNonNull(action, "action");
        this.module = Objects.requireNonNull(module, "module");
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    /**
     * @param module  the module that owns the event, lower-case: {@code "finance"}
     * @param summary one sentence in the past tense, shown in the audit list: {@code "Approved invoice"}
     */
    public static AuditEvent of(AuditAction action, String module, String summary) {
        return new AuditEvent(action, module, summary);
    }

    /**
     * What the event is about. With an entity set, the event also appears in that entity's activity timeline.
     *
     * @param label a name a human recognises it by (may be {@code null})
     */
    public AuditEvent entity(String type, Object id, String label) {
        this.entityType = type;
        this.entityId = id == null ? null : id.toString();
        this.entityLabel = label;
        return this;
    }

    /**
     * Shows the event in another record's activity timeline instead of the entity's own, for an
     * entity that is part of something bigger (a contact's changes under its customer).
     */
    public AuditEvent activityOwner(String type, Object id) {
        this.activityOwnerType = type;
        this.activityOwnerId = id == null ? null : id.toString();
        return this;
    }

    /** The relevant values before the event. */
    public AuditEvent before(Map<String, ?> values) {
        this.before = values;
        return this;
    }

    /** The relevant values after the event. */
    public AuditEvent after(Map<String, ?> values) {
        this.after = values;
        return this;
    }

    /** Anything else worth knowing: a reason, a count, a file name, an outcome. {@code null} values are skipped. */
    public AuditEvent metadata(String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
        return this;
    }

    /**
     * Names the actor explicitly instead of taking the signed-in user of the current request.
     *
     * @param actorId        {@code null} if nobody is identified (a sign-in attempt for an unknown email)
     * @param actorLabel     how to show them, usually an email
     * @param organizationId whose audit log the event belongs to; {@code null} hides it from every organization
     */
    public AuditEvent actor(UUID actorId, String actorLabel, UUID organizationId) {
        this.actorOverridden = true;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.organizationId = organizationId;
        return this;
    }

    public AuditAction action() {
        return action;
    }

    public String module() {
        return module;
    }

    public String summary() {
        return summary;
    }

    public String entityType() {
        return entityType;
    }

    public String entityId() {
        return entityId;
    }

    public String entityLabel() {
        return entityLabel;
    }

    /** The record whose timeline shows this event: the owner if one was set, otherwise the entity itself. */
    public String timelineType() {
        return activityOwnerType != null ? activityOwnerType : entityType;
    }

    public String timelineId() {
        return activityOwnerType != null ? activityOwnerId : entityId;
    }

    public Map<String, ?> before() {
        return before;
    }

    public Map<String, ?> after() {
        return after;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public boolean actorOverridden() {
        return actorOverridden;
    }

    public UUID actorId() {
        return actorId;
    }

    public String actorLabel() {
        return actorLabel;
    }

    public UUID organizationId() {
        return organizationId;
    }
}
