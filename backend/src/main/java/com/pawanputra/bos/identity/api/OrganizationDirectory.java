package com.pawanputra.bos.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * What the company calls itself on a document it issues: an invoice, a receipt, a quotation.
 * A module that prints such a document asks here instead of reading the organization tables.
 */
public interface OrganizationDirectory {

    /**
     * @param name    the legal name if one is on record, otherwise the everyday name
     * @param taxId   GSTIN or other tax registration; may be {@code null}
     * @param address the head office address on a few lines, or {@code null} if none is on record
     */
    record Letterhead(String name, String taxId, String email, String phone, String address) {
    }

    Optional<Letterhead> letterhead(UUID organizationId);
}
