package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.api.CustomerHealthContributor.Impact;
import com.pawanputra.bos.customer.api.CustomerHealthContributor.Signal;
import com.pawanputra.bos.customer.api.CustomerStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The health signals that can be worked out from the customer record and the contact log alone.
 * Pure functions of their inputs, so every rule can be read and tested in one place.
 *
 * <p>These say nothing about money or service quality: those signals come from the billing and
 * support modules through {@code CustomerHealthContributor} once they exist.
 */
final class CustomerHealthRules {

    private CustomerHealthRules() {
    }

    /**
     * @param lastContactAt  when the customer was last called, met, emailed or visited; {@code null} if never
     * @param reachable      whether any phone number or email is on record, for the customer or a contact
     */
    static List<Signal> evaluate(
            CustomerStatus status, Instant customerSince, Instant lastContactAt, boolean reachable, Instant now,
            CustomerHealthProperties thresholds) {
        List<Signal> signals = new ArrayList<>();

        if (status == CustomerStatus.BLOCKED) {
            signals.add(new Signal("CUSTOMER_BLOCKED", Impact.CRITICAL, "The customer is blocked"));
        } else if (status == CustomerStatus.INACTIVE) {
            signals.add(new Signal("CUSTOMER_INACTIVE", Impact.WARNING, "The customer is marked inactive"));
        }

        if (!reachable) {
            signals.add(new Signal("NOT_REACHABLE", Impact.WARNING, "No phone number or email is on record"));
        }

        if (lastContactAt != null) {
            long days = daysBetween(lastContactAt, now);
            if (days > thresholds.staleContactDays()) {
                signals.add(new Signal("CONTACT_STALE", Impact.WARNING, "Last contact was " + days + " days ago"));
            } else if (days <= thresholds.recentContactDays()) {
                signals.add(new Signal("CONTACT_RECENT", Impact.POSITIVE,
                        days == 0 ? "In contact today" : days == 1 ? "In contact yesterday"
                                : "In contact " + days + " days ago"));
            }
        } else {
            long daysKnown = daysBetween(customerSince, now);
            // A customer added recently simply has no history yet; that is not a problem to flag.
            if (daysKnown > thresholds.staleContactDays()) {
                signals.add(new Signal("NEVER_CONTACTED", Impact.WARNING,
                        "No contact has been logged in the " + daysKnown + " days since the customer was added"));
            }
        }
        return signals;
    }

    private static long daysBetween(Instant earlier, Instant later) {
        return Math.max(0, Duration.between(earlier, later).toDays());
    }
}
