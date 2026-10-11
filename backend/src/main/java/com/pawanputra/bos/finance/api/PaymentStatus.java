package com.pawanputra.bos.finance.api;

/** Persisted by name; must match the CHECK constraint on {@code payments.status}. */
public enum PaymentStatus {
    RECEIVED,
    /**
     * The money did not arrive after all (a bounced cheque) or the payment was entered by mistake.
     * The payment stays on record with the reason; it settles nothing.
     */
    REVERSED
}
