package com.pawanputra.bos.customer.api;

import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * How a module puts its events on the customer timeline. Declare a Spring bean of this type and the
 * customer 360 picks it up; nothing in the customer module changes.
 *
 * <pre>{@code
 * @Component
 * class QuotationTimelineSource implements CustomerTimelineSource {
 *     public Set<TimelineCategory> categories() { return Set.of(TimelineCategory.SALES); }
 *
 *     public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
 *         return quotations.findSentTo(customerId, notAfter, limit).stream()
 *                 .map(q -> new TimelineEvent("quotation-sent:" + q.getId(), TimelineEventTypes.QUOTATION_SENT,
 *                         TimelineCategory.SALES, q.getSentAt(), "Quotation sent", q.getNumber(), q.getSentByName(),
 *                         new TimelineEvent.Reference("Quotation", q.getId().toString()), null))
 *                 .toList();
 *     }
 * }
 * }</pre>
 *
 * <p>Rules for an implementation:
 * <ul>
 *   <li><b>Read your own records.</b> Build the events from your tables on each call. Do not store
 *       timeline rows anywhere: the record is the single source of truth.</li>
 *   <li><b>Stable ids.</b> The same record must give the same event id every time, in the form
 *       {@code "<source>:<record id>"}. One record that produces two events (sent, accepted) uses
 *       two prefixes.</li>
 *   <li><b>Newest first, bounded.</b> Return at most {@code limit} events with
 *       {@code occurredAt <= notAfter}, newest first. This needs an index on (customer, time).</li>
 *   <li><b>Check the organization.</b> Return nothing for a customer outside {@code organizationId}.</li>
 * </ul>
 */
public interface CustomerTimelineSource {

    /** The categories this source can produce, so it is not queried when the user has filtered them out. */
    Set<TimelineCategory> categories();

    /**
     * @param notAfter only events at or before this moment; {@code null} for "up to now"
     * @param limit    the most events to return
     */
    List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit);
}
