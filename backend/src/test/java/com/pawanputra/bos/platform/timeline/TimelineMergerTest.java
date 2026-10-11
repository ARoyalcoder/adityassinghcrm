package com.pawanputra.bos.platform.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawanputra.bos.platform.error.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class TimelineMergerTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static TimelineEvent event(String id, long minutesAfterT0, TimelineCategory category) {
        return TimelineEvent.of(id, "TEST", category, T0.plusSeconds(minutesAfterT0 * 60), id, null, null);
    }

    /** A source holding the given events, answering "newest {@code limit} at or before {@code notAfter}". */
    private static BiFunction<Instant, Integer, List<TimelineEvent>> source(List<TimelineEvent> all) {
        return (notAfter, limit) -> all.stream()
                .filter(e -> notAfter == null || !e.occurredAt().isAfter(notAfter))
                .sorted(Comparator.comparing(TimelineEvent::occurredAt).reversed()
                        .thenComparing(Comparator.comparing(TimelineEvent::id).reversed()))
                .limit(limit)
                .toList();
    }

    /** Pages through the sources exactly as a caller would, returning every event id in the order seen. */
    @SafeVarargs
    private static List<String> readAll(
            int pageSize, Set<TimelineCategory> categories, BiFunction<Instant, Integer, List<TimelineEvent>>... sources) {
        List<String> seen = new ArrayList<>();
        String token = null;
        for (int guard = 0; guard < 1000; guard++) {
            TimelineCursor cursor = TimelineCursor.decode(token);
            List<List<TimelineEvent>> fetched = new ArrayList<>();
            for (var source : sources) {
                fetched.add(source.apply(cursor == null ? null : cursor.occurredAt(), TimelineMerger.fetchSize(pageSize)));
            }
            TimelinePage page = TimelineMerger.merge(fetched, cursor, pageSize, categories);
            assertThat(page.events()).hasSizeLessThanOrEqualTo(pageSize);
            page.events().forEach(e -> seen.add(e.id()));
            token = page.nextCursor();
            if (token == null) {
                return seen;
            }
        }
        throw new AssertionError("paging did not terminate");
    }

    @Test
    void mergesSourcesNewestFirst() {
        List<TimelineEvent> calls = List.of(event("call-1", 10, TimelineCategory.COMMUNICATION),
                event("call-2", 40, TimelineCategory.COMMUNICATION));
        List<TimelineEvent> invoices = List.of(event("invoice-1", 30, TimelineCategory.FINANCE),
                event("invoice-2", 20, TimelineCategory.FINANCE));

        TimelinePage page = TimelineMerger.merge(List.of(calls, invoices), null, 10, Set.of());

        assertThat(page.events()).extracting(TimelineEvent::id)
                .containsExactly("call-2", "invoice-1", "invoice-2", "call-1");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void theSameRecordFromTwoSourcesIsShownOnce() {
        TimelineEvent quote = event("quotation-sent:42", 10, TimelineCategory.SALES);

        TimelinePage page = TimelineMerger.merge(List.of(List.of(quote), List.of(quote)), null, 10, Set.of());

        assertThat(page.events()).hasSize(1);
    }

    @Test
    void pagingShowsEveryEventExactlyOnceInOrder() {
        List<TimelineEvent> a = new ArrayList<>();
        List<TimelineEvent> b = new ArrayList<>();
        for (int i = 0; i < 37; i++) {
            a.add(event("a-%02d".formatted(i), i * 2, TimelineCategory.COMMUNICATION));
        }
        for (int i = 0; i < 12; i++) {
            b.add(event("b-%02d".formatted(i), i * 5 + 1, TimelineCategory.FINANCE));
        }
        List<String> expected = new ArrayList<>();
        List<TimelineEvent> all = new ArrayList<>(a);
        all.addAll(b);
        // Newest first; events in the same minute (a-28 and b-11 both fall on minute 56) by id, descending.
        all.stream().sorted(Comparator.comparing(TimelineEvent::occurredAt).reversed()
                        .thenComparing(Comparator.comparing(TimelineEvent::id).reversed()))
                .forEach(e -> expected.add(e.id()));

        for (int pageSize : new int[] {1, 3, 5, 25, 100}) {
            assertThat(readAll(pageSize, Set.of(), source(a), source(b))).as("page size %d", pageSize)
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void aSourceThatIsCutOffDoesNotCauseOlderEventsFromOthersToJumpAhead() {
        // One busy source with hundreds of recent events and one quiet source with a few old ones.
        // The quiet source's events must not appear until the busy source has been read down to them.
        List<TimelineEvent> busy = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            busy.add(event("busy-%03d".formatted(i), 1000 + i, TimelineCategory.COMMUNICATION));
        }
        List<TimelineEvent> quiet = List.of(event("quiet-new", 1150, TimelineCategory.SALES),
                event("quiet-old", 5, TimelineCategory.SALES));

        List<String> seen = readAll(5, Set.of(), source(busy), source(quiet));

        assertThat(seen).hasSize(302).doesNotHaveDuplicates();
        assertThat(seen.getLast()).isEqualTo("quiet-old");
        // Strictly newest first throughout.
        assertThat(seen.indexOf("busy-299")).isZero();
        assertThat(seen.indexOf("busy-151")).isLessThan(seen.indexOf("quiet-new"));
        assertThat(seen.indexOf("quiet-new")).isLessThan(seen.indexOf("busy-149"));
    }

    @Test
    void eventsSharingATimestampAreNeitherSkippedNorRepeatedAcrossPages() {
        List<TimelineEvent> a = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            a.add(event("same-" + i, 60, TimelineCategory.RECORD)); // nine events in the same instant
        }
        a.add(event("later", 61, TimelineCategory.RECORD));
        a.add(event("earlier", 59, TimelineCategory.RECORD));

        List<String> seen = readAll(2, Set.of(), source(a));

        assertThat(seen).hasSize(11).doesNotHaveDuplicates();
        assertThat(seen.getFirst()).isEqualTo("later");
        assertThat(seen.getLast()).isEqualTo("earlier");
    }

    @Test
    void categoryFilterKeepsOnlyMatchingEventsAndStillReachesTheEnd() {
        List<TimelineEvent> mixed = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            // Only every 40th event is FINANCE: most pages of the unfiltered timeline contain none.
            mixed.add(event("e-%03d".formatted(i), i, i % 40 == 0 ? TimelineCategory.FINANCE : TimelineCategory.COMMUNICATION));
        }

        List<String> finance = readAll(5, Set.of(TimelineCategory.FINANCE), source(mixed));

        assertThat(finance).containsExactly("e-080", "e-040", "e-000");
    }

    @Test
    void noEventsGivesAnEmptyLastPage() {
        TimelinePage page = TimelineMerger.merge(List.of(List.of(), List.of()), null, 10, Set.of());

        assertThat(page.events()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void cursorSurvivesARoundTripAndRejectsGarbage() {
        TimelineCursor cursor = new TimelineCursor(Instant.parse("2026-10-05T10:15:30.123456Z"), "interaction:abc|def");

        assertThat(TimelineCursor.decode(cursor.encode())).isEqualTo(cursor);
        assertThat(TimelineCursor.decode(null)).isNull();
        assertThat(TimelineCursor.decode(" ")).isNull();
        assertThatThrownBy(() -> TimelineCursor.decode("not-a-cursor")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TimelineCursor.decode("!!!!")).isInstanceOf(ApiException.class);
    }
}
