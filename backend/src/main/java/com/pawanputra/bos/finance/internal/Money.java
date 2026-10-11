package com.pawanputra.bos.finance.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Amounts of money: two decimal places, rounded half up, the way an invoice is added up by hand. */
final class Money {

    static final BigDecimal ZERO = new BigDecimal("0.00");
    /** The largest amount a money column holds: numeric(14,2). */
    static final BigDecimal MAX = new BigDecimal("999999999999.99");

    private Money() {
    }

    static BigDecimal of(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** The given percentage of an amount, rounded to paise. */
    static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** ₹1,18,000 or ₹1,18,000.50: Indian digit grouping, paise only when there are any. */
    static String rupees(BigDecimal amount) {
        BigDecimal value = of(amount);
        String digits = value.abs().toBigInteger().toString();
        StringBuilder grouped = new StringBuilder();
        int head = digits.length() - 3;
        if (head > 0) {
            // Everything before the last three digits is grouped in twos.
            int first = head % 2;
            if (first > 0) {
                grouped.append(digits, 0, first).append(',');
            }
            for (int i = first; i < head; i += 2) {
                grouped.append(digits, i, i + 2).append(',');
            }
            grouped.append(digits.substring(head));
        } else {
            grouped.append(digits);
        }
        String paise = value.abs().remainder(BigDecimal.ONE).movePointRight(2).toBigInteger().toString();
        return (value.signum() < 0 ? "-₹" : "₹") + grouped
                + (paise.equals("0") ? "" : "." + (paise.length() == 1 ? "0" + paise : paise));
    }
}
