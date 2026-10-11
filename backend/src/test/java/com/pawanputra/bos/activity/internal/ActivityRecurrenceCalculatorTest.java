package com.pawanputra.bos.activity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawanputra.bos.activity.api.RecurrencePattern;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivityRecurrenceCalculatorTest {

    @Test
    void dailyRecurrenceAdvancesByInterval() {
        Instant base = Instant.parse("2026-10-10T10:00:00Z");
        Instant now = Instant.parse("2026-10-09T10:00:00Z");

        Optional<Instant> next = ActivityRecurrenceCalculator.nextDueDate(
                base, RecurrencePattern.DAILY, 2, null, now);

        assertThat(next).isPresent().contains(Instant.parse("2026-10-12T10:00:00Z"));
    }

    @Test
    void weeklyRecurrenceAdvancesByWeeks() {
        Instant base = Instant.parse("2026-10-10T10:00:00Z");
        Instant now = Instant.parse("2026-10-09T10:00:00Z");

        Optional<Instant> next = ActivityRecurrenceCalculator.nextDueDate(
                base, RecurrencePattern.WEEKLY, 1, null, now);

        assertThat(next).isPresent().contains(Instant.parse("2026-10-17T10:00:00Z"));
    }

    @Test
    void biweeklyRecurrenceAdvancesByTwoWeeks() {
        Instant base = Instant.parse("2026-10-10T10:00:00Z");
        Instant now = Instant.parse("2026-10-09T10:00:00Z");

        Optional<Instant> next = ActivityRecurrenceCalculator.nextDueDate(
                base, RecurrencePattern.BIWEEKLY, 1, null, now);

        assertThat(next).isPresent().contains(Instant.parse("2026-10-24T10:00:00Z"));
    }

    @Test
    void monthlyRecurrenceAdvancesByMonths() {
        Instant base = Instant.parse("2026-10-10T10:00:00Z");
        Instant now = Instant.parse("2026-10-09T10:00:00Z");

        Optional<Instant> next = ActivityRecurrenceCalculator.nextDueDate(
                base, RecurrencePattern.MONTHLY, 1, null, now);

        assertThat(next).isPresent().contains(Instant.parse("2026-11-10T10:00:00Z"));
    }

    @Test
    void overdueTaskAdvancesPastNow() {
        Instant pastDue = Instant.parse("2026-10-01T10:00:00Z");
        Instant now = Instant.parse("2026-10-09T10:00:00Z");

        Optional<Instant> next = ActivityRecurrenceCalculator.nextDueDate(
                pastDue, RecurrencePattern.DAILY, 3, null, now);

        // 2026-10-01 + 3d = 04th, + 3d = 07th, + 3d = 10th (which is after now 09th)
        assertThat(next).isPresent().contains(Instant.parse("2026-10-10T10:00:00Z"));
    }

    @Test
    void respectsRecurrenceEndDate() {
        Instant base = Instant.parse("2026-10-10T10:00:00Z");
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        Instant endAt = Instant.parse("2026-10-15T10:00:00Z");

        Optional<Instant> next = ActivityRecurrenceCalculator.nextDueDate(
                base, RecurrencePattern.WEEKLY, 1, endAt, now);

        // 2026-10-17 is after endAt 2026-10-15 -> empty
        assertThat(next).isEmpty();
    }
}
