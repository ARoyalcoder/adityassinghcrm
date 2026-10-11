package com.pawanputra.bos.customer.web;

import com.pawanputra.bos.customer.api.AddressType;
import com.pawanputra.bos.customer.api.CustomerSource;
import com.pawanputra.bos.customer.api.CustomerStatus;
import com.pawanputra.bos.customer.api.CustomerType;
import com.pawanputra.bos.customer.api.InteractionDirection;
import com.pawanputra.bos.customer.api.InteractionType;
import com.pawanputra.bos.customer.internal.CustomerInteractionService;
import com.pawanputra.bos.customer.internal.CustomerPartsService;
import com.pawanputra.bos.customer.internal.CustomerService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request bodies of the customer endpoints. (Responses are the views built by the services.) */
final class CustomerDtos {

    private static final String PHONE_PATTERN = "^[+0-9][0-9 ()-]{5,28}$";
    private static final String PHONE_MESSAGE = "must be a phone number";

    private CustomerDtos() {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Create or update a customer.
     *
     * @param confirmDuplicates send {@code true} to save even though existing customers share the phone,
     *                          email or company name (after showing them to the user)
     * @param version           required when updating: the version that was loaded
     */
    record CustomerRequest(
            @NotNull CustomerType type,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 200) String companyName,
            @Size(max = 30) String taxId,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone,
            CustomerStatus status,
            CustomerSource source,
            UUID assignedUserId,
            @Size(max = 20) List<@NotBlank @Size(max = 50) String> tags,
            Boolean confirmDuplicates,
            Long version) {

        CustomerService.Details details() {
            return new CustomerService.Details(
                    type, trimToNull(firstName), trimToNull(lastName), trimToNull(companyName), trimToNull(taxId),
                    trimToNull(email), trimToNull(phone), status == null ? CustomerStatus.ACTIVE : status, source,
                    assignedUserId, tags == null ? List.of() : tags);
        }

        boolean duplicatesConfirmed() {
            return Boolean.TRUE.equals(confirmDuplicates);
        }
    }

    /** What to compare against existing customers. {@code excludeCustomerId} is the customer being edited, if any. */
    record DuplicateCheckRequest(
            @Size(max = 30) String phone,
            @Size(max = 254) String email,
            @Size(max = 200) String companyName,
            UUID excludeCustomerId) {
    }

    record ContactRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 100) String designation,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone,
            Boolean primaryContact,
            Long version) {

        CustomerPartsService.ContactDetails details() {
            return new CustomerPartsService.ContactDetails(
                    name.trim(), trimToNull(designation), trimToNull(email), trimToNull(phone),
                    Boolean.TRUE.equals(primaryContact));
        }
    }

    record AddressRequest(
            @NotNull AddressType type,
            @Size(max = 100) String label,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @Size(max = 20) String postalCode,
            @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two-letter country code") String countryCode,
            Boolean defaultAddress,
            Long version) {

        CustomerPartsService.AddressDetails details() {
            return new CustomerPartsService.AddressDetails(
                    type, trimToNull(label), line1.trim(), trimToNull(line2), city.trim(), state.trim(),
                    trimToNull(postalCode), countryCode == null ? "IN" : countryCode,
                    Boolean.TRUE.equals(defaultAddress));
        }
    }

    record NoteRequest(@NotBlank @Size(max = 4000) String body) {
    }

    /**
     * Log a call, meeting, email or visit.
     *
     * @param occurredAt when it happened (ISO-8601); leave out for "just now". Not in the future
     */
    record InteractionRequest(
            @NotNull InteractionType type,
            InteractionDirection direction,
            Instant occurredAt,
            @NotBlank @Size(max = 200) String subject,
            @Size(max = 4000) String details,
            @Size(max = 500) String outcome) {

        CustomerInteractionService.Details toDetails() {
            return new CustomerInteractionService.Details(
                    type, direction, occurredAt, subject.trim(), trimToNull(details), trimToNull(outcome));
        }
    }
}
