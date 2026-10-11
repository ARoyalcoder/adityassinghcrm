package com.pawanputra.bos.customer.api;

/** Persisted by name; must match the CHECK constraint on {@code customer_addresses.type}. */
public enum AddressType {
    /** Where invoices are addressed. */
    BILLING,
    /** Where the work is done: the site of an installation, the premises being designed. */
    SERVICE,
    OTHER
}
