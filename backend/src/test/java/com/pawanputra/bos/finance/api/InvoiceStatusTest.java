package com.pawanputra.bos.finance.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Test
    void anIssuedInvoiceIsSettledByItsAmounts() {
        assertThat(InvoiceStatus.settlement(dec("1000"), dec("0"))).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(InvoiceStatus.settlement(dec("400"), dec("600"))).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(InvoiceStatus.settlement(dec("0.00"), dec("1000"))).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void anInvoiceCreditedInFullIsPaidThoughNoMoneyArrived() {
        assertThat(InvoiceStatus.settlement(dec("0"), dec("0"))).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void aCreditNoteAloneDoesNotMakeAnInvoicePartiallyPaid() {
        // 1000 billed, 300 credited, nothing paid: 700 outstanding and still simply issued.
        assertThat(InvoiceStatus.settlement(dec("700"), dec("0"))).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void anOpenInvoiceTurnsOverdueTheDayAfterItIsDue() {
        assertThat(InvoiceStatus.ISSUED.on(TODAY, TODAY)).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(InvoiceStatus.ISSUED.on(TODAY.minusDays(1), TODAY)).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(InvoiceStatus.PARTIALLY_PAID.on(TODAY.minusDays(40), TODAY)).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(InvoiceStatus.PARTIALLY_PAID.on(TODAY.plusDays(5), TODAY)).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void anInvoiceWithNothingOwingIsNeverOverdue() {
        LocalDate longAgo = TODAY.minusYears(1);

        assertThat(InvoiceStatus.PAID.on(longAgo, TODAY)).isEqualTo(InvoiceStatus.PAID);
        assertThat(InvoiceStatus.CANCELLED.on(longAgo, TODAY)).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(InvoiceStatus.DRAFT.on(null, TODAY)).isEqualTo(InvoiceStatus.DRAFT);
    }

    @Test
    void moneyIsOwedOnIssuedPartlyPaidAndOverdueInvoicesOnly() {
        assertThat(InvoiceStatus.values()).filteredOn(InvoiceStatus::isOpen)
                .containsExactly(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);
        assertThat(InvoiceStatus.values()).filteredOn(status -> !status.isIssued()).containsExactly(InvoiceStatus.DRAFT);
    }

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }
}
