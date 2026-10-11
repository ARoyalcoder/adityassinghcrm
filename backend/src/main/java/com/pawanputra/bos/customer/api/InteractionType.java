package com.pawanputra.bos.customer.api;

/** A kind of contact with a customer. Persisted by name; must match {@code ck_customer_interactions_type}. */
public enum InteractionType {
    CALL,
    MEETING,
    EMAIL,
    SITE_VISIT,
    /** WhatsApp, SMS and similar. */
    MESSAGE,
    OTHER
}
