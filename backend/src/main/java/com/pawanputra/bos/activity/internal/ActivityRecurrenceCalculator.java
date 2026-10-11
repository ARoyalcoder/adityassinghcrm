package com.pawanputra.bos.activity.internal;

import com.pawanputra.bos.activity.api.RecurrencePattern;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

public final class ActivityRecurrenceCalculator {

    private ActivityRecurrenceCalculator() {
    }

    /**
     * Calculates the next due date for a recurring activity.
     * Advances the date to at least now/next period if past due.
     * Returns empty if next date exceeds recurrenceEndAt or recurrence pattern is null.
     */
    public static Optional<Instant> nextDueDate(
            Instant currentDueDate,
            RecurrencePattern pattern,
            Integer interval,
            Instant recurrenceEndAt,
            Instant now) {
        if (pattern == null || currentDueDate == null) {
            return Optional.empty();
        }
        int step = (interval != null && interval > 0) ? interval : 1;
        ZonedDateTime current = currentDueDate.atZone(ZoneOffset.UTC);
        ZonedDateTime minThreshold = (now != null) ? now.atZone(ZoneOffset.UTC) : current;

        ZonedDateTime next = advance(current, pattern, step);
        while (!next.isAfter(minThreshold)) {
            next = advance(next, pattern, step);
        }

        Instant nextInstant = next.toInstant();
        if (recurrenceEndAt != null && nextInstant.isAfter(recurrenceEndAt)) {
            return Optional.empty();
        }
        return Optional.of(nextInstant);
    }

    private static ZonedDateTime advance(ZonedDateTime base, RecurrencePattern pattern, int step) {
        return switch (pattern) {
            case DAILY -> base.plusDays(step);
            case WEEKLY -> base.plusWeeks(step);
            case BIWEEKLY -> base.plusWeeks(step * 2L);
            case MONTHLY -> base.plusMonths(step);
            case YEARLY -> base.plusYears(step);
        };
    }
}
