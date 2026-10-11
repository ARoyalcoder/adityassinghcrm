package com.pawanputra.bos.finance.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawanputra.bos.finance.internal.InvoiceMath.Line;
import com.pawanputra.bos.finance.internal.InvoiceMath.Totals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How an invoice adds up, and how money is written. */
class InvoiceMathTest {

    @Test
    void aLineIsQuantityTimesPriceLessDiscountPlusTax() {
        Line line = InvoiceMath.line(dec("8"), dec("12500"), dec("10"), dec("18"));

        assertThat(line.subtotal()).isEqualByComparingTo("100000.00");
        assertThat(line.discount()).isEqualByComparingTo("10000.00");
        assertThat(line.tax()).isEqualByComparingTo("16200.00");      // 18% of what is left after the discount
        assertThat(line.total()).isEqualByComparingTo("106200.00");
    }

    @Test
    void aLineWithoutDiscountOrTaxIsJustQuantityTimesPrice() {
        Line line = InvoiceMath.line(dec("62.5"), dec("48"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(line.subtotal()).isEqualByComparingTo("3000.00");
        assertThat(line.discount()).isEqualByComparingTo("0");
        assertThat(line.tax()).isEqualByComparingTo("0");
        assertThat(line.total()).isEqualByComparingTo("3000.00");
    }

    @Test
    void everyAmountIsRoundedToPaiseHalfUp() {
        // 3 x 33.335 would be 100.005; prices have two decimals, so use a fractional quantity instead.
        Line line = InvoiceMath.line(dec("0.333"), dec("100.05"), dec("12.5"), dec("18"));

        assertThat(line.subtotal()).isEqualByComparingTo("33.32");    // 33.31665
        assertThat(line.discount()).isEqualByComparingTo("4.17");     // 4.165 rounds up
        assertThat(line.tax()).isEqualByComparingTo("5.25");          // 18% of 29.15 = 5.247
        assertThat(line.total()).isEqualByComparingTo("34.40");
        assertThat(line.total().scale()).isEqualTo(2);
    }

    @Test
    void theInvoiceIsTheSumOfItsLinesSoItCanBeCheckedByHand() {
        List<Line> lines = List.of(
                InvoiceMath.line(dec("1"), dec("999.99"), dec("0"), dec("18")),
                InvoiceMath.line(dec("3"), dec("0.35"), dec("0"), dec("18")),
                InvoiceMath.line(dec("2"), dec("150"), dec("5"), dec("12")));

        Totals totals = InvoiceMath.totals(lines);

        assertThat(totals.subtotal()).isEqualByComparingTo("1301.04");
        assertThat(totals.discountTotal()).isEqualByComparingTo("15.00");
        // 180.00 + 0.19 + 34.20: each line's tax is rounded on its own, then added.
        assertThat(totals.taxTotal()).isEqualByComparingTo("214.39");
        assertThat(totals.total()).isEqualByComparingTo("1500.43");
        assertThat(totals.total()).isEqualByComparingTo(
                totals.subtotal().subtract(totals.discountTotal()).add(totals.taxTotal()));
    }

    @Test
    void anInvoiceWithNoLinesAddsUpToNothing() {
        Totals totals = InvoiceMath.totals(List.of());

        assertThat(totals.total()).isEqualByComparingTo("0");
        assertThat(totals.total().scale()).isEqualTo(2);
    }

    @Test
    void aFullDiscountLeavesNothingToTax() {
        Line line = InvoiceMath.line(dec("1"), dec("500"), dec("100"), dec("18"));

        assertThat(line.tax()).isEqualByComparingTo("0");
        assertThat(line.total()).isEqualByComparingTo("0");
    }

    @Test
    void rupeesAreWrittenWithIndianGroupingAndPaiseOnlyWhenThereAreAny() {
        assertThat(Money.rupees(dec("0"))).isEqualTo("₹0");
        assertThat(Money.rupees(dec("999"))).isEqualTo("₹999");
        assertThat(Money.rupees(dec("1000"))).isEqualTo("₹1,000");
        assertThat(Money.rupees(dec("118000"))).isEqualTo("₹1,18,000");
        assertThat(Money.rupees(dec("12345678.5"))).isEqualTo("₹1,23,45,678.50");
        assertThat(Money.rupees(dec("1500.05"))).isEqualTo("₹1,500.05");
        assertThat(Money.rupees(dec("-2500"))).isEqualTo("-₹2,500");
    }

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }
}
