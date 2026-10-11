package com.pawanputra.bos.finance.internal;

import java.math.BigDecimal;
import java.util.List;

/**
 * How an invoice adds up. Every amount is rounded to paise on its own line, and the invoice totals
 * are the sums of the lines, so the figures on a printed invoice can be checked with a calculator.
 */
final class InvoiceMath {

    private InvoiceMath() {
    }

    /**
     * @param subtotal quantity x unit price
     * @param discount the discount percentage of the subtotal
     * @param tax      the tax percentage of what is left after the discount
     * @param total    subtotal - discount + tax
     */
    record Line(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {
    }

    record Totals(BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total) {
    }

    static Line line(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal taxPercent) {
        BigDecimal subtotal = Money.of(quantity.multiply(unitPrice));
        BigDecimal discount = Money.percentOf(subtotal, discountPercent);
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal tax = Money.percentOf(taxable, taxPercent);
        return new Line(subtotal, discount, tax, taxable.add(tax));
    }

    static Totals totals(List<Line> lines) {
        BigDecimal subtotal = Money.ZERO;
        BigDecimal discount = Money.ZERO;
        BigDecimal tax = Money.ZERO;
        BigDecimal total = Money.ZERO;
        for (Line line : lines) {
            subtotal = subtotal.add(line.subtotal());
            discount = discount.add(line.discount());
            tax = tax.add(line.tax());
            total = total.add(line.total());
        }
        return new Totals(subtotal, discount, tax, total);
    }
}
