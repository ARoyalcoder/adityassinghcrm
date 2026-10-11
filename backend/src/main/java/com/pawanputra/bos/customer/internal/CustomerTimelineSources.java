package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.audit.api.ActivityFeed;
import com.pawanputra.bos.audit.api.ActivityFeed.RecordedChange;
import com.pawanputra.bos.audit.api.AuditAction;
import com.pawanputra.bos.customer.api.CustomerTimelineSource;
import com.pawanputra.bos.customer.api.InteractionDirection;
import com.pawanputra.bos.customer.api.InteractionType;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import com.pawanputra.bos.platform.timeline.TimelineEventTypes;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;

/**
 * The timeline sources the customer module itself provides. They are ordinary
 * {@link CustomerTimelineSource} beans, registered exactly as another module would register its own.
 *
 * <p>Callers have already checked that the customer belongs to the organization, and each query
 * below is by customer id, so a source cannot return another organization's events.
 */
@Configuration
class CustomerTimelineSources {

    /** Stands in for "no upper bound" where a query needs a value. */
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    /** Calls, meetings, emails and visits, from the interaction log. */
    @Bean
    CustomerTimelineSource interactionTimelineSource(CustomerInteractionRepository interactions) {
        return source(Set.of(TimelineCategory.COMMUNICATION), (organizationId, customerId, notAfter, limit) ->
                interactions.findByCustomerIdAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
                                customerId, upTo(notAfter), PageRequest.of(0, limit))
                        .stream().map(CustomerTimelineSources::interactionEvent).toList());
    }

    /** Notes, from the notes themselves (so the timeline shows what was written, not just that something was). */
    @Bean
    CustomerTimelineSource noteTimelineSource(CustomerNoteRepository notes) {
        return source(Set.of(TimelineCategory.COMMUNICATION), (organizationId, customerId, notAfter, limit) ->
                notes.findByCustomerIdAndCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(
                                customerId, upTo(notAfter), PageRequest.of(0, limit))
                        .stream()
                        .map(note -> TimelineEvent.of("note:" + note.getId(), TimelineEventTypes.NOTE_ADDED,
                                TimelineCategory.COMMUNICATION, note.getCreatedAt(), "Note added", note.getBody(),
                                note.getAuthorName()))
                        .toList());
    }

    /** Changes to the customer record and its contacts and addresses, from the audit trail. */
    @Bean
    CustomerTimelineSource recordChangeTimelineSource(ActivityFeed activityFeed) {
        return source(Set.of(TimelineCategory.RECORD), (organizationId, customerId, notAfter, limit) ->
                // Notes reach the timeline with their text from noteTimelineSource; their audit
                // entries are excluded in the query rather than shown a second time.
                activityFeed.changesUpTo(
                                organizationId, "Customer", customerId.toString(), notAfter, limit, Set.of("Note"))
                        .stream().map(CustomerTimelineSources::recordEvent).toList());
    }

    private static TimelineEvent interactionEvent(CustomerInteraction interaction) {
        Map<String, String> details = new LinkedHashMap<>();
        if (interaction.getDirection() != null) {
            details.put("Direction", interaction.getDirection() == InteractionDirection.INBOUND ? "Incoming" : "Outgoing");
        }
        if (interaction.getOutcome() != null) {
            details.put("Outcome", interaction.getOutcome());
        }
        InteractionType type = interaction.getType();
        return new TimelineEvent(
                "interaction:" + interaction.getId(),
                switch (type) {
                    case CALL -> TimelineEventTypes.CALL_LOGGED;
                    case MEETING -> TimelineEventTypes.MEETING_COMPLETED;
                    case EMAIL -> TimelineEventTypes.EMAIL_LOGGED;
                    case SITE_VISIT -> TimelineEventTypes.SITE_VISIT_COMPLETED;
                    case MESSAGE -> TimelineEventTypes.MESSAGE_LOGGED;
                    case OTHER -> TimelineEventTypes.INTERACTION_LOGGED;
                },
                TimelineCategory.COMMUNICATION,
                interaction.getOccurredAt(),
                switch (type) {
                    case CALL -> "Call logged";
                    case MEETING -> "Meeting completed";
                    case EMAIL -> "Email logged";
                    case SITE_VISIT -> "Site visit completed";
                    case MESSAGE -> "Message logged";
                    case OTHER -> "Interaction logged";
                },
                interaction.getSubject(),
                interaction.getLoggedByName(),
                new TimelineEvent.Reference("Interaction", interaction.getId().toString()),
                details.isEmpty() ? null : details);
    }

    /** Turns an audit entry about the customer, or one of its parts, into a timeline event. */
    private static TimelineEvent recordEvent(RecordedChange change) {
        String entity = change.changedEntityType() == null ? "Customer" : change.changedEntityType();
        AuditAction action = change.action();
        String type;
        String title;
        switch (entity) {
            case "Customer" -> {
                switch (action) {
                    case CREATE -> {
                        type = TimelineEventTypes.CUSTOMER_CREATED;
                        title = "Customer created";
                    }
                    case STATUS_CHANGE -> {
                        type = TimelineEventTypes.CUSTOMER_STATUS_CHANGED;
                        title = "Status changed";
                    }
                    default -> {
                        type = TimelineEventTypes.CUSTOMER_UPDATED;
                        title = "Details updated";
                    }
                }
            }
            case "Contact" -> {
                type = partType(action, TimelineEventTypes.CONTACT_ADDED, TimelineEventTypes.CONTACT_UPDATED,
                        TimelineEventTypes.CONTACT_REMOVED);
                title = partTitle(action, "Contact");
            }
            case "Address" -> {
                type = partType(action, TimelineEventTypes.ADDRESS_ADDED, TimelineEventTypes.ADDRESS_UPDATED,
                        TimelineEventTypes.ADDRESS_REMOVED);
                title = partTitle(action, "Address");
            }
            default -> {
                // A kind of part added later that this mapping does not name yet: still shown.
                type = TimelineEventTypes.CUSTOMER_UPDATED;
                title = entity + " changed";
            }
        }
        return TimelineEvent.of("record:" + change.id(), type, TimelineCategory.RECORD, change.occurredAt(), title,
                change.message(), change.actorLabel());
    }

    private static String partType(AuditAction action, String added, String updated, String removed) {
        return switch (action) {
            case CREATE -> added;
            case DELETE -> removed;
            default -> updated;
        };
    }

    private static String partTitle(AuditAction action, String what) {
        return switch (action) {
            case CREATE -> what + " added";
            case DELETE -> what + " removed";
            default -> what + " updated";
        };
    }

    private static Instant upTo(Instant notAfter) {
        return notAfter == null ? FAR_FUTURE : notAfter;
    }

    private interface Fetch {
        List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit);
    }

    private static CustomerTimelineSource source(Set<TimelineCategory> categories, Fetch fetch) {
        return new CustomerTimelineSource() {
            @Override
            public Set<TimelineCategory> categories() {
                return categories;
            }

            @Override
            public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
                return fetch.events(organizationId, customerId, notAfter, limit);
            }
        };
    }
}
