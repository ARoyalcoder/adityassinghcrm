package com.pawanputra.bos.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Where an invoice stands.
 *
 * <p>Five of these are stored. {@code OVERDUE} is not: it is an invoice that still has money owing
 * after its due date, which depends on today's date. It is worked out with {@link #on} each time an
 * invoice is read, so an invoice turns overdue at midnight without anything having to run.
 */
public enum InvoiceStatus {
    /** Being prepared. Has no number, can be edited or discarded, and is not owed. */
    DRAFT,
    /** Sent to the customer; nothing paid yet. */
    ISSUED,
    PARTIALLY_PAID,
    /** Nothing is outstanding: paid, credited, or both. */
    PAID,
    /** Money is still owing and the due date has passed. Never stored. */
    OVERDUE,
    /** Withdrawn after being issued. Keeps its number; nothing is owed. */
    CANCELLED;

    /**
     * The stored status of an issued invoice, from its amounts.
     *
     * @param outstanding what is still owed: total plus debit notes, less credit notes and payments
     * @param paid        payments allocated to the invoice
     */
    public static InvoiceStatus settlement(BigDecimal outstanding, BigDecimal paid) {
        if (outstanding.signum() <= 0) {
            return PAID;
        }
        return paid.signum() > 0 ? PARTIALLY_PAID : ISSUED;
    }

    /** This status as it should be shown on the given day: an open invoice past its due date is overdue. */
    public InvoiceStatus on(LocalDate dueDate, LocalDate today) {
        return isOpen() && dueDate != null && dueDate.isBefore(today) ? OVERDUE : this;
    }

    /** Money is still owed on the invoice. */
    public boolean isOpen() {
        return this == ISSUED || this == PARTIALLY_PAID || this == OVERDUE;
    }

    /** The invoice has a number and is part of the books (it may since have been cancelled). */
    public boolean isIssued() {
        return this != DRAFT;
    }
}
