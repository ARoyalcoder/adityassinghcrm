package com.pawanputra.bos.customer.api;

/** Persisted by name; must match the CHECK constraint on {@code customers.status}. */
public enum CustomerStatus {
    /** Known to the company but has not bought anything yet. */
    PROSPECT,
    ACTIVE,
    /** No longer doing business with the company. Kept for history. */
    INACTIVE,
    /** The company has decided not to serve them (for example for non-payment). */
    BLOCKED
}
