package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.customer.api.CustomerDirectory;
import com.pawanputra.bos.customer.api.CustomerDirectory.CustomerSummary;
import com.pawanputra.bos.identity.api.UserDirectory;
import com.pawanputra.bos.identity.api.UserDirectory.UserSummary;
import com.pawanputra.bos.platform.error.FieldValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** What every finance service needs to know about the world outside it: the date, who is asking, who the customer is. */
@Component
@EnableConfigurationProperties(FinanceProperties.class)
public class FinanceContext {

    private final Clock clock;
    private final FinanceProperties properties;
    private final UserDirectory users;
    private final CustomerDirectory customers;

    FinanceContext(Clock clock, FinanceProperties properties, UserDirectory users, CustomerDirectory customers) {
        this.clock = clock;
        this.properties = properties;
        this.users = users;
        this.customers = customers;
    }

    /**
     * Who is asking, and what they may do with the books.
     *
     * @param manage  creates and issues invoices, records and allocates payments
     * @param approve takes money back off the books: notes, refunds, cancellations, reversals
     */
    public record Actor(UUID id, UUID organizationId, String email, boolean manage, boolean approve) {
    }

    public FinanceProperties properties() {
        return properties;
    }

    public Instant now() {
        return clock.instant();
    }

    /** Today's date where the business is, which is what an invoice or a receipt is dated by. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(properties.timeZone()));
    }

    /** The person's name as it should appear on a record they make. */
    String nameOf(Actor actor) {
        return users.find(actor.organizationId(), actor.id()).map(UserSummary::fullName).orElse(actor.email());
    }

    /** The customer, or a validation error on {@code customerId} if there is no such customer in the organization. */
    CustomerSummary customer(UUID organizationId, UUID customerId) {
        return customers.find(organizationId, customerId)
                .orElseThrow(() -> new FieldValidationException("customerId", "Choose a customer"));
    }

    /** A date on a document may not be in the future: the books record what has happened. */
    void requireNotInFuture(String field, LocalDate date) {
        if (date.isAfter(today())) {
            throw new FieldValidationException(field, "must not be in the future");
        }
    }
}
