package com.pawanputra.bos.customer.api;

/** Persisted by name; must match the CHECK constraint on {@code customers.type}. */
public enum CustomerType {
    /** A person. Identified by first and last name. */
    INDIVIDUAL,
    /** A company or other organisation. Identified by its company name; its people are contacts. */
    BUSINESS
}
