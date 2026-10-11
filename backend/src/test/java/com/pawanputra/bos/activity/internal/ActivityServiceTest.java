package com.pawanputra.bos.activity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawanputra.bos.activity.api.ActivityType;
import com.pawanputra.bos.activity.api.RecurrencePattern;
import com.pawanputra.bos.activity.api.TargetEntityType;
import com.pawanputra.bos.activity.api.TaskPriority;
import com.pawanputra.bos.activity.api.TaskStatus;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActivityServiceTest {

    private ActivityRepository activities;
    private ActivityReminderRepository reminders;
    private UserDirectory users;
    private DomainEventPublisher events;
    private Clock clock;
    private ActivityService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-09T10:00:00Z");

    @BeforeEach
    void setUp() {
        activities = mock(ActivityRepository.class);
        reminders = mock(ActivityReminderRepository.class);
        users = mock(UserDirectory.class);
        events = mock(DomainEventPublisher.class);
        clock = Clock.fixed(now, ZoneOffset.UTC);

        service = new ActivityService(activities, reminders, users, events, clock);
    }

    @Test
    void createActivitySavesAndPublishesEvent() {
        when(users.find(orgId, userId))
                .thenReturn(Optional.of(new UserDirectory.UserSummary(userId, "Aditya Singh", "a@example.com", true)));
        when(activities.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivityService.CreateCommand cmd = new ActivityService.CreateCommand(
                ActivityType.TASK,
                "Follow up on quote",
                "Discuss pricing",
                TargetEntityType.CUSTOMER,
                UUID.randomUUID(),
                "Acme Corp",
                userId,
                TaskPriority.HIGH,
                TaskStatus.PENDING,
                now.plusSeconds(3600),
                now,
                null,
                now.plusSeconds(1800),
                false,
                null,
                null,
                null);

        ActivityViews.Detail created = service.create(orgId, cmd, userId, "Aditya Singh");

        assertThat(created.title()).isEqualTo("Follow up on quote");
        assertThat(created.ownerName()).isEqualTo("Aditya Singh");
        assertThat(created.priority()).isEqualTo(TaskPriority.HIGH);
        verify(activities).save(any(Activity.class));
        verify(reminders).save(any(ActivityReminder.class));
        verify(events).publish(any());
    }

    @Test
    void completeRecurringTaskSpawnsNextOccurrence() {
        Instant dueDate = now.plusSeconds(3600);
        Activity activity = new Activity(
                orgId,
                ActivityType.TASK,
                "Weekly Sync",
                "Review progress",
                TargetEntityType.PROJECT,
                UUID.randomUUID(),
                "Solar Project Alpha",
                userId,
                "Aditya Singh",
                TaskPriority.MEDIUM,
                TaskStatus.PENDING,
                dueDate,
                now,
                null,
                dueDate.minusSeconds(900),
                true,
                RecurrencePattern.WEEKLY,
                1,
                null,
                null);

        when(activities.findByIdAndOrganizationId(activity.getId(), orgId)).thenReturn(Optional.of(activity));
        when(activities.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        ActivityViews.Detail completed = service.complete(
                orgId, activity.getId(), new ActivityService.CompleteCommand("Completed sync"), "Aditya Singh");

        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.outcome()).isEqualTo("Completed sync");
        // Next occurrence saved
        verify(activities).save(any(Activity.class));
    }
}
