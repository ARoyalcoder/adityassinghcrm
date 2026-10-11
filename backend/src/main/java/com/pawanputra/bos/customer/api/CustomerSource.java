package com.pawanputra.bos.customer.api;

/** How the customer first came to the company. Persisted by name; must match {@code ck_customers_source}. */
public enum CustomerSource {
    WALK_IN,
    REFERRAL,
    WEBSITE,
    PHONE_CALL,
    SOCIAL_MEDIA,
    ADVERTISEMENT,
    PARTNER,
    OTHER
}
