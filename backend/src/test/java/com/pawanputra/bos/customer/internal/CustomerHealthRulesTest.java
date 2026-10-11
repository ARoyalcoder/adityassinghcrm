package com.pawanputra.bos.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawanputra.bos.customer.api.CustomerHealthContributor.Impact;
import com.pawanputra.bos.customer.api.CustomerHealthContributor.Signal;
import com.pawanputra.bos.customer.api.CustomerStatus;
import com.pawanputra.bos.customer.internal.CustomerOverviewService.HealthLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerHealthRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final CustomerHealthProperties THRESHOLDS = new CustomerHealthProperties(30, 90);

    private static Instant daysAgo(long days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private static List<Signal> evaluate(CustomerStatus status, long knownForDays, Long lastContactDaysAgo, boolean reachable) {
        return CustomerHealthRules.evaluate(status, daysAgo(knownForDays),
                lastContactDaysAgo == null ? null : daysAgo(lastContactDaysAgo), reachable, NOW, THRESHOLDS);
    }

    private static HealthLevel level(List<Signal> signals) {
        return CustomerOverviewService.level(signals);
    }

    @Test
    void recentContactIsGood() {
        List<Signal> signals = evaluate(CustomerStatus.ACTIVE, 400, 3L, true);

        assertThat(signals).extracting(Signal::code).containsExactly("CONTACT_RECENT");
        assertThat(signals.getFirst().message()).isEqualTo("In contact 3 days ago");
        assertThat(level(signals)).isEqualTo(HealthLevel.GOOD);
    }

    @Test
    void contactTodayAndYesterdayReadNaturally() {
        assertThat(evaluate(CustomerStatus.ACTIVE, 10, 0L, true).getFirst().message()).isEqualTo("In contact today");
        assertThat(evaluate(CustomerStatus.ACTIVE, 10, 1L, true).getFirst().message()).isEqualTo("In contact yesterday");
    }

    @Test
    void contactThatIsNeitherRecentNorStaleSaysNothing() {
        List<Signal> signals = evaluate(CustomerStatus.ACTIVE, 400, 60L, true);

        assertThat(signals).isEmpty();
        assertThat(level(signals)).isEqualTo(HealthLevel.UNKNOWN); // no evidence either way
    }

    @Test
    void thresholdsAreInclusiveOnTheGoodSide() {
        assertThat(evaluate(CustomerStatus.ACTIVE, 400, 30L, true)).extracting(Signal::code).containsExactly("CONTACT_RECENT");
        assertThat(evaluate(CustomerStatus.ACTIVE, 400, 31L, true)).isEmpty();
        assertThat(evaluate(CustomerStatus.ACTIVE, 400, 90L, true)).isEmpty();
        assertThat(evaluate(CustomerStatus.ACTIVE, 400, 91L, true)).extracting(Signal::code).containsExactly("CONTACT_STALE");
    }

    @Test
    void longSilenceNeedsAttention() {
        List<Signal> signals = evaluate(CustomerStatus.ACTIVE, 400, 120L, true);

        assertThat(signals).containsExactly(new Signal("CONTACT_STALE", Impact.WARNING, "Last contact was 120 days ago"));
        assertThat(level(signals)).isEqualTo(HealthLevel.NEEDS_ATTENTION);
    }

    @Test
    void aNewCustomerWithNoHistoryIsUnknownNotUnhealthy() {
        List<Signal> signals = evaluate(CustomerStatus.ACTIVE, 5, null, true);

        assertThat(signals).isEmpty();
        assertThat(level(signals)).isEqualTo(HealthLevel.UNKNOWN);
    }

    @Test
    void anOldCustomerNeverContactedNeedsAttention() {
        List<Signal> signals = evaluate(CustomerStatus.ACTIVE, 200, null, true);

        assertThat(signals).extracting(Signal::code).containsExactly("NEVER_CONTACTED");
        assertThat(signals.getFirst().message()).contains("200 days");
        assertThat(level(signals)).isEqualTo(HealthLevel.NEEDS_ATTENTION);
    }

    @Test
    void blockedCustomerIsAtRiskWhateverElseIsTrue() {
        List<Signal> signals = evaluate(CustomerStatus.BLOCKED, 400, 1L, true);

        assertThat(signals).extracting(Signal::code).containsExactly("CUSTOMER_BLOCKED", "CONTACT_RECENT");
        assertThat(level(signals)).isEqualTo(HealthLevel.AT_RISK);
    }

    @Test
    void inactiveOrUnreachableCustomerNeedsAttentionEvenWithRecentContact() {
        assertThat(level(evaluate(CustomerStatus.INACTIVE, 400, 1L, true))).isEqualTo(HealthLevel.NEEDS_ATTENTION);

        List<Signal> unreachable = evaluate(CustomerStatus.ACTIVE, 400, 1L, false);
        assertThat(unreachable).extracting(Signal::code).containsExactly("NOT_REACHABLE", "CONTACT_RECENT");
        assertThat(level(unreachable)).isEqualTo(HealthLevel.NEEDS_ATTENTION);
    }

    @Test
    void prospectIsNotPenalisedForBeingAProspect() {
        assertThat(evaluate(CustomerStatus.PROSPECT, 5, null, true)).isEmpty();
    }

    @Test
    void theWorstSignalFromAnyModuleDecidesTheLevel() {
        Signal good = new Signal("CONTACT_RECENT", Impact.POSITIVE, "In contact today");
        Signal overdue = new Signal("INVOICES_OVERDUE", Impact.CRITICAL, "2 invoices overdue");
        Signal tickets = new Signal("OPEN_TICKETS", Impact.WARNING, "3 open tickets");

        assertThat(level(List.of(good))).isEqualTo(HealthLevel.GOOD);
        assertThat(level(List.of(good, tickets))).isEqualTo(HealthLevel.NEEDS_ATTENTION);
        assertThat(level(List.of(good, tickets, overdue))).isEqualTo(HealthLevel.AT_RISK);
        assertThat(level(List.of())).isEqualTo(HealthLevel.UNKNOWN);
    }
}
