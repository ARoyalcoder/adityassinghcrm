package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.api.CustomerTimelineSource;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineCursor;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import com.pawanputra.bos.platform.timeline.TimelineMerger;
import com.pawanputra.bos.platform.timeline.TimelinePage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's unified timeline: everything that happened with the customer, from every module,
 * in one chronological list.
 *
 * <p>Nothing is stored for it. Each page is assembled from the {@link CustomerTimelineSource} beans
 * that exist at the time, so a module joins the timeline by declaring one, and an event can never
 * disagree with the record it describes.
 */
@Service
@Transactional(readOnly = true)
public class CustomerTimelineService {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 100;

    /**
     * With a category filter, a stretch of the timeline may contain nothing that matches. Rather
     * than hand the client an empty page with "more available", keep going a few rounds.
     */
    private static final int MAX_ROUNDS = 5;

    private final CustomerService customers;
    private final List<CustomerTimelineSource> sources;

    public CustomerTimelineService(CustomerService customers, List<CustomerTimelineSource> sources) {
        this.customers = customers;
        this.sources = sources;
    }

    /**
     * @param cursorToken where the previous page ended, or {@code null} for the newest events
     * @param categories  show only these; empty for everything
     */
    public TimelinePage timeline(
            UUID organizationId, UUID customerId, String cursorToken, int limit, Set<TimelineCategory> categories) {
        customers.load(organizationId, customerId); // 404 outside the caller's organization
        List<CustomerTimelineSource> relevant = sources.stream()
                .filter(source -> categories.isEmpty() || !Collections.disjoint(source.categories(), categories))
                .toList();

        List<TimelineEvent> collected = new ArrayList<>();
        String token = cursorToken;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            TimelineCursor cursor = TimelineCursor.decode(token);
            Instant notAfter = cursor == null ? null : cursor.occurredAt();
            int wanted = limit - collected.size();
            int fetch = TimelineMerger.fetchSize(wanted);
            List<List<TimelineEvent>> perSource = relevant.stream()
                    .map(source -> source.events(organizationId, customerId, notAfter, fetch))
                    .toList();
            TimelinePage page = TimelineMerger.merge(perSource, cursor, wanted, categories);
            collected.addAll(page.events());
            token = page.nextCursor();
            if (token == null || collected.size() >= limit) {
                break;
            }
        }
        return new TimelinePage(List.copyOf(collected), token);
    }
}
