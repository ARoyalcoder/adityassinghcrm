package com.pawanputra.bos.platform.timeline;

import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Where a page of a timeline ended: the last event shown. The next page is everything strictly
 * older, with the id breaking ties between events that share a timestamp.
 *
 * <p>A position in time rather than a page number, because a timeline is merged from several sources
 * and new events keep arriving at the top: "page 2" would shift, "older than this event" does not.
 */
public record TimelineCursor(Instant occurredAt, String id) {

    private static final String SEPARATOR = "|";

    static TimelineCursor after(TimelineEvent event) {
        return new TimelineCursor(event.occurredAt(), event.id());
    }

    /** Whether the event comes after this position in a newest-first timeline (that is, it is older). */
    boolean isBefore(TimelineEvent event) {
        int byTime = event.occurredAt().compareTo(occurredAt);
        return byTime < 0 || (byTime == 0 && event.id().compareTo(id) < 0);
    }

    /** An opaque token for the client to send back unchanged. */
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((occurredAt + SEPARATOR + id).getBytes(StandardCharsets.UTF_8));
    }

    /** @return {@code null} for a missing token (the first page) */
    public static TimelineCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String text = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = text.indexOf(SEPARATOR);
            if (separator > 0) {
                return new TimelineCursor(Instant.parse(text.substring(0, separator)), text.substring(separator + 1));
            }
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // fall through: not a token this API issued
        }
        throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The timeline cursor is not valid");
    }
}
