package com.pawanputra.bos.order.internal;

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
public class OrderTimelineSource implements CustomerTimelineSource {

    private final OrderRepository orders;

    public OrderTimelineSource(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public Set<TimelineCategory> categories() {
        return Set.of(TimelineCategory.SALES);
    }

    @Override
    public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
        Instant cutoff = notAfter != null ? notAfter : Instant.now();
        List<Order> list = orders.findByOrganizationIdAndCustomerId(organizationId, customerId);

        List<TimelineEvent> events = new ArrayList<>();
        for (Order o : list) {
            if (o.getDeletedAt() == null && !o.getCreatedAt().isAfter(cutoff)) {
                events.add(new TimelineEvent(
                        "order-created:" + o.getId(),
                        TimelineEventTypes.ORDER_CREATED,
                        TimelineCategory.SALES,
                        o.getCreatedAt(),
                        "Order confirmed: " + o.getOrderNumber(),
                        o.getTitle() + " (INR " + o.getFinalAmount() + ") - Status: " + o.getStatus(),
                        o.getCreatedBy(),
                        new TimelineEvent.Reference("Order", o.getId().toString()),
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
