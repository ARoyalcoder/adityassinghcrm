package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.customer.api.CustomerHealthContributor;
import com.pawanputra.bos.customer.api.CustomerHealthContributor.Impact;
import com.pawanputra.bos.customer.api.CustomerHealthContributor.Signal;
import com.pawanputra.bos.customer.api.CustomerTimelineSource;
import com.pawanputra.bos.finance.api.InvoiceStatus;
import com.pawanputra.bos.finance.api.PaymentStatus;
import com.pawanputra.bos.platform.timeline.TimelineCategory;
import com.pawanputra.bos.platform.timeline.TimelineEvent;
import com.pawanputra.bos.platform.timeline.TimelineEventTypes;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What finance contributes to the customer 360: invoices and payments on the customer's timeline,
 * and overdue money in the customer's health. Both are read from the finance records on request;
 * the customer module knows nothing about either.
 */
@Configuration
class FinanceCustomerSignals {

    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Bean
    CustomerTimelineSource invoiceTimelineSource(InvoiceRepository invoices) {
        return source((organizationId, customerId, notAfter, limit) -> invoices
                .findAllByOrganizationIdAndCustomerIdAndIssuedAtLessThanEqualOrderByIssuedAtDescIdDesc(
                        organizationId, customerId, upTo(notAfter), PageRequest.of(0, limit))
                .stream().map(FinanceCustomerSignals::issued).toList());
    }

    @Bean
    CustomerTimelineSource paymentTimelineSource(PaymentRepository payments) {
        return source((organizationId, customerId, notAfter, limit) -> payments
                .findAllByOrganizationIdAndCustomerIdAndCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(
                        organizationId, customerId, upTo(notAfter), PageRequest.of(0, limit))
                .stream().map(FinanceCustomerSignals::received).toList());
    }

    /** Overdue invoices count against a customer; long-overdue ones put the relationship at risk. */
    @Bean
    CustomerHealthContributor overdueInvoicesContributor(JdbcTemplate jdbc, FinanceContext context) {
        return (organizationId, customerId) -> {
            LocalDate today = context.today();
            return jdbc.query("""
                    SELECT count(*), coalesce(sum(total + debit_total - credit_total - amount_paid), 0), min(due_date)
                    FROM invoices
                    WHERE organization_id = ? AND customer_id = ? AND status IN ('ISSUED', 'PARTIALLY_PAID')
                      AND deleted_at IS NULL AND due_date < ?
                    """, row -> {
                        row.next();
                        long count = row.getLong(1);
                        if (count == 0) {
                            return List.<Signal>of();
                        }
                        long oldest = Ageing.daysOverdue(row.getObject(3, LocalDate.class), today);
                        boolean critical = oldest > context.properties().health().criticalAfterDays();
                        return List.of(new Signal("INVOICES_OVERDUE", critical ? Impact.CRITICAL : Impact.WARNING,
                                (count == 1 ? "1 invoice is overdue" : count + " invoices are overdue") + ": "
                                        + Money.rupees(row.getBigDecimal(2)) + " owing, the oldest by " + oldest
                                        + (oldest == 1 ? " day" : " days")));
                    }, organizationId, customerId, Date.valueOf(today));
        };
    }

    private static TimelineEvent issued(Invoice invoice) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Due", DAY.format(invoice.getDueDate()));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            details.put("Since", "Cancelled");
        }
        return new TimelineEvent(
                "invoice-issued:" + invoice.getId(), TimelineEventTypes.INVOICE_GENERATED, TimelineCategory.FINANCE,
                invoice.getIssuedAt(), "Invoice issued",
                invoice.getInvoiceNumber() + " for " + Money.rupees(invoice.getTotal()), invoice.getIssuedByName(),
                new TimelineEvent.Reference("Invoice", invoice.getId().toString()), details);
    }

    private static TimelineEvent received(Payment payment) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Method", payment.getMethodName());
        if (payment.getReference() != null) {
            details.put("Reference", payment.getReference());
        }
        if (payment.getStatus() == PaymentStatus.REVERSED) {
            details.put("Since", "Reversed: " + payment.getReversalReason());
        }
        return new TimelineEvent(
                "payment-received:" + payment.getId(), TimelineEventTypes.PAYMENT_RECEIVED, TimelineCategory.FINANCE,
                payment.getCreatedAt(), "Payment received",
                payment.getPaymentNumber() + " for " + Money.rupees(payment.getAmount()), payment.getRecordedByName(),
                new TimelineEvent.Reference("Payment", payment.getId().toString()), details);
    }

    private static Instant upTo(Instant notAfter) {
        return notAfter == null ? FAR_FUTURE : notAfter;
    }

    @FunctionalInterface
    private interface Events {
        List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit);
    }

    private static CustomerTimelineSource source(Events events) {
        return new CustomerTimelineSource() {
            @Override
            public Set<TimelineCategory> categories() {
                return Set.of(TimelineCategory.FINANCE);
            }

            @Override
            public List<TimelineEvent> events(UUID organizationId, UUID customerId, Instant notAfter, int limit) {
                return events.events(organizationId, customerId, notAfter, limit);
            }
        };
    }
}
