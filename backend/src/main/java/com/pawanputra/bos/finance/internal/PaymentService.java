package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.customer.api.CustomerDirectory.CustomerSummary;
import com.pawanputra.bos.finance.api.PaymentIntake;
import com.pawanputra.bos.finance.api.PaymentStatus;
import com.pawanputra.bos.finance.internal.AllocationPlanner.Open;
import com.pawanputra.bos.finance.internal.AllocationPlanner.Share;
import com.pawanputra.bos.finance.internal.FinanceContext.Actor;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.persistence.Specs;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments: recording money received, saying which invoices it settles, and putting right what was
 * recorded wrongly.
 *
 * <p>Nothing here edits or deletes. An allocation made to the wrong invoice is released (a second
 * row that undoes the first) and made again. Money that should go back is refunded out of what is
 * unallocated. A payment that should never have been recorded is reversed and stays on file.
 *
 * <p>Locks are always taken in the same order, the payment first and then its invoices by id, so
 * two of these operations can wait for each other but never deadlock.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    static final int MAX_ALLOCATIONS = 100;
    private static final String SCOPE_RECORD = "payments.record";
    private static final String SCOPE_ALLOCATE = "payments.allocate";
    private static final String SCOPE_REFUND = "payments.refund";

    private final PaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final RefundRepository refunds;
    private final InvoiceRepository invoices;
    private final PaymentMethodService methods;
    private final JdbcTemplate jdbc;
    private final DocumentNumbers numbers;
    private final Idempotency idempotency;
    private final FinanceContext context;

    PaymentService(
            PaymentRepository payments, PaymentAllocationRepository allocations, RefundRepository refunds,
            InvoiceRepository invoices, PaymentMethodService methods, JdbcTemplate jdbc, DocumentNumbers numbers,
            Idempotency idempotency, FinanceContext context) {
        this.payments = payments;
        this.allocations = allocations;
        this.refunds = refunds;
        this.invoices = invoices;
        this.methods = methods;
        this.jdbc = jdbc;
        this.numbers = numbers;
        this.idempotency = idempotency;
        this.context = context;
    }

    public record AllocationInput(UUID invoiceId, BigDecimal amount) {
    }

    /**
     * @param receivedOn   {@code null} for today
     * @param allocations  which invoices the money settles, and by how much; may be empty (an advance)
     * @param autoAllocate settle the customer's invoices oldest first instead of naming them
     */
    public record PaymentInput(
            UUID customerId, BigDecimal amount, LocalDate receivedOn, UUID methodId, String reference, String notes,
            List<AllocationInput> allocations, boolean autoAllocate) {
    }

    /** @param refundDate {@code null} for today */
    public record RefundInput(BigDecimal amount, LocalDate refundDate, UUID methodId, String reference, String reason) {
    }

    /** @param unallocatedOnly only payments with money still to allocate or refund */
    public record Filter(String search, PaymentStatus status, UUID customerId, boolean unallocatedOnly) {
    }

    /** @param unallocated what is left to allocate or refund */
    public record Summary(
            UUID id, String paymentNumber, PaymentStatus status, UUID customerId, String customerName,
            LocalDate receivedOn, String currency, BigDecimal amount, BigDecimal allocated, BigDecimal refunded,
            BigDecimal unallocated, String methodName, String reference) {
    }

    /** @param released the allocation was later taken back, and no longer counts */
    public record Allocation(
            UUID id, UUID invoiceId, String invoiceNumber, BigDecimal amount, boolean released, String releaseReason,
            String allocatedByName, Instant allocatedAt) {
    }

    public record RefundView(
            UUID id, String refundNumber, LocalDate refundDate, BigDecimal amount, String methodName, String reference,
            String reason, String refundedByName, Instant createdAt) {
    }

    /** What the person asking may do with this payment as it stands. */
    public record Actions(boolean allocate, boolean release, boolean refund, boolean reverse) {
    }

    public record Detail(
            Summary payment, String notes, String source, String gateway, String recordedByName, Instant recordedAt,
            Instant reversedAt, String reversedByName, String reversalReason, List<Allocation> allocations,
            List<RefundView> refunds, Actions actions) {
    }

    /** @param replayed this request had been made before; the payment is the one the first request recorded */
    public record Recorded(Detail detail, boolean replayed) {
    }

    // ------------------------------------------------------------------ reading

    public Page<Summary> search(Actor actor, Filter filter, Pageable pageable) {
        Specification<Payment> spec = Specs.<Payment>equalTo("organizationId", actor.organizationId())
                .and(Specs.search(filter.search(), "paymentNumber", "customerName", "reference"))
                .and(Specs.equalTo("status", filter.status()))
                .and(Specs.equalTo("customerId", filter.customerId()));
        if (filter.unallocatedOnly()) {
            spec = spec.and((root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), PaymentStatus.RECEIVED),
                    cb.greaterThan(root.<BigDecimal>get("amount"),
                            cb.sum(root.<BigDecimal>get("allocatedAmount"), root.<BigDecimal>get("refundedAmount")))));
        }
        return payments.findAll(spec, pageable).map(PaymentService::summary);
    }

    public Detail get(Actor actor, UUID id) {
        return detail(actor, payments.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id)));
    }

    // ------------------------------------------------------------------ recording

    /**
     * Records money received and, in the same transaction, what it settles.
     *
     * @param idempotencyKey if given, a repeat of this request returns the payment the first one
     *                       recorded instead of recording another
     */
    @Transactional
    public Recorded record(Actor actor, PaymentInput input, String idempotencyKey) {
        UUID organizationId = actor.organizationId();
        String fingerprint = Idempotency.fingerprint(input.customerId(), input.amount(), input.receivedOn(),
                input.methodId(), input.reference(), input.autoAllocate(), fingerprintOf(input.allocations()));
        var earlier = idempotency.claim(organizationId, SCOPE_RECORD, idempotencyKey, fingerprint);
        if (earlier.isPresent()) {
            return new Recorded(get(actor, earlier.get()), true);
        }

        CustomerSummary customer = context.customer(organizationId, input.customerId());
        PaymentMethod method = methods.usable(organizationId, input.methodId());
        if (method.isRequiresReference() && input.reference() == null) {
            throw new FieldValidationException("reference", "A reference is required for " + method.getName());
        }
        LocalDate receivedOn = input.receivedOn() != null ? input.receivedOn() : context.today();
        context.requireNotInFuture("receivedOn", receivedOn);
        BigDecimal amount = Money.of(input.amount());

        List<Share> shares;
        if (input.autoAllocate()) {
            // Lock everything the customer owes before deciding who gets what.
            shares = AllocationPlanner.oldestFirst(amount,
                    invoices.lockAllByCustomer(organizationId, customer.id(), InvoiceService.OPEN).stream()
                            .map(invoice -> new Open(invoice.getId(), invoice.getInvoiceNumber(), invoice.getDueDate(),
                                    invoice.getOutstanding()))
                            .toList());
        } else {
            shares = shares(input.allocations());
        }

        List<Planned> planned = plan(organizationId, customer.id(), amount, shares);
        String name = context.nameOf(actor);
        Payment payment = payments.save(new Payment(
                organizationId, numbers.next(organizationId, DocumentType.PAYMENT, receivedOn), customer.id(),
                customer.name(), receivedOn, amount, context.properties().currency(), method.getId(), method.getName(),
                input.reference(), input.notes(), name));
        apply(payment, planned, name);
        payment = payments.saveAndFlush(payment);
        idempotency.complete(organizationId, SCOPE_RECORD, idempotencyKey, "Payment", payment.getId());
        return new Recorded(detail(actor, payment), false);
    }

    /** Puts more of a payment's unallocated money against invoices. */
    @Transactional
    public Recorded allocate(Actor actor, UUID id, List<AllocationInput> requested, String idempotencyKey) {
        UUID organizationId = actor.organizationId();
        var earlier = idempotency.claim(organizationId, SCOPE_ALLOCATE, idempotencyKey,
                Idempotency.fingerprint(id, fingerprintOf(requested)));
        if (earlier.isPresent()) {
            return new Recorded(get(actor, id), true);
        }
        Payment payment = lock(actor, id);
        requireReceived(payment);
        apply(payment, plan(organizationId, payment.getCustomerId(), payment.getUnallocated(), shares(requested)),
                context.nameOf(actor));
        payment = payments.saveAndFlush(payment);
        idempotency.complete(organizationId, SCOPE_ALLOCATE, idempotencyKey, "Payment", payment.getId());
        return new Recorded(detail(actor, payment), false);
    }

    /**
     * Records a payment a gateway has collected. Runs inside {@link GatewayPaymentIntake}'s
     * transaction, after it has established that the gateway has not reported this payment before.
     *
     * <p>Unlike a payment typed in by a person, this never fails over the invoice: the money has
     * arrived whatever state the invoice is in. As much as the invoice can still take is allocated
     * to it, and the rest is left unallocated for someone to deal with.
     */
    Payment recordFromGateway(PaymentIntake.GatewayPayment reported) {
        UUID organizationId = reported.organizationId();
        CustomerSummary customer = context.customer(organizationId, reported.customerId());
        PaymentMethod method = methods.usableByCode(organizationId, reported.methodCode());
        LocalDate receivedOn = reported.receivedOn() != null ? reported.receivedOn() : context.today();
        BigDecimal amount = Money.of(reported.amount());
        List<Planned> planned = List.of();
        if (reported.invoiceId() != null) {
            Invoice invoice = invoices.lockByIdAndOrganizationId(reported.invoiceId(), organizationId).orElse(null);
            if (invoice != null && invoice.getCustomerId().equals(customer.id())
                    && InvoiceService.OPEN.contains(invoice.getStatus())) {
                planned = List.of(new Planned(invoice, amount.min(invoice.getOutstanding())));
            }
        }
        String name = "Payment gateway (" + reported.gateway() + ")";
        Payment payment = new Payment(
                organizationId, numbers.next(organizationId, DocumentType.PAYMENT, receivedOn), customer.id(),
                customer.name(), receivedOn, amount, context.properties().currency(), method.getId(), method.getName(),
                reported.reference(), null, name);
        payment.fromGateway(reported.gateway(), reported.gatewayPaymentId());
        payment = payments.save(payment);
        apply(payment, planned, name);
        return payments.saveAndFlush(payment);
    }

    // ------------------------------------------------------------------ putting things right

    /** Takes an allocation back: the invoice is owed that much again and the payment has it to use. */
    @Transactional
    public Detail release(Actor actor, UUID id, UUID allocationId, String reason) {
        Payment payment = lock(actor, id);
        requireReceived(payment);
        PaymentAllocation allocation = allocations.findByIdAndPaymentId(allocationId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation", allocationId));
        if (allocation.getAmount().signum() < 0 || allocations.existsByReversesId(allocationId)) {
            throw new BusinessRuleException("This allocation has already been released");
        }
        release(payment, allocation, reason, context.nameOf(actor));
        return detail(actor, payments.saveAndFlush(payment));
    }

    /** Returns unallocated money to the customer. */
    @Transactional
    public Recorded refund(Actor actor, UUID id, RefundInput input, String idempotencyKey) {
        UUID organizationId = actor.organizationId();
        var earlier = idempotency.claim(organizationId, SCOPE_REFUND, idempotencyKey, Idempotency.fingerprint(
                id, input.amount(), input.refundDate(), input.methodId(), input.reference(), input.reason()));
        if (earlier.isPresent()) {
            return new Recorded(get(actor, id), true);
        }
        Payment payment = lock(actor, id);
        requireReceived(payment);
        BigDecimal amount = Money.of(input.amount());
        if (amount.compareTo(payment.getUnallocated()) > 0) {
            throw new BusinessRuleException("Only " + Money.rupees(payment.getUnallocated()) + " of payment "
                    + payment.getPaymentNumber() + " is unallocated. To refund more, release that much from its "
                    + "invoices first.");
        }
        PaymentMethod method = methods.usable(organizationId, input.methodId());
        if (method.isRequiresReference() && input.reference() == null) {
            throw new FieldValidationException("reference", "A reference is required for " + method.getName());
        }
        LocalDate date = input.refundDate() != null ? input.refundDate() : context.today();
        context.requireNotInFuture("refundDate", date);
        if (date.isBefore(payment.getReceivedOn())) {
            throw new FieldValidationException("refundDate", "must not be before the payment was received");
        }
        Refund refund = refunds.save(new Refund(
                organizationId, numbers.next(organizationId, DocumentType.REFUND, date), id, payment.getCustomerId(),
                date, amount, method.getId(), method.getName(), input.reference(), input.reason(),
                context.nameOf(actor)));
        payment.refund(amount);
        payment = payments.saveAndFlush(payment);
        idempotency.complete(organizationId, SCOPE_REFUND, idempotencyKey, "Refund", refund.getId());
        return new Recorded(detail(actor, payment), false);
    }

    /**
     * Undoes a payment that should not stand: a bounced cheque, an entry made by mistake. Every
     * allocation is released, so the invoices it settled are owed again. The payment stays on
     * record, marked reversed, with the reason.
     */
    @Transactional
    public Detail reverse(Actor actor, UUID id, String reason) {
        Payment payment = lock(actor, id);
        requireReceived(payment);
        if (payment.getRefundedAmount().signum() > 0) {
            throw new BusinessRuleException("Payment " + payment.getPaymentNumber()
                    + " has been partly refunded and cannot be reversed");
        }
        String name = context.nameOf(actor);
        for (PaymentAllocation allocation : allocations.findActiveByPaymentId(id)) {
            release(payment, allocation, "Payment reversed: " + reason, name);
        }
        payment.reverse(context.now(), name, reason);
        return detail(actor, payments.saveAndFlush(payment));
    }

    // ------------------------------------------------------------------ helpers

    private Payment lock(Actor actor, UUID id) {
        return payments.lockByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    private static void requireReceived(Payment payment) {
        if (payment.getStatus() != PaymentStatus.RECEIVED) {
            throw new BusinessRuleException("Payment " + payment.getPaymentNumber() + " has been reversed");
        }
    }

    /** What was asked for, checked for shape: each invoice once, in the order the invoices are locked. */
    private static List<Share> shares(List<AllocationInput> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        if (requested.size() > MAX_ALLOCATIONS) {
            throw new FieldValidationException("allocations", "At most " + MAX_ALLOCATIONS + " invoices at a time");
        }
        Set<UUID> seen = new HashSet<>();
        for (AllocationInput allocation : requested) {
            if (!seen.add(allocation.invoiceId())) {
                throw new FieldValidationException("allocations", "The same invoice is listed twice");
            }
        }
        return requested.stream().map(allocation -> new Share(allocation.invoiceId(), Money.of(allocation.amount())))
                .sorted(Comparator.comparing(Share::invoiceId)).toList();
    }

    private static String fingerprintOf(List<AllocationInput> requested) {
        if (requested == null) {
            return "";
        }
        return requested.stream().sorted(Comparator.comparing(AllocationInput::invoiceId))
                .map(allocation -> allocation.invoiceId() + "=" + allocation.amount().stripTrailingZeros().toPlainString())
                .reduce("", (left, right) -> left + ";" + right);
    }

    /** An invoice, locked and checked, and how much of the payment goes to it. */
    private record Planned(Invoice invoice, BigDecimal amount) {
    }

    /**
     * Locks each invoice and checks that it can take its share: it must be the same customer's,
     * still owing, and owing at least this much. Changes nothing, so it runs before the payment is
     * numbered: a request that fails here has used no number.
     *
     * @param available how much of the payment there is to allocate
     */
    private List<Planned> plan(UUID organizationId, UUID customerId, BigDecimal available, List<Share> shares) {
        BigDecimal asked = shares.stream().map(Share::amount).reduce(Money.ZERO, BigDecimal::add);
        if (asked.compareTo(available) > 0) {
            throw new FieldValidationException("allocations", "The allocations add up to " + Money.rupees(asked)
                    + ", but only " + Money.rupees(available) + " of the payment is unallocated");
        }
        List<Planned> planned = new ArrayList<>();
        for (Share share : shares) {
            Invoice invoice = invoices.lockByIdAndOrganizationId(share.invoiceId(), organizationId)
                    .orElseThrow(() -> new FieldValidationException("allocations", "One of the invoices was not found"));
            if (!invoice.getCustomerId().equals(customerId)) {
                throw new BusinessRuleException("Invoice " + number(invoice) + " belongs to another customer");
            }
            if (!InvoiceService.OPEN.contains(invoice.getStatus())) {
                throw new BusinessRuleException("Invoice " + number(invoice) + " is "
                        + InvoiceService.words(invoice.getStatus()) + " and cannot take a payment");
            }
            if (share.amount().compareTo(invoice.getOutstanding()) > 0) {
                throw new BusinessRuleException("Invoice " + number(invoice) + " has only "
                        + Money.rupees(invoice.getOutstanding()) + " outstanding");
            }
            planned.add(new Planned(invoice, share.amount()));
        }
        return planned;
    }

    /** Records the allocations and moves the running totals of the payment and of each invoice. */
    private void apply(Payment payment, List<Planned> planned, String byName) {
        for (Planned share : planned) {
            allocations.save(new PaymentAllocation(
                    payment.getOrganizationId(), payment.getId(), share.invoice().getId(), share.amount(), byName));
            share.invoice().applyPayment(share.amount());
            payment.allocate(share.amount());
        }
    }

    private void release(Payment payment, PaymentAllocation allocation, String reason, String byName) {
        Invoice invoice = invoices.lockByIdAndOrganizationId(allocation.getInvoiceId(), payment.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", allocation.getInvoiceId()));
        allocations.save(allocation.release(reason, byName));
        invoice.applyPayment(allocation.getAmount().negate());
        payment.allocate(allocation.getAmount().negate());
    }

    private static String number(Invoice invoice) {
        return invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "(draft)";
    }

    static Summary summary(Payment payment) {
        return new Summary(
                payment.getId(), payment.getPaymentNumber(), payment.getStatus(), payment.getCustomerId(),
                payment.getCustomerName(), payment.getReceivedOn(), payment.getCurrency(), payment.getAmount(),
                payment.getAllocatedAmount(), payment.getRefundedAmount(), payment.getUnallocated(),
                payment.getMethodName(), payment.getReference());
    }

    private Detail detail(Actor actor, Payment payment) {
        UUID id = payment.getId();
        List<Allocation> made = jdbc.query("""
                SELECT a.id, i.id, i.invoice_number, a.amount, r.id IS NOT NULL, r.reason, a.allocated_by_name, a.created_at
                FROM payment_allocations a
                JOIN invoices i ON i.id = a.invoice_id
                LEFT JOIN payment_allocations r ON r.reverses_id = a.id
                WHERE a.payment_id = ? AND a.amount > 0
                ORDER BY a.created_at, a.id
                """, (row, index) -> new Allocation(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getString(3), row.getBigDecimal(4), row.getBoolean(5), row.getString(6), row.getString(7),
                instant(row.getTimestamp(8))), id);
        List<RefundView> returned = refunds.findAllByPaymentIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(refund -> new RefundView(refund.getId(), refund.getRefundNumber(), refund.getRefundDate(),
                        refund.getAmount(), refund.getMethodName(), refund.getReference(), refund.getReason(),
                        refund.getRefundedByName(), refund.getCreatedAt()))
                .toList();
        boolean received = payment.getStatus() == PaymentStatus.RECEIVED;
        boolean spare = payment.getUnallocated().signum() > 0;
        Actions actions = new Actions(
                actor.manage() && received && spare,
                actor.approve() && received && payment.getAllocatedAmount().signum() > 0,
                actor.approve() && received && spare,
                actor.approve() && received && payment.getRefundedAmount().signum() == 0);
        return new Detail(
                summary(payment), payment.getNotes(), payment.getSource(), payment.getGateway(),
                payment.getRecordedByName(), payment.getCreatedAt(), payment.getReversedAt(),
                payment.getReversedByName(), payment.getReversalReason(), made, returned, actions);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
