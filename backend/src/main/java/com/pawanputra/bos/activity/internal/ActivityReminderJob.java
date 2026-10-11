package com.pawanputra.bos.activity.internal;

import com.pawanputra.bos.activity.api.ActivityEvents;
import com.pawanputra.bos.activity.api.ReminderStatus;
import com.pawanputra.bos.platform.event.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically checks for due activity reminders and triggers them.
 */
@Component
public class ActivityReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ActivityReminderJob.class);

    private final ActivityRepository activities;
    private final ActivityReminderRepository reminders;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ActivityReminderJob(
            ActivityRepository activities,
            ActivityReminderRepository reminders,
            DomainEventPublisher events,
            Clock clock) {
        this.activities = activities;
        this.reminders = reminders;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.activities.reminders-cron:0 */1 * * * *}")
    @Transactional
    public void processDueReminders() {
        Instant now = clock.instant();
        List<Activity> due = activities.findDueReminders(ReminderStatus.PENDING, now);

        if (due.isEmpty()) {
            return;
        }

        log.info("Processing {} due activity reminders", due.size());
        for (Activity activity : due) {
            activity.setReminderStatus(ReminderStatus.TRIGGERED);

            List<ActivityReminder> records = reminders.findByActivityId(activity.getId());
            for (ActivityReminder r : records) {
                if ("PENDING".equals(r.getStatus()) && !r.getRemindAt().isAfter(now)) {
                    r.markSent(now);
                }
            }

            events.publish(ActivityEvents.ReminderTriggeredEvent.of(
                    activity.getId(),
                    activity.getOrganizationId(),
                    activity.getOwnerId(),
                    activity.getTitle(),
                    activity.getDueDate()));
        }
    }
}
