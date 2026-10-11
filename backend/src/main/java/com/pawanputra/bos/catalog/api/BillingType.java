package com.pawanputra.bos.catalog.api;

/**
 * How a service is charged. Stored on the service so billing, quoting and reporting modules decide
 * from data rather than from the vertical or the service code.
 * Persisted by name; must match the CHECK constraint on {@code services.billing_type}.
 */
public enum BillingType {
    /** A fixed job charged once (an installation, a design). */
    ONE_TIME,
    /** Charged every period (annual maintenance, a monthly retainer). */
    RECURRING,
    /** No standard price: every enquiry gets its own quote. */
    QUOTE_BASED
}
