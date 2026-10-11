package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.api.AddressType;
import com.pawanputra.bos.customer.api.CustomerSource;
import com.pawanputra.bos.customer.api.CustomerStatus;
import com.pawanputra.bos.customer.api.CustomerType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What the customer module shows to the outside. Built inside a transaction from the entities, so
 * that nothing lazy is touched after it ends and no entity ever reaches a controller.
 */
public final class CustomerViews {

    private CustomerViews() {
    }

    /** The employee a customer is assigned to. {@code name} is "Unknown user" if they no longer exist. */
    public record Assignee(UUID id, String name) {
    }

    /** One row of the customer list. */
    public record Summary(
            UUID id, String customerNumber, CustomerType type, String displayName, String email, String phone,
            CustomerStatus status, CustomerSource source, Assignee assignedTo, List<String> tags, Instant createdAt) {
    }

    /** Everything on the customer's Overview tab. The counts feed the labels of the other tabs. */
    public record Detail(
            UUID id,
            String customerNumber,
            CustomerType type,
            String displayName,
            String firstName,
            String lastName,
            String companyName,
            String taxId,
            String email,
            String phone,
            CustomerStatus status,
            CustomerSource source,
            Assignee assignedTo,
            List<String> tags,
            long contactCount,
            long addressCount,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record Contact(
            UUID id, String name, String designation, String email, String phone, boolean primaryContact,
            long version) {

        static Contact from(CustomerContact contact) {
            return new Contact(
                    contact.getId(), contact.getName(), contact.getDesignation(), contact.getEmail(),
                    contact.getPhone(), contact.isPrimaryContact(), contact.getVersion());
        }
    }

    public record Address(
            UUID id, AddressType type, String label, String line1, String line2, String city, String state,
            String postalCode, String countryCode, boolean defaultAddress, long version) {

        static Address from(CustomerAddress address) {
            return new Address(
                    address.getId(), address.getType(), address.getLabel(), address.getLine1(), address.getLine2(),
                    address.getCity(), address.getState(), address.getPostalCode(), address.getCountryCode(),
                    address.isDefaultAddress(), address.getVersion());
        }
    }

    public record Note(UUID id, String body, String authorName, Instant createdAt) {

        static Note from(CustomerNote note) {
            return new Note(note.getId(), note.getBody(), note.getAuthorName(), note.getCreatedAt());
        }
    }

    public record Tag(UUID id, String name) {
    }

    /** Why two customers look like the same one. */
    public enum MatchReason {
        PHONE,
        EMAIL,
        COMPANY_NAME
    }

    /**
     * An existing customer that may be the one being entered.
     *
     * @param matchedOn every reason it matched, e.g. the same phone number and the same company name
     */
    public record DuplicateMatch(
            UUID id, String customerNumber, String displayName, CustomerType type, String email, String phone,
            CustomerStatus status, Set<MatchReason> matchedOn) {
    }
}
