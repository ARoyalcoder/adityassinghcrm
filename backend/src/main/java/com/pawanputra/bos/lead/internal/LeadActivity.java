package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.customer.api.InteractionType;
import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Something that was done about a lead: a call, a meeting, an email, a visit. Logged once, as a record. */
@Entity
@Table(name = "lead_activities")
public class LeadActivity extends AuditableEntity {

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private InteractionType type;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "subject", nullable = false, updatable = false, length = 200)
    private String subject;

    @Column(name = "logged_by_name", nullable = false, updatable = false, length = 200)
    private String loggedByName;

    protected LeadActivity() {
        // for JPA
    }

    public LeadActivity(UUID leadId, InteractionType type, Instant occurredAt, String subject, String loggedByName) {
        this.leadId = leadId;
        this.type = type;
        this.occurredAt = occurredAt;
        this.subject = subject;
        this.loggedByName = loggedByName;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public InteractionType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getSubject() {
        return subject;
    }

    public String getLoggedByName() {
        return loggedByName;
    }
}
