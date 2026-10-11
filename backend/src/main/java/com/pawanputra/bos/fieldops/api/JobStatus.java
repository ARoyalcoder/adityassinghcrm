package com.pawanputra.bos.fieldops.api;

import java.util.Optional;

/**
 * Where a field job stands. A job moves forward one step at a time:
 *
 * <pre>ASSIGNED → ACCEPTED → EN_ROUTE → ARRIVED → IN_PROGRESS → COMPLETED → CUSTOMER_CONFIRMED</pre>
 *
 * {@code UNASSIGNED} comes before a technician is chosen, and {@code CANCELLED} ends a job early.
 */
public enum JobStatus {
    /** Created, with nobody to do it yet. */
    UNASSIGNED,
    /** Given to a technician, who has not yet said they will do it. */
    ASSIGNED,
    ACCEPTED,
    /** The technician is travelling to the site. */
    EN_ROUTE,
    ARRIVED,
    IN_PROGRESS,
    /** The technician says the work is done. */
    COMPLETED,
    /** The customer has confirmed the work on site. Nothing changes after this. */
    CUSTOMER_CONFIRMED,
    CANCELLED;

    /** The one step a technician can take from here; empty where there is none. */
    public Optional<JobStatus> next() {
        return switch (this) {
            case ASSIGNED -> Optional.of(ACCEPTED);
            case ACCEPTED -> Optional.of(EN_ROUTE);
            case EN_ROUTE -> Optional.of(ARRIVED);
            case ARRIVED -> Optional.of(IN_PROGRESS);
            case IN_PROGRESS -> Optional.of(COMPLETED);
            case COMPLETED -> Optional.of(CUSTOMER_CONFIRMED);
            case UNASSIGNED, CUSTOMER_CONFIRMED, CANCELLED -> Optional.empty();
        };
    }

    /** Nothing more can happen to the job. */
    public boolean isClosed() {
        return this == CUSTOMER_CONFIRMED || this == CANCELLED;
    }

    /** The technician's part is done (whether or not the customer has confirmed it yet). */
    public boolean isDone() {
        return this == COMPLETED || this == CUSTOMER_CONFIRMED;
    }

    /** A technician can still be chosen or changed: nobody has set off yet. */
    public boolean canBeReassigned() {
        return this == UNASSIGNED || this == ASSIGNED || this == ACCEPTED;
    }

    /**
     * The technician is on the job and may record what they do: tick the checklist, add photos and
     * materials. From accepting it until the customer confirms.
     */
    public boolean acceptsWork() {
        return this == ACCEPTED || this == EN_ROUTE || this == ARRIVED || this == IN_PROGRESS || this == COMPLETED;
    }
}
