package com.pawanputra.bos.platform.timeline;

import java.util.List;

/**
 * One page of a timeline, newest first.
 *
 * @param nextCursor pass it back to get the next (older) page; {@code null} when there is nothing older
 */
public record TimelinePage(List<TimelineEvent> events, String nextCursor) {
}
