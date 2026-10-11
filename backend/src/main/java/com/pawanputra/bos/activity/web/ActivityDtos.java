package com.pawanputra.bos.activity.web;

import com.pawanputra.bos.activity.api.ActivityType;
import com.pawanputra.bos.activity.api.RecurrencePattern;
import com.pawanputra.bos.activity.api.TargetEntityType;
import com.pawanputra.bos.activity.api.TaskPriority;
import com.pawanputra.bos.activity.api.TaskStatus;
import com.pawanputra.bos.activity.internal.ActivityService;
import com.pawanputra.bos.customer.api.InteractionDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class ActivityDtos {

    private ActivityDtos() {
    }

    record CreateActivityRequest(
            @NotNull ActivityType type,
            @NotBlank @Size(max = 250) String title,
            @Size(max = 4000) String description,
            @NotNull TargetEntityType entityType,
            @NotNull UUID entityId,
            @Size(max = 255) String entityName,
            UUID ownerId,
            TaskPriority priority,
            TaskStatus status,
            Instant dueDate,
            Instant occurredAt,
            InteractionDirection direction,
            Instant reminderAt,
            Boolean isRecurring,
            RecurrencePattern recurrencePattern,
            Integer recurrenceInterval,
            Instant recurrenceEndAt) {

        ActivityService.CreateCommand toCommand() {
            return new ActivityService.CreateCommand(
                    type,
                    title.trim(),
                    description != null ? description.trim() : null,
                    entityType,
                    entityId,
                    entityName != null ? entityName.trim() : null,
                    ownerId,
                    priority != null ? priority : TaskPriority.MEDIUM,
                    status != null ? status : TaskStatus.PENDING,
                    dueDate,
                    occurredAt,
                    direction,
                    reminderAt,
                    Boolean.TRUE.equals(isRecurring),
                    recurrencePattern,
                    recurrenceInterval != null ? recurrenceInterval : 1,
                    recurrenceEndAt);
        }
    }

    record UpdateActivityRequest(
            ActivityType type,
            @Size(max = 250) String title,
            @Size(max = 4000) String description,
            TargetEntityType entityType,
            UUID entityId,
            @Size(max = 255) String entityName,
            UUID ownerId,
            TaskPriority priority,
            TaskStatus status,
            Instant dueDate,
            Instant occurredAt,
            InteractionDirection direction,
            Instant reminderAt,
            Boolean isRecurring,
            RecurrencePattern recurrencePattern,
            Integer recurrenceInterval,
            Instant recurrenceEndAt) {

        ActivityService.UpdateCommand toCommand() {
            return new ActivityService.UpdateCommand(
                    type,
                    title != null ? title.trim() : null,
                    description != null ? description.trim() : null,
                    entityType,
                    entityId,
                    entityName != null ? entityName.trim() : null,
                    ownerId,
                    priority,
                    status,
                    dueDate,
                    occurredAt,
                    direction,
                    reminderAt,
                    Boolean.TRUE.equals(isRecurring),
                    recurrencePattern,
                    recurrenceInterval,
                    recurrenceEndAt);
        }
    }

    record CompleteActivityRequest(@Size(max = 1000) String outcome) {
        ActivityService.CompleteCommand toCommand() {
            return new ActivityService.CompleteCommand(outcome != null ? outcome.trim() : null);
        }
    }

    record RescheduleActivityRequest(@NotNull Instant dueDate, Instant reminderAt) {
        ActivityService.RescheduleCommand toCommand() {
            return new ActivityService.RescheduleCommand(dueDate, reminderAt);
        }
    }
}
