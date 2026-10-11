package com.pawanputra.bos.activity.internal;

import com.pawanputra.bos.activity.api.ActivityType;
import com.pawanputra.bos.activity.api.TargetEntityType;
import com.pawanputra.bos.activity.api.TaskStatus;
import com.pawanputra.bos.customer.api.CustomerTimelineSource;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import com.pawanputra.bos.platform.timeline.TimelineEventTypes;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class ActivityTimelineSource implements CustomerTimelineSource {

    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final ActivityRepository activities;

    public ActivityTimelineSource(ActivityRepository activities) {
        this.activities = activities;
    }

    @Override
    public Set<TimelineCategory> categories() {
        return Set.of(TimelineCategory.COMMUNICATION, TimelineCategory.RECORD);
    }

    @Override
    public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
        Instant upTo = notAfter != null ? notAfter : FAR_FUTURE;
        return activities.findByEntityTypeAndEntityIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
                        TargetEntityType.CUSTOMER, customerId, upTo, PageRequest.of(0, limit))
                .stream()
                .filter(a -> a.getOrganizationId().equals(organizationId))
                .map(ActivityTimelineSource::toEvent)
                .toList();
    }

    private static TimelineEvent toEvent(Activity activity) {
        Map<String, String> details = new LinkedHashMap<>();
        if (activity.getOutcome() != null) {
            details.put("Outcome", activity.getOutcome());
        }
        if (activity.getDueDate() != null) {
            details.put("Due", activity.getDueDate().toString());
        }
        if (activity.getPriority() != null) {
            details.put("Priority", activity.getPriority().name());
        }
        if (activity.getStatus() != null) {
            details.put("Status", activity.getStatus().name());
        }

        ActivityType type = activity.getType();
        String eventType = switch (type) {
            case CALL -> TimelineEventTypes.CALL_LOGGED;
            case MEETING -> TimelineEventTypes.MEETING_COMPLETED;
            case EMAIL -> TimelineEventTypes.EMAIL_LOGGED;
            case SITE_VISIT -> TimelineEventTypes.SITE_VISIT_COMPLETED;
            case NOTE -> TimelineEventTypes.NOTE_ADDED;
            case WHATSAPP -> TimelineEventTypes.MESSAGE_LOGGED;
            case FOLLOW_UP -> "FOLLOW_UP_SCHEDULED";
            case TASK -> activity.getStatus() == TaskStatus.COMPLETED ? "TASK_COMPLETED" : "TASK_CREATED";
        };

        String title = switch (type) {
            case CALL -> "Call logged";
            case MEETING -> "Meeting completed";
            case EMAIL -> "Email logged";
            case SITE_VISIT -> "Site visit completed";
            case NOTE -> "Note added";
            case WHATSAPP -> "WhatsApp message logged";
            case FOLLOW_UP -> "Follow-up scheduled";
            case TASK -> activity.getStatus() == TaskStatus.COMPLETED ? "Task completed" : "Task created";
        };

        return new TimelineEvent(
                "activity:" + activity.getId(),
                eventType,
                (type == ActivityType.TASK) ? TimelineCategory.RECORD : TimelineCategory.COMMUNICATION,
                activity.getOccurredAt(),
                title,
                activity.getTitle(),
                activity.getOwnerName(),
                new TimelineEvent.Reference("Activity", activity.getId().toString()),
                details.isEmpty() ? null : details);
    }
}
