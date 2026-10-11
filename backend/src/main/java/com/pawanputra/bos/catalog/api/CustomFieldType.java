package com.pawanputra.bos.catalog.api;

/**
 * The kinds of field an administrator can add to a service's lead form. The type decides what
 * answer is acceptable and how it is stored; it never changes after the field is created.
 */
public enum CustomFieldType {
    /** One line of text. */
    TEXT,
    /** A number, optionally with a unit ("sq ft") and a range. */
    NUMBER,
    /** An amount of money in INR, to at most two decimal places. */
    CURRENCY,
    /** Exactly one of the field's options. */
    SELECT,
    /** Any number of the field's options. */
    MULTI_SELECT,
    /** Yes or no. */
    BOOLEAN,
    /** A calendar date. */
    DATE,
    /** Several lines of text. */
    TEXTAREA,
    /** An uploaded file. */
    FILE;

    public boolean hasOptions() {
        return this == SELECT || this == MULTI_SELECT;
    }

    public boolean isNumeric() {
        return this == NUMBER || this == CURRENCY;
    }

    public boolean isText() {
        return this == TEXT || this == TEXTAREA;
    }
}
