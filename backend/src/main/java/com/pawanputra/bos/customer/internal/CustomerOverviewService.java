package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.api.AddressType;
import com.pawanputra.bos.customer.api.CustomerHealthContributor;
import com.pawanputra.bos.customer.api.CustomerHealthContributor.Impact;
import com.pawanputra.bos.customer.api.CustomerHealthContributor.Signal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The parts of the customer 360 that are worked out rather than stored: health and the summary facts.
 * Both are computed on request from current records, so they cannot go stale.
 */
@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(CustomerHealthProperties.class)
public class CustomerOverviewService {

    private final CustomerService customers;
    private final CustomerContactRepository contacts;
    private final CustomerAddressRepository addresses;
    private final CustomerNoteRepository notes;
    private final CustomerInteractionRepository interactions;
    private final List<CustomerHealthContributor> healthContributors;
    private final CustomerHealthProperties healthProperties;
    private final Clock clock;

    public CustomerOverviewService(
            CustomerService customers,
            CustomerContactRepository contacts,
            CustomerAddressRepository addresses,
            CustomerNoteRepository notes,
            CustomerInteractionRepository interactions,
            List<CustomerHealthContributor> healthContributors,
            CustomerHealthProperties healthProperties,
            Clock clock) {
        this.customers = customers;
        this.contacts = contacts;
        this.addresses = addresses;
        this.notes = notes;
        this.interactions = interactions;
        this.healthContributors = healthContributors;
        this.healthProperties = healthProperties;
        this.clock = clock;
    }

    public enum HealthLevel {
        /** Nothing wrong, and some evidence things are going well. */
        GOOD,
        /** Someone should look at one or more of the signals. */
        NEEDS_ATTENTION,
        /** At least one signal puts the relationship at risk. */
        AT_RISK,
        /** Too little is known to say. Not the same as "good". */
        UNKNOWN
    }

    /**
     * @param signals why: every signal that was found, most serious first. The level is the worst of them
     */
    public record Health(HealthLevel level, List<Signal> signals) {
    }

    public record PrimaryContact(String name, String designation, String phone, String email) {
    }

    /**
     * @param lastContactAt   the most recent call, meeting, email or visit; {@code null} if none is logged
     * @param billingAddress  the default billing address on one line; {@code null} if there is none
     * @param serviceAddress  the default service address on one line; {@code null} if there is none
     */
    public record Summary(
            Instant customerSince,
            Instant lastContactAt,
            String lastContactSubject,
            long contactCount,
            long addressCount,
            long interactionCount,
            long noteCount,
            PrimaryContact primaryContact,
            String billingAddress,
            String serviceAddress) {
    }

    public record Overview(Health health, Summary summary) {
    }

    public Overview overview(UUID organizationId, UUID customerId) {
        Customer customer = customers.load(organizationId, customerId);
        Optional<CustomerInteraction> lastContact =
                interactions.findFirstByCustomerIdOrderByOccurredAtDescIdDesc(customerId);
        List<CustomerContact> customerContacts = contacts.findAllByCustomerIdOrderByPrimaryContactDescNameAsc(customerId);

        boolean reachable = customer.getPhone() != null || customer.getEmail() != null
                || customerContacts.stream().anyMatch(c -> c.getPhone() != null || c.getEmail() != null);
        List<Signal> signals = new ArrayList<>(CustomerHealthRules.evaluate(
                customer.getStatus(), customer.getCreatedAt(),
                lastContact.map(CustomerInteraction::getOccurredAt).orElse(null), reachable, clock.instant(),
                healthProperties));
        for (CustomerHealthContributor contributor : healthContributors) {
            signals.addAll(contributor.signals(organizationId, customerId));
        }
        signals.sort(Comparator.comparing((Signal signal) -> signal.impact()).reversed());

        Summary summary = new Summary(
                customer.getCreatedAt(),
                lastContact.map(CustomerInteraction::getOccurredAt).orElse(null),
                lastContact.map(CustomerInteraction::getSubject).orElse(null),
                customerContacts.size(),
                addresses.countByCustomerId(customerId),
                interactions.countByCustomerId(customerId),
                notes.countByCustomerId(customerId),
                customerContacts.stream().filter(CustomerContact::isPrimaryContact).findFirst()
                        .map(c -> new PrimaryContact(c.getName(), c.getDesignation(), c.getPhone(), c.getEmail()))
                        .orElse(null),
                defaultAddress(customerId, AddressType.BILLING),
                defaultAddress(customerId, AddressType.SERVICE));
        return new Overview(new Health(level(signals), List.copyOf(signals)), summary);
    }

    /** The worst signal decides; with nothing but silence the honest answer is "unknown". */
    static HealthLevel level(List<Signal> signals) {
        if (signals.stream().anyMatch(signal -> signal.impact() == Impact.CRITICAL)) {
            return HealthLevel.AT_RISK;
        }
        if (signals.stream().anyMatch(signal -> signal.impact() == Impact.WARNING)) {
            return HealthLevel.NEEDS_ATTENTION;
        }
        if (signals.stream().anyMatch(signal -> signal.impact() == Impact.POSITIVE)) {
            return HealthLevel.GOOD;
        }
        return HealthLevel.UNKNOWN;
    }

    private String defaultAddress(UUID customerId, AddressType type) {
        return addresses.findByCustomerIdAndTypeAndDefaultAddressTrue(customerId, type)
                .map(a -> String.join(", ", java.util.stream.Stream.of(a.getLine1(), a.getLine2(), a.getCity(), a.getState())
                        .filter(part -> part != null && !part.isBlank()).toList())
                        + (a.getPostalCode() == null ? "" : " " + a.getPostalCode()))
                .orElse(null);
    }
}
