package com.pawanputra.bos.opportunity.api;

import com.pawanputra.bos.platform.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OpportunityEvents {

    private OpportunityEvents() {
    }

    public record OpportunityCreatedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID opportunityId,
            UUID organizationId,
            String title,
            BigDecimal value,
            UUID customerId,
            UUID leadId,
            UUID ownerId) implements DomainEvent {

        public static OpportunityCreatedEvent of(
                UUID opportunityId, UUID organizationId, String title, BigDecimal value,
                UUID customerId, UUID leadId, UUID ownerId) {
            return new OpportunityCreatedEvent(
                    UUID.randomUUID(), Instant.now(), opportunityId, organizationId, title, value, customerId, leadId, ownerId);
        }
    }

    public record OpportunityStageChangedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID opportunityId,
            UUID organizationId,
            UUID fromStageId,
            String fromStageName,
            UUID toStageId,
            String toStageName,
            OpportunityStatus status) implements DomainEvent {

        public static OpportunityStageChangedEvent of(
                UUID opportunityId, UUID organizationId, UUID fromStageId, String fromStageName,
                UUID toStageId, String toStageName, OpportunityStatus status) {
            return new OpportunityStageChangedEvent(
                    UUID.randomUUID(), Instant.now(), opportunityId, organizationId, fromStageId, fromStageName, toStageId, toStageName, status);
        }
    }
}
