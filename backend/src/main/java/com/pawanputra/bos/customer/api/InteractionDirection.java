package com.pawanputra.bos.customer.api;

/** Who contacted whom. Persisted by name; must match {@code ck_customer_interactions_direction}. */
public enum InteractionDirection {
    /** The customer contacted the company. */
    INBOUND,
    /** The company contacted the customer. */
    OUTBOUND
}
