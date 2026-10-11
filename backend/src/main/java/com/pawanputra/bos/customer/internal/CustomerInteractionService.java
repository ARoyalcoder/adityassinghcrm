package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.customer.api.InteractionDirection;
import com.pawanputra.bos.customer.api.InteractionType;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.security.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The log of contact with a customer: calls, meetings, emails, site visits. */
@Service
@Transactional(readOnly = true)
public class CustomerInteractionService {

    /** Allows for a clock a little ahead on the user's device; anything later is a mistake. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private final CustomerService customers;
    private final CustomerInteractionRepository interactions;
    private final UserDirectory users;
    private final Clock clock;

    public CustomerInteractionService(
            CustomerService customers, CustomerInteractionRepository interactions, UserDirectory users, Clock clock) {
        this.customers = customers;
        this.interactions = interactions;
        this.users = users;
        this.clock = clock;
    }

    /**
     * @param occurredAt when it happened; {@code null} means now
     * @param direction  {@code null} when it does not apply
     */
    public record Details(
            InteractionType type, InteractionDirection direction, Instant occurredAt, String subject,
            String details, String outcome) {
    }

    public record View(
            UUID id, InteractionType type, InteractionDirection direction, Instant occurredAt, String subject,
            String details, String outcome, String loggedByName, Instant createdAt) {

        static View from(CustomerInteraction interaction) {
            return new View(
                    interaction.getId(), interaction.getType(), interaction.getDirection(),
                    interaction.getOccurredAt(), interaction.getSubject(), interaction.getDetails(),
                    interaction.getOutcome(), interaction.getLoggedByName(), interaction.getCreatedAt());
        }
    }

    public Page<View> list(UUID organizationId, UUID customerId, Pageable pageable) {
        customers.load(organizationId, customerId);
        return interactions.findAllByCustomerIdOrderByOccurredAtDescIdDesc(customerId, pageable).map(View::from);
    }

    @Transactional
    public View log(CurrentUser author, UUID customerId, Details details) {
        Customer customer = customers.load(author.organizationId(), customerId);
        Instant now = clock.instant();
        Instant occurredAt = details.occurredAt() == null ? now : details.occurredAt();
        if (occurredAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            // This is a record of what happened. Something planned for later is a follow-up, not an interaction.
            throw new BusinessRuleException("An interaction cannot be logged for a time in the future");
        }
        String loggedBy = users.find(author.organizationId(), author.id())
                .map(UserDirectory.UserSummary::fullName).orElse(author.email());
        return View.from(interactions.saveAndFlush(new CustomerInteraction(
                customer, details.type(), details.direction(), occurredAt, details.subject(), details.details(),
                details.outcome(), loggedBy)));
    }

    /** For an entry logged by mistake. The removal itself is kept in the audit log. */
    @Transactional
    public void remove(UUID organizationId, UUID customerId, UUID interactionId) {
        customers.load(organizationId, customerId);
        interactions.delete(interactions.findByIdAndCustomerId(interactionId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Interaction", interactionId)));
    }
}
