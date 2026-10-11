package com.pawanputra.bos.project.api;

import com.pawanputra.bos.platform.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ProjectEvents {

    private ProjectEvents() {
    }

    public record ProjectCreatedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID projectId,
            UUID organizationId,
            String projectNumber,
            String title,
            UUID customerId,
            String customerName,
            UUID orderId,
            String orderNumber,
            UUID projectManagerId,
            BigDecimal budget,
            BigDecimal revenue,
            String status,
            String createdBy) implements DomainEvent {

        public static ProjectCreatedEvent of(
                UUID projectId,
                UUID organizationId,
                String projectNumber,
                String title,
                UUID customerId,
                String customerName,
                UUID orderId,
                String orderNumber,
                UUID projectManagerId,
                BigDecimal budget,
                BigDecimal revenue,
                String status,
                String createdBy) {
            return new ProjectCreatedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    projectId,
                    organizationId,
                    projectNumber,
                    title,
                    customerId,
                    customerName,
                    orderId,
                    orderNumber,
                    projectManagerId,
                    budget,
                    revenue,
                    status,
                    createdBy);
        }
    }

    public record ProjectStatusChangedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID projectId,
            UUID organizationId,
            String projectNumber,
            String fromStatus,
            String toStatus,
            String notes,
            String changedBy) implements DomainEvent {

        public static ProjectStatusChangedEvent of(
                UUID projectId,
                UUID organizationId,
                String projectNumber,
                String fromStatus,
                String toStatus,
                String notes,
                String changedBy) {
            return new ProjectStatusChangedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    projectId,
                    organizationId,
                    projectNumber,
                    fromStatus,
                    toStatus,
                    notes,
                    changedBy);
        }
    }

    public record MilestoneCompletedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID milestoneId,
            UUID projectId,
            UUID organizationId,
            String milestoneTitle,
            String completedBy) implements DomainEvent {

        public static MilestoneCompletedEvent of(
                UUID milestoneId,
                UUID projectId,
                UUID organizationId,
                String milestoneTitle,
                String completedBy) {
            return new MilestoneCompletedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    milestoneId,
                    projectId,
                    organizationId,
                    milestoneTitle,
                    completedBy);
        }
    }
}
