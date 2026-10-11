package com.pawanputra.bos.quotation.api;

import com.pawanputra.bos.platform.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class QuotationEvents {

    private QuotationEvents() {
    }

    public record QuotationCreatedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID quotationId,
            UUID organizationId,
            String quotationNumber,
            String title,
            BigDecimal finalAmount,
            UUID customerId,
            UUID leadId,
            UUID opportunityId,
            UUID ownerId) implements DomainEvent {

        public static QuotationCreatedEvent of(
                UUID quotationId, UUID organizationId, String quotationNumber, String title,
                BigDecimal finalAmount, UUID customerId, UUID leadId, UUID opportunityId, UUID ownerId) {
            return new QuotationCreatedEvent(
                    UUID.randomUUID(), Instant.now(), quotationId, organizationId, quotationNumber,
                    title, finalAmount, customerId, leadId, opportunityId, ownerId);
        }
    }

    public record QuotationStatusChangedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID quotationId,
            UUID organizationId,
            String quotationNumber,
            QuotationStatus fromStatus,
            QuotationStatus toStatus,
            int versionNumber,
            String actor) implements DomainEvent {

        public static QuotationStatusChangedEvent of(
                UUID quotationId, UUID organizationId, String quotationNumber,
                QuotationStatus fromStatus, QuotationStatus toStatus, int versionNumber, String actor) {
            return new QuotationStatusChangedEvent(
                    UUID.randomUUID(), Instant.now(), quotationId, organizationId, quotationNumber,
                    fromStatus, toStatus, versionNumber, actor);
        }
    }

    public record QuotationVersionCreatedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID quotationId,
            UUID organizationId,
            String quotationNumber,
            int newVersionNumber,
            BigDecimal finalAmount,
            String changeSummary,
            String createdBy) implements DomainEvent {

        public static QuotationVersionCreatedEvent of(
                UUID quotationId, UUID organizationId, String quotationNumber,
                int newVersionNumber, BigDecimal finalAmount, String changeSummary, String createdBy) {
            return new QuotationVersionCreatedEvent(
                    UUID.randomUUID(), Instant.now(), quotationId, organizationId, quotationNumber,
                    newVersionNumber, finalAmount, changeSummary, createdBy);
        }
    }
}
