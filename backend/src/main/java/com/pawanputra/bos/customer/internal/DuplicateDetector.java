package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.internal.CustomerViews.DuplicateMatch;
import com.pawanputra.bos.customer.internal.CustomerViews.MatchReason;
import com.pawanputra.bos.platform.persistence.Specs;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Finds existing customers that may be the same as the one being entered. Two customers are possible
 * duplicates if they share:
 * <ul>
 *   <li>a <b>phone</b> number, compared as digits (so formatting and +91 do not matter), on the
 *       customer or on any of its contacts;</li>
 *   <li>an <b>email</b> address, ignoring case, on the customer or on any of its contacts;</li>
 *   <li>a <b>company name</b>, ignoring case, punctuation and "Pvt Ltd"-style endings.</li>
 * </ul>
 * Only live customers of the same organization are considered. A match is a warning for a person to
 * judge, not proof: two people can share a landline, and a group can have several legal entities.
 */
@Component
class DuplicateDetector {

    private static final int MAX_MATCHES = 10;

    private final CustomerRepository customers;
    private final CustomerContactRepository contacts;

    DuplicateDetector(CustomerRepository customers, CustomerContactRepository contacts) {
        this.customers = customers;
        this.contacts = contacts;
    }

    /**
     * @param excludeCustomerId the customer being edited, which must not match itself; {@code null} when creating
     */
    List<DuplicateMatch> find(UUID organizationId, String phone, String email, String companyName, UUID excludeCustomerId) {
        String phoneKey = CustomerKeys.phone(phone);
        String emailKey = CustomerKeys.email(email);
        String companyKey = CustomerKeys.company(companyName);
        if (phoneKey == null && emailKey == null && companyKey == null) {
            return List.of();
        }

        Map<UUID, Customer> found = new LinkedHashMap<>();
        Map<UUID, Set<MatchReason>> reasons = new LinkedHashMap<>();

        List<Specification<Customer>> anyOf = new ArrayList<>();
        if (phoneKey != null) {
            anyOf.add(Specs.equalTo("phoneKey", phoneKey));
        }
        if (emailKey != null) {
            anyOf.add(Specs.equalTo("email", emailKey));
        }
        if (companyKey != null) {
            anyOf.add(Specs.equalTo("companyNameKey", companyKey));
        }
        Specification<Customer> spec = Specs.<Customer>equalTo("organizationId", organizationId)
                .and(Specification.anyOf(anyOf));
        for (Customer customer : customers.findAll(spec, PageRequest.of(0, MAX_MATCHES, Sort.by("displayName")))) {
            if (phoneKey != null && phoneKey.equals(customer.getPhoneKey())) {
                add(found, reasons, customer, MatchReason.PHONE);
            }
            if (emailKey != null && emailKey.equals(customer.getEmail())) {
                add(found, reasons, customer, MatchReason.EMAIL);
            }
            if (companyKey != null && companyKey.equals(customer.getCompanyNameKey())) {
                add(found, reasons, customer, MatchReason.COMPANY_NAME);
            }
        }
        if (phoneKey != null) {
            contacts.findByPhoneKey(organizationId, phoneKey)
                    .forEach(contact -> add(found, reasons, contact.getCustomer(), MatchReason.PHONE));
        }
        if (emailKey != null) {
            contacts.findByEmail(organizationId, emailKey)
                    .forEach(contact -> add(found, reasons, contact.getCustomer(), MatchReason.EMAIL));
        }

        return found.values().stream()
                .filter(customer -> !customer.getId().equals(excludeCustomerId))
                .limit(MAX_MATCHES)
                .map(customer -> new DuplicateMatch(
                        customer.getId(), customer.getCustomerNumber(), customer.getDisplayName(), customer.getType(),
                        customer.getEmail(), customer.getPhone(), customer.getStatus(), reasons.get(customer.getId())))
                .toList();
    }

    private static void add(
            Map<UUID, Customer> found, Map<UUID, Set<MatchReason>> reasons, Customer customer, MatchReason reason) {
        found.putIfAbsent(customer.getId(), customer);
        reasons.computeIfAbsent(customer.getId(), id -> EnumSet.noneOf(MatchReason.class)).add(reason);
    }
}
