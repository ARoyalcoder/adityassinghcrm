package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.customer.api.InteractionDirection;
import com.pawanputra.bos.customer.api.InteractionType;
import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One contact with a customer: a call, a meeting, an email, a site visit. Logged once as a record of
 * what happened; it can be removed if it was logged by mistake, but not rewritten.
 *
 * <p>Deliberately not a {@link CustomerPart}: an interaction reaches the customer timeline as itself
 * ("Call logged"), so it must not also be filed there as a change to the customer record.
 */
@Entity
@Audited(module = "customers", entity = "Interaction", label = "subject")
@Table(name = "customer_interactions")
public class CustomerInteraction extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private InteractionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", updatable = false, length = 10)
    private InteractionDirection direction;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "subject", nullable = false, updatable = false, length = 200)
    private String subject;

    @Column(name = "details", updatable = false, length = 4000)
    private String details;

    @Column(name = "outcome", updatable = false, length = 500)
    private String outcome;

    @Column(name = "logged_by_name", nullable = false, updatable = false, length = 200)
    private String loggedByName;

    protected CustomerInteraction() {
        // for JPA
    }

    public CustomerInteraction(
            Customer customer, InteractionType type, InteractionDirection direction, Instant occurredAt,
            String subject, String details, String outcome, String loggedByName) {
        this.customer = customer;
        this.type = type;
        this.direction = direction;
        this.occurredAt = occurredAt;
        this.subject = subject;
        this.details = details;
        this.outcome = outcome;
        this.loggedByName = loggedByName;
    }

    public Customer getCustomer() {
        return customer;
    }

    public InteractionType getType() {
        return type;
    }

    public InteractionDirection getDirection() {
        return direction;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getSubject() {
        return subject;
    }

    public String getDetails() {
        return details;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getLoggedByName() {
        return loggedByName;
    }
}
