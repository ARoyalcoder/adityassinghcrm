package com.pawanputra.bos.customer.api;

import java.util.Optional;
import java.util.UUID;

/**
 * How other modules refer to customers. A module that bills or serves a customer stores the
 * customer's id and asks this directory whether the id is valid and what to print.
 * Every lookup is limited to one organization.
 */
public interface CustomerDirectory {

    /**
     * @param name           the name the customer is known by
     * @param taxId          GSTIN or other tax registration; may be {@code null}
     * @param billingAddress the default billing address on a few lines, or {@code null} if none is on record
     * @param blocked        the company has decided not to serve them
     */
    record CustomerSummary(
            UUID id, String customerNumber, String name, String taxId, String email, String phone,
            String billingAddress, boolean blocked) {
    }

    Optional<CustomerSummary> find(UUID organizationId, UUID customerId);
}
