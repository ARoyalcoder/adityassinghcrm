package com.pawanputra.bos.finance.internal;

import java.time.LocalDate;

/** The fiscal year a date falls in. A fiscal year is known by the calendar year it starts in. */
final class FiscalYear {

    private FiscalYear() {
    }

    /**
     * @param startMonth 1 to 12: the month the fiscal year starts in
     * @return 2026 for any date from April 2026 to March 2027 when the year starts in April
     */
    static int startingIn(LocalDate date, int startMonth) {
        return date.getMonthValue() >= startMonth ? date.getYear() : date.getYear() - 1;
    }

    /** As it appears in a document number: "2627" for 2026-27, or "2026" when the fiscal year is the calendar year. */
    static String label(int startingYear, int startMonth) {
        if (startMonth == 1) {
            return String.valueOf(startingYear);
        }
        return "%02d%02d".formatted(startingYear % 100, (startingYear + 1) % 100);
    }
}
