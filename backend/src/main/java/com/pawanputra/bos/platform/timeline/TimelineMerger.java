package com.pawanputra.bos.platform.timeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges events from several sources into one newest-first timeline, one page at a time.
 *
 * <p>How a caller uses it for one page of {@code limit} events:
 * <ol>
 *   <li>ask every source for its newest {@link #fetchSize(int)} events not after the cursor's time;</li>
 *   <li>pass the results to {@link #merge}.</li>
 * </ol>
 * No source needs to know about the others, and no event is stored anywhere.
 *
 * <p><b>Why a page never skips an event.</b> A source that returned as many events as it was asked
 * for may have more, older than the last one it returned. Until those are fetched, nothing older than
 * that point can be shown, because the unfetched events might belong in between. So the merge only
 * emits events down to the <i>horizon</i>: the newest such cut-off among the sources. Everything
 * below the horizon is left for the next page, which fetches it again.
 */
public final class TimelineMerger {

    /**
     * Extra events requested beyond the page size. A source is asked for "not after the cursor's
     * time", which re-includes events sharing the cursor's exact timestamp that were already shown;
     * the margin makes sure a full page of genuinely older events still comes back.
     */
    private static final int TIE_MARGIN = 20;

    /** Newest first; the id makes the order total, so paging never skips or repeats an event. */
    private static final Comparator<TimelineEvent> NEWEST_FIRST = Comparator
            .comparing(TimelineEvent::occurredAt).reversed()
            .thenComparing(Comparator.comparing(TimelineEvent::id).reversed());

    private TimelineMerger() {
    }

    /** How many events to request from each source for a page of {@code limit}. */
    public static int fetchSize(int limit) {
        return limit + 1 + TIE_MARGIN;
    }

    /**
     * @param perSource  what each source returned: its newest events at or before the cursor's time,
     *                   at most {@link #fetchSize(int)} of them
     * @param cursor     where the previous page ended, or {@code null} for the first page
     * @param categories keep only these; empty or {@code null} keeps everything
     */
    public static TimelinePage merge(
            Collection<List<TimelineEvent>> perSource, TimelineCursor cursor, int limit,
            Set<TimelineCategory> categories) {
        int requested = fetchSize(limit);

        // The newest point below which some source may still be holding events back.
        TimelineEvent horizon = null;
        // Keyed by id: if two sources describe the same record, it is shown once.
        Map<String, TimelineEvent> unique = new LinkedHashMap<>();
        for (List<TimelineEvent> events : perSource) {
            if (events.size() >= requested) {
                TimelineEvent oldest = events.stream().max(NEWEST_FIRST).orElseThrow();
                if (horizon == null || NEWEST_FIRST.compare(oldest, horizon) < 0) {
                    horizon = oldest;
                }
            }
            for (TimelineEvent event : events) {
                unique.putIfAbsent(event.id(), event);
            }
        }

        List<TimelineEvent> safe = new ArrayList<>();
        for (TimelineEvent event : unique.values()) {
            boolean older = cursor == null || cursor.isBefore(event);
            boolean aboveHorizon = horizon == null || NEWEST_FIRST.compare(event, horizon) <= 0;
            if (older && aboveHorizon) {
                safe.add(event);
            }
        }
        safe.sort(NEWEST_FIRST);

        List<TimelineEvent> page = new ArrayList<>();
        int visited = 0;
        while (visited < safe.size() && page.size() < limit) {
            TimelineEvent event = safe.get(visited++);
            if (categories == null || categories.isEmpty() || categories.contains(event.category())) {
                page.add(event);
            }
        }

        boolean more = visited < safe.size() || horizon != null;
        if (!more || visited == 0) {
            return new TimelinePage(List.copyOf(page), null);
        }
        // Continue from the last event looked at, whether or not the filter let it through, so a
        // filtered timeline keeps moving even when a stretch of it has no matching events.
        return new TimelinePage(List.copyOf(page), TimelineCursor.after(safe.get(visited - 1)).encode());
    }
}
