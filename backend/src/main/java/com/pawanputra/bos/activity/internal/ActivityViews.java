package com.pawanputra.bos.activity.internal;

import com.pawanputra.bos.activity.api.ActivityType;
import com.pawanputra.bos.activity.api.RecurrencePattern;
import com.pawanputra.bos.activity.api.ReminderStatus;
import com.pawanputra.bos.activity.api.TargetEntityType;
import com.pawanputra.bos.activity.api.TaskPriority;
import com.pawanputra.bos.activity.api.TaskStatus;
import com.pawanputra.bos.customer.api.InteractionDirection;
import java.time.Instant;
import java.util.UUID;

public final class ActivityViews {

    private ActivityViews() {
    }

    public record Detail(
            UUID id,
            ActivityType type,
            String title,
            String description,
            TargetEntityType entityType,
            UUID entityId,
            String entityName,
            UUID ownerId,
            String ownerName,
            TaskPriority priority,
            TaskStatus status,
            Instant dueDate,
            Instant occurredAt,
            Instant completedAt,
            String completedBy,
            String outcome,
            InteractionDirection direction,
            Instant reminderAt,
            ReminderStatus reminderStatus,
            boolean isRecurring,
            RecurrencePattern recurrencePattern,
            Integer recurrenceInterval,
            Instant recurrenceEndAt,
            UUID parentActivityId,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            long version) {

        public static Detail from(Activity a) {
            return new Detail(
                    a.getId(),
                    a.getType(),
                    a.getTitle(),
                    a.getDescription(),
                    a.getEntityType(),
                    a.getEntityId(),
                    a.getEntityName(),
                    a.getOwnerId(),
                    a.getOwnerName(),
                    a.getPriority(),
                    a.getStatus(),
                    a.getDueDate(),
                    a.getOccurredAt(),
                    a.getCompletedAt(),
                    a.getCompletedBy(),
                    a.getOutcome(),
                    a.getDirection(),
                    a.getReminderAt(),
                    a.getReminderStatus(),
                    a.isRecurring(),
                    a.getRecurrencePattern(),
                    a.getRecurrenceInterval(),
                    a.getRecurrenceEndAt(),
                    a.getParentActivity() != null ? a.getParentActivity().getId() : null,
                    a.getCreatedAt(),
                    a.getCreatedBy(),
                    a.getUpdatedAt(),
                    a.getVersion());
        }
    }

    public record Counts(
            long todaysWorkCount,
            long myTasksCount,
            long overdueCount,
            long upcomingCount,
            long completedCount,
            long totalOpenCount) {
    }
}
