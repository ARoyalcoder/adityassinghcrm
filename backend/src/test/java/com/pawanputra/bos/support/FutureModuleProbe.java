package com.pawanputra.bos.support;

import com.pawanputra.bos.customer.api.CustomerHealthContributor;
import com.pawanputra.bos.customer.api.CustomerTimelineSource;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Plays the part of modules that do not exist yet (quotations, invoices, tickets, AMC) to prove that
 * such a module joins the customer timeline and health by declaring a bean, with no change to the
 * customer module. It holds its "records" in memory and answers exactly as a real source must.
 */
@Component
public class FutureModuleProbe implements CustomerTimelineSource, CustomerHealthContributor {

    private final Map<UUID, List<TimelineEvent>> eventsByCustomer = new ConcurrentHashMap<>();
    private final Map<UUID, List<Signal>> signalsByCustomer = new ConcurrentHashMap<>();

    public void addEvent(UUID customerId, String id, String type, TimelineCategory category, Instant occurredAt,
            String title, String description) {
        eventsByCustomer.computeIfAbsent(customerId, key -> new ArrayList<>()).add(new TimelineEvent(
                id, type, category, occurredAt, title, description, "Probe User",
                new TimelineEvent.Reference("ProbeRecord", id), Map.of("Amount", "₹1,85,000")));
    }

    public void addSignal(UUID customerId, Signal signal) {
        signalsByCustomer.computeIfAbsent(customerId, key -> new ArrayList<>()).add(signal);
    }

    @Override
    public Set<TimelineCategory> categories() {
        return Set.of(TimelineCategory.SALES, TimelineCategory.PROJECT, TimelineCategory.FINANCE,
                TimelineCategory.SERVICE, TimelineCategory.AMC);
    }

    @Override
    public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
        return eventsByCustomer.getOrDefault(customerId, List.of()).stream()
                .filter(event -> notAfter == null || !event.occurredAt().isAfter(notAfter))
                .sorted(Comparator.comparing(TimelineEvent::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<Signal> signals(UUID organizationId, UUID customerId) {
        return signalsByCustomer.getOrDefault(customerId, List.of());
    }
}
