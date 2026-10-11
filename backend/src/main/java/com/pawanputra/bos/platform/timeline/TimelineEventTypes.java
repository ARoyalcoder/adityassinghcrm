package com.pawanputra.bos.platform.timeline;

/**
 * The event codes the product knows how to show. A code is what the frontend keys its icon and
 * wording on, so modules use these constants rather than inventing spellings.
 *
 * <p>Codes under "produced today" are emitted by existing modules. The rest are reserved for the
 * modules that will own those records; nothing emits them until that module is built. Adding a code
 * here does not make events appear: only a real record in a real module does.
 */
public final class TimelineEventTypes {

    // ---- produced today (customer module)
    public static final String CUSTOMER_CREATED = "CUSTOMER_CREATED";
    public static final String CUSTOMER_UPDATED = "CUSTOMER_UPDATED";
    public static final String CUSTOMER_STATUS_CHANGED = "CUSTOMER_STATUS_CHANGED";
    public static final String CONTACT_ADDED = "CONTACT_ADDED";
    public static final String CONTACT_UPDATED = "CONTACT_UPDATED";
    public static final String CONTACT_REMOVED = "CONTACT_REMOVED";
    public static final String ADDRESS_ADDED = "ADDRESS_ADDED";
    public static final String ADDRESS_UPDATED = "ADDRESS_UPDATED";
    public static final String ADDRESS_REMOVED = "ADDRESS_REMOVED";
    public static final String NOTE_ADDED = "NOTE_ADDED";
    public static final String CALL_LOGGED = "CALL_LOGGED";
    public static final String MEETING_COMPLETED = "MEETING_COMPLETED";
    public static final String EMAIL_LOGGED = "EMAIL_LOGGED";
    public static final String SITE_VISIT_COMPLETED = "SITE_VISIT_COMPLETED";
    public static final String MESSAGE_LOGGED = "MESSAGE_LOGGED";
    public static final String INTERACTION_LOGGED = "INTERACTION_LOGGED";

    // ---- reserved for modules not built yet
    public static final String LEAD_CREATED = "LEAD_CREATED";
    public static final String QUOTATION_SENT = "QUOTATION_SENT";
    public static final String QUOTATION_ACCEPTED = "QUOTATION_ACCEPTED";
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String PROJECT_STARTED = "PROJECT_STARTED";
    public static final String MILESTONE_COMPLETED = "MILESTONE_COMPLETED";
    public static final String INVOICE_GENERATED = "INVOICE_GENERATED";
    public static final String PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String TICKET_CREATED = "TICKET_CREATED";
    public static final String TICKET_RESOLVED = "TICKET_RESOLVED";
    public static final String AMC_CREATED = "AMC_CREATED";
    public static final String AMC_RENEWED = "AMC_RENEWED";
    public static final String PROOF_OF_WORK_RECORDED = "PROOF_OF_WORK_RECORDED";

    private TimelineEventTypes() {
    }
}
