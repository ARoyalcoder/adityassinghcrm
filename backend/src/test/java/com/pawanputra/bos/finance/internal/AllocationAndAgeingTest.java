package com.pawanputra.bos.finance.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawanputra.bos.finance.internal.Ageing.Item;
import com.pawanputra.bos.finance.internal.Ageing.Report;
import com.pawanputra.bos.finance.internal.AllocationPlanner.Open;
import com.pawanputra.bos.finance.internal.AllocationPlanner.Share;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which invoices a payment settles, how old a debt is, and which year a document belongs to. */
class AllocationAndAgeingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final List<Integer> BANDS = List.of(30, 60, 90);
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    // ------------------------------------------------------------------ allocation

    @Test
    void aPaymentSettlesTheOldestDebtFirst() {
        List<Open> open = List.of(
                new Open(A, "INV-2627-000003", TODAY.plusDays(10), dec("500")),
                new Open(B, "INV-2627-000001", TODAY.minusDays(20), dec("300")),
                new Open(C, "INV-2627-000002", TODAY.minusDays(5), dec("400")));

        List<Share> shares = AllocationPlanner.oldestFirst(dec("800"), open);

        // B (due longest ago) in full, then C in full, and the last 100 to A.
        assertThat(shares).containsExactly(new Share(B, dec("300")), new Share(C, dec("400")), new Share(A, dec("100")));
    }

    @Test
    void whatIsLeftWhenTheInvoicesRunOutStaysUnallocated() {
        List<Share> shares = AllocationPlanner.oldestFirst(dec("1000"), List.of(new Open(A, "INV-1", TODAY, dec("250"))));

        assertThat(shares).containsExactly(new Share(A, dec("250")));
    }

    @Test
    void invoicesDueOnTheSameDayAreSettledInNumberOrder() {
        List<Open> open = List.of(
                new Open(A, "INV-2627-000007", TODAY, dec("100")),
                new Open(B, "INV-2627-000004", TODAY, dec("100")));

        assertThat(AllocationPlanner.oldestFirst(dec("150"), open))
                .containsExactly(new Share(B, dec("100")), new Share(A, dec("50")));
    }

    @Test
    void aCustomerWhoOwesNothingGetsNoAllocations() {
        assertThat(AllocationPlanner.oldestFirst(dec("500"), List.of())).isEmpty();
    }

    // ------------------------------------------------------------------ ageing

    @Test
    void theBandsFollowTheConfiguredUpperEnds() {
        assertThat(Ageing.buckets(BANDS)).extracting(Ageing.Bucket::label)
                .containsExactly("Not due", "1-30 days", "31-60 days", "61-90 days", "Over 90 days");
        assertThat(Ageing.buckets(List.of(15))).extracting(Ageing.Bucket::label)
                .containsExactly("Not due", "1-15 days", "Over 15 days");
    }

    @Test
    void anInvoiceDueTodayIsNotOverdueAndTheBandsMeetWithoutGaps() {
        assertThat(Ageing.bucketIndex(-12, BANDS)).isZero();
        assertThat(Ageing.bucketIndex(0, BANDS)).isZero();
        assertThat(Ageing.bucketIndex(1, BANDS)).isEqualTo(1);
        assertThat(Ageing.bucketIndex(30, BANDS)).isEqualTo(1);
        assertThat(Ageing.bucketIndex(31, BANDS)).isEqualTo(2);
        assertThat(Ageing.bucketIndex(60, BANDS)).isEqualTo(2);
        assertThat(Ageing.bucketIndex(61, BANDS)).isEqualTo(3);
        assertThat(Ageing.bucketIndex(90, BANDS)).isEqualTo(3);
        assertThat(Ageing.bucketIndex(91, BANDS)).isEqualTo(4);
        assertThat(Ageing.bucketIndex(4000, BANDS)).isEqualTo(4);
    }

    @Test
    void theReportAddsUpEachCustomerByBandLargestDebtFirst() {
        Report report = Ageing.report(TODAY, BANDS, List.of(
                new Item(A, "Acme Solar", TODAY.plusDays(5), dec("1000.00")),      // not due
                new Item(A, "Acme Solar", TODAY.minusDays(10), dec("250.50")),     // 1-30
                new Item(B, "Bright Interiors", TODAY.minusDays(45), dec("4000.00")), // 31-60
                new Item(B, "Bright Interiors", TODAY.minusDays(200), dec("600.00")), // over 90
                new Item(A, "Acme Solar", TODAY.minusDays(30), dec("100.00"))));    // 1-30

        assertThat(report.asOf()).isEqualTo(TODAY);
        assertThat(report.totals()).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(dec("1000"), dec("350.50"), dec("4000"), dec("0"), dec("600"));
        assertThat(report.total()).isEqualByComparingTo("5950.50");
        assertThat(report.overdue()).isEqualByComparingTo("4950.50");

        assertThat(report.customers()).extracting(Ageing.Row::customerName)
                .containsExactly("Bright Interiors", "Acme Solar");
        Ageing.Row bright = report.customers().get(0);
        assertThat(bright.total()).isEqualByComparingTo("4600");
        assertThat(bright.oldestDaysOverdue()).isEqualTo(200);
        assertThat(bright.invoices()).isEqualTo(2);
        Ageing.Row acme = report.customers().get(1);
        assertThat(acme.amounts()).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(dec("1000"), dec("350.50"), dec("0"), dec("0"), dec("0"));
        assertThat(acme.oldestDaysOverdue()).isEqualTo(30);
    }

    @Test
    void aCustomerWithNothingOverdueHasNoOldestDebt() {
        Report report = Ageing.report(TODAY, BANDS, List.of(new Item(A, "Acme Solar", TODAY.plusDays(3), dec("10"))));

        assertThat(report.customers().get(0).oldestDaysOverdue()).isZero();
        assertThat(report.overdue()).isEqualByComparingTo("0");
    }

    @Test
    void nothingOwedGivesAnEmptyReportWithAllTheBands() {
        Report report = Ageing.report(TODAY, BANDS, List.of());

        assertThat(report.customers()).isEmpty();
        assertThat(report.totals()).hasSize(5);
        assertThat(report.total()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ fiscal year and document numbers

    @Test
    void aFiscalYearStartingInAprilRunsToTheNextMarch() {
        assertThat(FiscalYear.startingIn(LocalDate.of(2026, 4, 1), 4)).isEqualTo(2026);
        assertThat(FiscalYear.startingIn(LocalDate.of(2026, 10, 10), 4)).isEqualTo(2026);
        assertThat(FiscalYear.startingIn(LocalDate.of(2027, 3, 31), 4)).isEqualTo(2026);
        assertThat(FiscalYear.startingIn(LocalDate.of(2026, 3, 31), 4)).isEqualTo(2025);
        assertThat(FiscalYear.label(2026, 4)).isEqualTo("2627");
        assertThat(FiscalYear.label(2099, 4)).isEqualTo("9900");
    }

    @Test
    void aFiscalYearThatIsTheCalendarYearIsWrittenInFull() {
        assertThat(FiscalYear.startingIn(LocalDate.of(2026, 1, 1), 1)).isEqualTo(2026);
        assertThat(FiscalYear.startingIn(LocalDate.of(2026, 12, 31), 1)).isEqualTo(2026);
        assertThat(FiscalYear.label(2026, 1)).isEqualTo("2026");
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void theSameRequestHasTheSameFingerprintHoweverTheAmountIsWritten() {
        assertThat(Idempotency.fingerprint(A, dec("100"), "UPI"))
                .isEqualTo(Idempotency.fingerprint(A, dec("100.00"), "UPI"))
                .hasSize(64);
    }

    @Test
    void aDifferentRequestHasADifferentFingerprint() {
        String original = Idempotency.fingerprint(A, dec("100"), "UPI");

        assertThat(Idempotency.fingerprint(A, dec("100.01"), "UPI")).isNotEqualTo(original);
        assertThat(Idempotency.fingerprint(B, dec("100"), "UPI")).isNotEqualTo(original);
        // The parts are kept apart: "ab" + "c" is not "a" + "bc".
        assertThat(Idempotency.fingerprint("ab", "c")).isNotEqualTo(Idempotency.fingerprint("a", "bc"));
    }

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }
}
