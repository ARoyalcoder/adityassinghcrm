package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.customer.api.CustomerTimelineSource;
import com.pawanputra.bos.opportunity.api.OpportunityStatus;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import com.pawanputra.bos.platform.timeline.TimelineEventTypes;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OpportunityTimelineSource implements CustomerTimelineSource {

    private final OpportunityRepository opportunities;

    public OpportunityTimelineSource(OpportunityRepository opportunities) {
        this.opportunities = opportunities;
    }

    @Override
    public Set<TimelineCategory> categories() {
        return Set.of(TimelineCategory.SALES);
    }

    @Override
    public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
        return opportunities.findByOrganizationIdAndCustomerId(organizationId, customerId).stream()
                .filter(o -> notAfter == null || !o.getCreatedAt().isAfter(notAfter))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .map(OpportunityTimelineSource::toEvent)
                .toList();
    }

    private static TimelineEvent toEvent(Opportunity o) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Stage", o.getStage().getName());
        details.put("Value", "₹" + o.getValue().toPlainString());
        details.put("Probability", o.getProbability() + "%");
        if (o.getStatus() == OpportunityStatus.WON) {
            details.put("Status", "Won");
        } else if (o.getStatus() == OpportunityStatus.LOST) {
            details.put("Status", "Lost");
            if (o.getLossReason() != null) {
                details.put("Reason", o.getLossReason());
            }
        }

        String eventType = o.getStatus() == OpportunityStatus.WON
                ? TimelineEventTypes.ORDER_CREATED
                : "OPPORTUNITY_UPDATED";

        String title = o.getStatus() == OpportunityStatus.WON
                ? "Opportunity won: " + o.getTitle()
                : (o.getStatus() == OpportunityStatus.LOST ? "Opportunity lost: " + o.getTitle() : "Opportunity: " + o.getTitle());

        return new TimelineEvent(
                "opportunity:" + o.getId(),
                eventType,
                TimelineCategory.SALES,
                o.getCreatedAt(),
                title,
                o.getStage().getName() + " · ₹" + o.getValue().toPlainString(),
                o.getOwnerName(),
                new TimelineEvent.Reference("Opportunity", o.getId().toString()),
                details);
    }
}
