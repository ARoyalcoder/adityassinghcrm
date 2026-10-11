package com.pawanputra.bos.platform.timeline;

/** The broad kind of a timeline event. Used to filter a timeline and to choose how an event looks. */
public enum TimelineCategory {
    /** Calls, meetings, emails, visits, notes. */
    COMMUNICATION,
    /** Leads, opportunities, quotations, orders. */
    SALES,
    /** Projects and their milestones. */
    PROJECT,
    /** Invoices and payments. */
    FINANCE,
    /** Support tickets and service visits. */
    SERVICE,
    /** Maintenance contracts and warranties. */
    AMC,
    /** Changes to the record itself: created, status changed, contact added. */
    RECORD
}
