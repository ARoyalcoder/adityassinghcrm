package com.pawanputra.bos.quotation.internal;

import com.pawanputra.bos.customer.api.CustomerTimelineSource;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import com.pawanputra.bos.platform.timeline.TimelineEventTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class QuotationTimelineSource implements CustomerTimelineSource {

    private final QuotationRepository quotations;

    public QuotationTimelineSource(QuotationRepository quotations) {
        this.quotations = quotations;
    }

    @Override
    public Set<TimelineCategory> categories() {
        return Set.of(TimelineCategory.SALES);
    }

    @Override
    public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
        Instant cutoff = notAfter != null ? notAfter : Instant.now();
        List<Quotation> list = quotations.findByOrganizationIdAndCustomerId(organizationId, customerId);

        List<TimelineEvent> events = new ArrayList<>();
        for (Quotation q : list) {
            if (q.getSentAt() != null && !q.getSentAt().isAfter(cutoff)) {
                events.add(new TimelineEvent(
                        "quotation-sent:" + q.getId(),
                        TimelineEventTypes.QUOTATION_SENT,
                        TimelineCategory.SALES,
                        q.getSentAt(),
                        "Quotation sent to client",
                        q.getQuotationNumber() + " - " + q.getTitle() + " (INR " + q.getFinalAmount() + ")",
                        q.getSentBy(),
                        new TimelineEvent.Reference("Quotation", q.getId().toString()),
                        null));
            }

            if (q.getAcceptedAt() != null && !q.getAcceptedAt().isAfter(cutoff)) {
                events.add(new TimelineEvent(
                        "quotation-accepted:" + q.getId(),
                        TimelineEventTypes.QUOTATION_ACCEPTED,
                        TimelineCategory.SALES,
                        q.getAcceptedAt(),
                        "Quotation accepted by client",
                        q.getQuotationNumber() + " - " + q.getTitle() + " (INR " + q.getFinalAmount() + ")",
                        q.getAcceptedBy(),
                        new TimelineEvent.Reference("Quotation", q.getId().toString()),
                        null));
            }
        }

        events.sort((a, b) -> b.occurredAt().compareTo(a.occurredAt()));
        if (events.size() > limit) {
            return events.subList(0, limit);
        }
        return events;
    }
}
