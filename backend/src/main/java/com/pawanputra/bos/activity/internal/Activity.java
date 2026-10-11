package com.pawanputra.bos.activity.internal;

import com.pawanputra.bos.activity.api.ActivityType;
import com.pawanputra.bos.activity.api.RecurrencePattern;
import com.pawanputra.bos.activity.api.ReminderStatus;
import com.pawanputra.bos.activity.api.TargetEntityType;
import com.pawanputra.bos.activity.api.TaskPriority;
import com.pawanputra.bos.activity.api.TaskStatus;
import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.customer.api.InteractionDirection;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Audited(module = "activities", entity = "Activity", label = "title")
@Table(name = "activities")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class Activity extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ActivityType type;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private TargetEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

    @Column(name = "outcome", length = 1000)
    private String outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10)
    private InteractionDirection direction;

    @Column(name = "reminder_at")
    private Instant reminderAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_status", nullable = false, length = 20)
    private ReminderStatus reminderStatus = ReminderStatus.NONE;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_pattern", length = 20)
    private RecurrencePattern recurrencePattern;

    @Column(name = "recurrence_interval")
    private Integer recurrenceInterval = 1;

    @Column(name = "recurrence_end_at")
    private Instant recurrenceEndAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_activity_id")
    private Activity parentActivity;

    protected Activity() {
        // JPA
    }

    public Activity(
            UUID organizationId,
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
            InteractionDirection direction,
            Instant reminderAt,
            boolean recurring,
            RecurrencePattern recurrencePattern,
            Integer recurrenceInterval,
            Instant recurrenceEndAt,
            Activity parentActivity) {
        this.organizationId = organizationId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityName = entityName;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.priority = priority != null ? priority : TaskPriority.MEDIUM;
        this.status = status != null ? status : TaskStatus.PENDING;
        this.dueDate = dueDate;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.direction = direction;
        this.reminderAt = reminderAt;
        this.reminderStatus = reminderAt != null ? ReminderStatus.PENDING : ReminderStatus.NONE;
        this.recurring = recurring;
        this.recurrencePattern = recurrencePattern;
        this.recurrenceInterval = recurrenceInterval != null ? recurrenceInterval : 1;
        this.recurrenceEndAt = recurrenceEndAt;
        this.parentActivity = parentActivity;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public ActivityType getType() {
        return type;
    }

    public void setType(ActivityType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TargetEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(TargetEntityType entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(String completedBy) {
        this.completedBy = completedBy;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public InteractionDirection getDirection() {
        return direction;
    }

    public void setDirection(InteractionDirection direction) {
        this.direction = direction;
    }

    public Instant getReminderAt() {
        return reminderAt;
    }

    public void setReminderAt(Instant reminderAt) {
        this.reminderAt = reminderAt;
        if (reminderAt != null && (this.reminderStatus == null || this.reminderStatus == ReminderStatus.NONE)) {
            this.reminderStatus = ReminderStatus.PENDING;
        } else if (reminderAt == null) {
            this.reminderStatus = ReminderStatus.NONE;
        }
    }

    public ReminderStatus getReminderStatus() {
        return reminderStatus;
    }

    public void setReminderStatus(ReminderStatus reminderStatus) {
        this.reminderStatus = reminderStatus;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public RecurrencePattern getRecurrencePattern() {
        return recurrencePattern;
    }

    public void setRecurrencePattern(RecurrencePattern recurrencePattern) {
        this.recurrencePattern = recurrencePattern;
    }

    public Integer getRecurrenceInterval() {
        return recurrenceInterval;
    }

    public void setRecurrenceInterval(Integer recurrenceInterval) {
        this.recurrenceInterval = recurrenceInterval;
    }

    public Instant getRecurrenceEndAt() {
        return recurrenceEndAt;
    }

    public void setRecurrenceEndAt(Instant recurrenceEndAt) {
        this.recurrenceEndAt = recurrenceEndAt;
    }

    public Activity getParentActivity() {
        return parentActivity;
    }

    public void setParentActivity(Activity parentActivity) {
        this.parentActivity = parentActivity;
    }

    public void markCompleted(String byUser, Instant when, String outcomeNote) {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = when;
        this.completedBy = byUser;
        if (outcomeNote != null && !outcomeNote.isBlank()) {
            this.outcome = outcomeNote;
        }
        if (this.reminderStatus == ReminderStatus.PENDING) {
            this.reminderStatus = ReminderStatus.DISMISSED;
        }
    }
}
