package com.pawanputra.bos.lead.api;

/** How far the qualification questions have been answered, and what the answers say. */
public enum QualificationStatus {
    /** No question has been answered. */
    NOT_STARTED,
    /** Some questions are answered, but a required one is not. */
    IN_PROGRESS,
    /** Every required question is answered and no answer rules the lead out. */
    QUALIFIED,
    /** An answer rules the lead out. */
    DISQUALIFIED
}
