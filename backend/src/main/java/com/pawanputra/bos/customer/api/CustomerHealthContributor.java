package com.pawanputra.bos.customer.api;

import java.util.List;
import java.util.UUID;

/**
 * How a module tells the customer 360 something that bears on a customer's health. Declare a Spring
 * bean of this type; the health shown on the customer is the worst of all signals from all modules.
 *
 * <p>For example, billing would report {@code CRITICAL "2 invoices overdue by more than 60 days"} and
 * support would report {@code WARNING "3 open tickets"}. Return an empty list when there is nothing
 * to say. Signals are computed on request from the module's own records, never stored.
 */
public interface CustomerHealthContributor {

    enum Impact {
        /** Evidence the relationship is in good shape. */
        POSITIVE,
        /** Something someone should look at. */
        WARNING,
        /** Something that puts the relationship or the money at risk. */
        CRITICAL
    }

    /**
     * @param code    stable identifier of the rule: {@code "INVOICES_OVERDUE"}
     * @param message one sentence a salesperson can read: {@code "2 invoices overdue by more than 60 days"}
     */
    record Signal(String code, Impact impact, String message) {
    }

    List<Signal> signals(UUID organizationId, UUID customerId);
}
