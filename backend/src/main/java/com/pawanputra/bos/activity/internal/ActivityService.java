package com.pawanputra.bos.activity.internal;

import com.pawanputra.bos.activity.api.ActivityEvents;
import com.pawanputra.bos.activity.api.ActivityType;
import com.pawanputra.bos.activity.api.RecurrencePattern;
import com.pawanputra.bos.activity.api.ReminderStatus;
import com.pawanputra.bos.activity.api.TargetEntityType;
import com.pawanputra.bos.activity.api.TaskPriority;
import com.pawanputra.bos.activity.api.TaskStatus;
import com.pawanputra.bos.customer.api.InteractionDirection;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.identity.api.UserDirectory.UserSummary;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import com.pawanputra.bos.platform.persistence.Specs;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activities;
    private final ActivityReminderRepository reminders;
    private final UserDirectory users;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ActivityService(
            ActivityRepository activities,
            ActivityReminderRepository reminders,
            UserDirectory users,
            DomainEventPublisher events,
            Clock clock) {
        this.activities = activities;
        this.reminders = reminders;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    public record CreateCommand(
            ActivityType type,
            String title,
            String description,
            TargetEntityType entityType,
            UUID entityId,
            String entityName,
            UUID ownerId,
            TaskPriority priority,
            TaskStatus status,
            Instant dueDate,
            Instant occurredAt,
            InteractionDirection direction,
            Instant reminderAt,
            boolean isRecurring,
            RecurrencePattern recurrencePattern,
            Integer recurrenceInterval,
            Instant recurrenceEndAt) {
    }

    public record UpdateCommand(
            ActivityType type,
            String title,
            String description,
            TargetEntityType entityType,
            UUID entityId,
            String entityName,
            UUID ownerId,
            TaskPriority priority,
            TaskStatus status,
            Instant dueDate,
            Instant occurredAt,
            InteractionDirection direction,
            Instant reminderAt,
            boolean isRecurring,
            RecurrencePattern recurrencePattern,
            Integer recurrenceInterval,
            Instant recurrenceEndAt) {
    }

    public record CompleteCommand(String outcome) {
    }

    public record RescheduleCommand(Instant dueDate, Instant reminderAt) {
    }

    public record ActivityFilter(
            ActivityType type,
            TargetEntityType entityType,
            UUID entityId,
            UUID ownerId,
            TaskPriority priority,
            TaskStatus status,
            String view,
            String search) {
    }

    @Transactional
    public ActivityViews.Detail create(UUID organizationId, CreateCommand cmd, UUID actorId, String actorName) {
        UUID ownerId = cmd.ownerId() != null ? cmd.ownerId() : actorId;
        String ownerName = resolveUserName(organizationId, ownerId, actorName);

        Activity activity = new Activity(
                organizationId,
                cmd.type(),
                cmd.title(),
                cmd.description(),
                cmd.entityType(),
                cmd.entityId(),
                cmd.entityName(),
                ownerId,
                ownerName,
                cmd.priority(),
                cmd.status(),
                cmd.dueDate(),
                cmd.occurredAt() != null ? cmd.occurredAt() : clock.instant(),
                cmd.direction(),
                cmd.reminderAt(),
                cmd.isRecurring(),
                cmd.recurrencePattern(),
                cmd.recurrenceInterval(),
                cmd.recurrenceEndAt(),
                null);

        Activity saved = activities.save(activity);

        if (saved.getReminderAt() != null) {
            reminders.save(new ActivityReminder(
                    saved, organizationId, saved.getOwnerId(), saved.getReminderAt(), "IN_APP", actorName));
        }

        events.publish(ActivityEvents.ActivityCreatedEvent.of(
                saved.getId(),
                organizationId,
                saved.getType(),
                saved.getEntityType(),
                saved.getEntityId(),
                saved.getOwnerId(),
                saved.getTitle()));

        return ActivityViews.Detail.from(saved);
    }

    @Transactional
    public ActivityViews.Detail update(UUID organizationId, UUID activityId, UpdateCommand cmd, String actorName) {
        Activity activity = getEntity(organizationId, activityId);

        if (cmd.type() != null) {
            activity.setType(cmd.type());
        }
        if (cmd.title() != null) {
            activity.setTitle(cmd.title());
        }
        activity.setDescription(cmd.description());
        if (cmd.entityType() != null) {
            activity.setEntityType(cmd.entityType());
        }
        if (cmd.entityId() != null) {
            activity.setEntityId(cmd.entityId());
        }
        if (cmd.entityName() != null) {
            activity.setEntityName(cmd.entityName());
        }
        if (cmd.ownerId() != null && !cmd.ownerId().equals(activity.getOwnerId())) {
            activity.setOwnerId(cmd.ownerId());
            activity.setOwnerName(resolveUserName(organizationId, cmd.ownerId(), actorName));
        }
        if (cmd.priority() != null) {
            activity.setPriority(cmd.priority());
        }
        if (cmd.status() != null) {
            activity.setStatus(cmd.status());
        }
        activity.setDueDate(cmd.dueDate());
        if (cmd.occurredAt() != null) {
            activity.setOccurredAt(cmd.occurredAt());
        }
        activity.setDirection(cmd.direction());
        activity.setRecurring(cmd.isRecurring());
        activity.setRecurrencePattern(cmd.recurrencePattern());
        activity.setRecurrenceInterval(cmd.recurrenceInterval());
        activity.setRecurrenceEndAt(cmd.recurrenceEndAt());

        // Reminder updates
        Instant prevReminder = activity.getReminderAt();
        activity.setReminderAt(cmd.reminderAt());
        if (cmd.reminderAt() != null && !cmd.reminderAt().equals(prevReminder)) {
            activity.setReminderStatus(ReminderStatus.PENDING);
            reminders.save(new ActivityReminder(
                    activity, organizationId, activity.getOwnerId(), cmd.reminderAt(), "IN_APP", actorName));
        }

        return ActivityViews.Detail.from(activity);
    }

    @Transactional
    public ActivityViews.Detail complete(UUID organizationId, UUID activityId, CompleteCommand cmd, String actorName) {
        Activity activity = getEntity(organizationId, activityId);
        Instant now = clock.instant();

        activity.markCompleted(actorName, now, cmd.outcome());

        // Recurring task propagation: generate next occurrence if applicable
        if (activity.isRecurring() && activity.getRecurrencePattern() != null && activity.getDueDate() != null) {
            Optional<Instant> nextDue = ActivityRecurrenceCalculator.nextDueDate(
                    activity.getDueDate(),
                    activity.getRecurrencePattern(),
                    activity.getRecurrenceInterval(),
                    activity.getRecurrenceEndAt(),
                    now);

            if (nextDue.isPresent()) {
                Instant nextDueDate = nextDue.get();
                Instant nextReminder = null;
                if (activity.getReminderAt() != null) {
                    Duration offset = Duration.between(activity.getReminderAt(), activity.getDueDate());
                    nextReminder = nextDueDate.minus(offset);
                }

                Activity parent = activity.getParentActivity() != null ? activity.getParentActivity() : activity;

                Activity nextOccurrence = new Activity(
                        organizationId,
                        activity.getType(),
                        activity.getTitle(),
                        activity.getDescription(),
                        activity.getEntityType(),
                        activity.getEntityId(),
                        activity.getEntityName(),
                        activity.getOwnerId(),
                        activity.getOwnerName(),
                        activity.getPriority(),
                        TaskStatus.PENDING,
                        nextDueDate,
                        nextDueDate,
                        activity.getDirection(),
                        nextReminder,
                        true,
                        activity.getRecurrencePattern(),
                        activity.getRecurrenceInterval(),
                        activity.getRecurrenceEndAt(),
                        parent);

                Activity savedNext = activities.save(nextOccurrence);

                if (nextReminder != null) {
                    reminders.save(new ActivityReminder(
                            savedNext, organizationId, savedNext.getOwnerId(), nextReminder, "IN_APP", actorName));
                }
            }
        }

        events.publish(ActivityEvents.ActivityCompletedEvent.of(
                activity.getId(),
                organizationId,
                activity.getType(),
                activity.getEntityType(),
                activity.getEntityId(),
                activity.getOwnerId(),
                activity.getTitle(),
                cmd.outcome()));

        return ActivityViews.Detail.from(activity);
    }

    @Transactional
    public ActivityViews.Detail reschedule(UUID organizationId, UUID activityId, RescheduleCommand cmd, String actorName) {
        Activity activity = getEntity(organizationId, activityId);
        activity.setDueDate(cmd.dueDate());
        if (cmd.reminderAt() != null) {
            activity.setReminderAt(cmd.reminderAt());
            activity.setReminderStatus(ReminderStatus.PENDING);
            reminders.save(new ActivityReminder(
                    activity, organizationId, activity.getOwnerId(), cmd.reminderAt(), "IN_APP", actorName));
        }
        return ActivityViews.Detail.from(activity);
    }

    @Transactional
    public ActivityViews.Detail dismissReminder(UUID organizationId, UUID activityId) {
        Activity activity = getEntity(organizationId, activityId);
        activity.setReminderStatus(ReminderStatus.DISMISSED);
        return ActivityViews.Detail.from(activity);
    }

    @Transactional
    public void delete(UUID organizationId, UUID activityId) {
        Activity activity = getEntity(organizationId, activityId);
        activity.markDeleted(clock.instant());
    }

    public ActivityViews.Detail get(UUID organizationId, UUID activityId) {
        return ActivityViews.Detail.from(getEntity(organizationId, activityId));
    }

    public Page<ActivityViews.Detail> list(
            UUID organizationId, UUID currentUserId, ActivityFilter filter, Pageable pageable) {
        Specification<Activity> spec = buildSpecification(organizationId, currentUserId, filter);
        return activities.findAll(spec, pageable).map(ActivityViews.Detail::from);
    }

    public ActivityViews.Counts getCounts(UUID organizationId, UUID currentUserId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        long todaysWork = activities.countDueToday(organizationId, startOfDay, endOfDay);
        long myTasks = activities.countByOrganizationIdAndOwnerIdAndStatusNot(
                organizationId, currentUserId, TaskStatus.COMPLETED);
        long overdue = activities.countOverdue(organizationId, now);
        long upcoming = activities.countUpcoming(organizationId, endOfDay);
        long completed = activities.countByOrganizationIdAndStatus(organizationId, TaskStatus.COMPLETED);
        long totalOpen = overdue + todaysWork + upcoming;

        return new ActivityViews.Counts(todaysWork, myTasks, overdue, upcoming, completed, totalOpen);
    }

    private Activity getEntity(UUID organizationId, UUID activityId) {
        return activities.findByIdAndOrganizationId(activityId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));
    }

    private String resolveUserName(UUID organizationId, UUID userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        return users.find(organizationId, userId)
                .map(UserSummary::fullName)
                .orElse(fallback != null ? fallback : "Unassigned");
    }

    private Specification<Activity> buildSpecification(
            UUID organizationId, UUID currentUserId, ActivityFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));

            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.entityType() != null) {
                predicates.add(cb.equal(root.get("entityType"), filter.entityType()));
            }
            if (filter.entityId() != null) {
                predicates.add(cb.equal(root.get("entityId"), filter.entityId()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }

            Instant now = clock.instant();
            LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
            Instant startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

            // Handle named views
            String view = filter.view() != null ? filter.view().trim().toLowerCase() : "";
            switch (view) {
                case "todays-work" -> {
                    Predicate dueToday = cb.between(root.get("dueDate"), startOfDay, endOfDay);
                    Predicate occurredToday = cb.between(root.get("occurredAt"), startOfDay, endOfDay);
                    predicates.add(cb.or(dueToday, occurredToday));
                }
                case "my-tasks" -> {
                    predicates.add(cb.equal(root.get("ownerId"), currentUserId));
                    predicates.add(cb.notEqual(root.get("status"), TaskStatus.COMPLETED));
                    predicates.add(cb.notEqual(root.get("status"), TaskStatus.CANCELLED));
                }
                case "overdue" -> {
                    predicates.add(cb.lessThan(root.get("dueDate"), now));
                    predicates.add(cb.notEqual(root.get("status"), TaskStatus.COMPLETED));
                    predicates.add(cb.notEqual(root.get("status"), TaskStatus.CANCELLED));
                }
                case "upcoming" -> {
                    predicates.add(cb.greaterThan(root.get("dueDate"), endOfDay));
                    predicates.add(cb.notEqual(root.get("status"), TaskStatus.COMPLETED));
                    predicates.add(cb.notEqual(root.get("status"), TaskStatus.CANCELLED));
                }
                case "completed" -> predicates.add(cb.equal(root.get("status"), TaskStatus.COMPLETED));
                default -> {
                    if (filter.ownerId() != null) {
                        predicates.add(cb.equal(root.get("ownerId"), filter.ownerId()));
                    }
                    if (filter.status() != null) {
                        predicates.add(cb.equal(root.get("status"), filter.status()));
                    }
                }
            }

            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                Predicate matchTitle = cb.like(cb.lower(root.get("title")), pattern);
                Predicate matchDesc = cb.like(cb.lower(root.get("description")), pattern);
                Predicate matchEntity = cb.like(cb.lower(root.get("entityName")), pattern);
                Predicate matchOutcome = cb.like(cb.lower(root.get("outcome")), pattern);
                predicates.add(cb.or(matchTitle, matchDesc, matchEntity, matchOutcome));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
