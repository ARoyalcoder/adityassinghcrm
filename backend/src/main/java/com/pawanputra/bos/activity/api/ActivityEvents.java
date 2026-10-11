package com.pawanputra.bos.activity.api;

import com.pawanputra.bos.platform.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public final class ActivityEvents {

    private ActivityEvents() {
    }

    public record ActivityCreatedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID activityId,
            UUID organizationId,
            ActivityType type,
            TargetEntityType entityType,
            UUID entityId,
            UUID ownerId,
            String title) implements DomainEvent {

        public static ActivityCreatedEvent of(
                UUID activityId, UUID organizationId, ActivityType type,
                TargetEntityType entityType, UUID entityId, UUID ownerId, String title) {
            return new ActivityCreatedEvent(
                    UUID.randomUUID(), Instant.now(), activityId, organizationId, type, entityType, entityId, ownerId, title);
        }
    }

    public record ActivityCompletedEvent(
            UUID eventId,
            Instant occurredAt,
            UUID activityId,
            UUID organizationId,
            ActivityType type,
            TargetEntityType entityType,
            UUID entityId,
            UUID ownerId,
            String title,
            String outcome) implements DomainEvent {

        public static ActivityCompletedEvent of(
                UUID activityId, UUID organizationId, ActivityType type,
                TargetEntityType entityType, UUID entityId, UUID ownerId, String title, String outcome) {
            return new ActivityCompletedEvent(
                    UUID.randomUUID(), Instant.now(), activityId, organizationId, type, entityType, entityId, ownerId, title, outcome);
        }
    }

    public record ReminderTriggeredEvent(
            UUID eventId,
            Instant occurredAt,
            UUID activityId,
            UUID organizationId,
            UUID userId,
            String title,
            Instant dueDate) implements DomainEvent {

        public static ReminderTriggeredEvent of(
                UUID activityId, UUID organizationId, UUID userId, String title, Instant dueDate) {
            return new ReminderTriggeredEvent(
                    UUID.randomUUID(), Instant.now(), activityId, organizationId, userId, title, dueDate);
        }
    }
}
