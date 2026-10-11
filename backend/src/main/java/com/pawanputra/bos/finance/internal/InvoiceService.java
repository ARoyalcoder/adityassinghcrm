package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.customer.api.CustomerDirectory.CustomerSummary;
import com.pawanputra.bos.finance.api.InvoiceStatus;
import com.pawanputra.bos.finance.internal.FinanceContext.Actor;
import com.pawanputra.bos.identity.api.OrganizationDirectory;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ConflictException;
import com.pawanputra.bos.platform.error.FieldValidationException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.persistence.Specs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoices: preparing a draft, issuing it, and correcting it afterwards.
 *
 * <p>A draft is free to change. Issuing gives it a number and freezes it; from then on the only way
 * to change what the customer owes is a credit note (less) or a debit note (more), and the only way
 * to withdraw it is to cancel it, which keeps it on record. Whatever moves an invoice's amounts
 * first locks the invoice row, so nothing is decided on figures that are about to change.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceService {

    /** Stored statuses of an invoice that still has money owing. */
    static final Set<InvoiceStatus> OPEN = Set.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID);

    static final int MAX_ITEMS = 100;
    private static final String SCOPE_CREDIT_NOTE = "invoices.credit-note";
    private static final String SCOPE_DEBIT_NOTE = "invoices.debit-note";

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final CreditNoteRepository creditNotes;
    private final DebitNoteRepository debitNotes;
    private final JdbcTemplate jdbc;
    private final DocumentNumbers numbers;
    private final Idempotency idempotency;
    private final FinanceContext context;
    private final OrganizationDirectory organizations;
    private final EntityManager entityManager;

    InvoiceService(
            InvoiceRepository invoices, InvoiceItemRepository items, CreditNoteRepository creditNotes,
            DebitNoteRepository debitNotes, JdbcTemplate jdbc, DocumentNumbers numbers, Idempotency idempotency,
            FinanceContext context, OrganizationDirectory organizations, EntityManager entityManager) {
        this.invoices = invoices;
        this.items = items;
        this.creditNotes = creditNotes;
        this.debitNotes = debitNotes;
        this.jdbc = jdbc;
        this.numbers = numbers;
        this.idempotency = idempotency;
        this.context = context;
        this.organizations = organizations;
        this.entityManager = entityManager;
    }

    /** One line as typed in. Percentages left out count as zero. */
    public record ItemInput(
            UUID serviceId, String description, String hsnSac, BigDecimal quantity, String unit, BigDecimal unitPrice,
            BigDecimal discountPercent, BigDecimal taxPercent) {
    }

    /**
     * What goes on a draft. A billing address or tax id left out is taken from the customer record;
     * payment terms left out are the configured default.
     */
    public record DraftInput(
            UUID customerId, String billingAddress, String customerTaxId, String reference, Integer paymentTermsDays,
            String notes, String terms, List<ItemInput> items) {
    }

    /** @param noteDate {@code null} for today */
    public record NoteInput(BigDecimal amount, LocalDate noteDate, String reason) {
    }

    /**
     * @param status          as shown: {@code OVERDUE} matches open invoices past their due date, and
     *                        {@code ISSUED} / {@code PARTIALLY_PAID} only those not yet past it
     * @param outstandingOnly only invoices that still have money owing, overdue or not
     */
    public record Filter(String search, InvoiceStatus status, UUID customerId, boolean outstandingOnly) {
    }

    /**
     * @param status      as it stands today, so {@code OVERDUE} where that applies
     * @param outstanding what is still owed; zero for a draft or a cancelled invoice
     * @param daysOverdue days past the due date; {@code null} unless the invoice is overdue
     */
    public record Summary(
            UUID id, String invoiceNumber, InvoiceStatus status, UUID customerId, String customerName,
            LocalDate issueDate, LocalDate dueDate, String currency, BigDecimal total, BigDecimal amountPaid,
            BigDecimal outstanding, Long daysOverdue) {
    }

    public record Item(
            UUID id, UUID serviceId, String description, String hsnSac, BigDecimal quantity, String unit,
            BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal taxPercent, BigDecimal lineSubtotal,
            BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal lineTotal) {
    }

    /** A payment allocated to the invoice. @param released the allocation was later taken back, and no longer counts */
    public record Settlement(
            UUID allocationId, UUID paymentId, String paymentNumber, LocalDate receivedOn, String methodName,
            BigDecimal amount, boolean released, String allocatedByName, Instant allocatedAt) {
    }

    public record Note(
            UUID id, String noteNumber, LocalDate noteDate, BigDecimal amount, String reason, String issuedByName,
            Instant createdAt) {
    }

    /** The company's own details, for the head of the printed invoice. */
    public record Seller(String name, String taxId, String email, String phone, String address) {
    }

    /** What the person asking may do with this invoice as it stands. */
    public record Actions(
            boolean edit, boolean issue, boolean discard, boolean recordPayment, boolean creditNote, boolean debitNote,
            boolean cancel) {
    }

    public record Detail(
            Summary invoice, String billingAddress, String customerTaxId, String reference, int paymentTermsDays,
            String notes, String terms, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal,
            BigDecimal creditTotal, BigDecimal debitTotal, List<Item> items, List<Settlement> payments,
            List<Note> creditNotes, List<Note> debitNotes, Seller seller, Instant issuedAt, String issuedByName,
            Instant cancelledAt, String cancellationReason, Actions actions, long version) {
    }

    /** @param replayed this request had been made before; the invoice is as the first one left it */
    public record Noted(Detail detail, boolean replayed) {
    }

    public record LineAmounts(BigDecimal lineSubtotal, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal lineTotal) {
    }

    public record Preview(
            List<LineAmounts> lines, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total) {
    }

    // ------------------------------------------------------------------ reading

    public Page<Summary> search(Actor actor, Filter filter, Pageable pageable) {
        LocalDate today = context.today();
        Specification<Invoice> spec = Specs.<Invoice>equalTo("organizationId", actor.organizationId())
                .and(Specs.search(filter.search(), "invoiceNumber", "customerName", "reference"))
                .and(Specs.equalTo("customerId", filter.customerId()))
                .and(shownAs(filter.status(), today));
        if (filter.outstandingOnly()) {
            spec = spec.and((root, query, cb) -> root.get("status").in(OPEN));
        }
        return invoices.findAll(spec, pageable).map(invoice -> summary(invoice, today));
    }

    public Detail get(Actor actor, UUID id) {
        return detail(actor, load(actor, id));
    }

    /** A customer's invoices that can take a payment, the one due earliest first. */
    public List<Summary> openFor(Actor actor, UUID customerId) {
        LocalDate today = context.today();
        return invoices.findAllByOrganizationIdAndCustomerIdAndStatusInOrderByDueDateAscInvoiceNumberAsc(
                        actor.organizationId(), customerId, OPEN)
                .stream().map(invoice -> summary(invoice, today)).toList();
    }

    /** What the given lines add up to, without saving anything. */
    public Preview preview(List<ItemInput> lines) {
        List<InvoiceMath.Line> amounts = amounts(lines);
        InvoiceMath.Totals totals = InvoiceMath.totals(amounts);
        return new Preview(
                amounts.stream().map(line -> new LineAmounts(line.subtotal(), line.discount(), line.tax(), line.total()))
                        .toList(),
                totals.subtotal(), totals.discountTotal(), totals.taxTotal(), totals.total());
    }

    // ------------------------------------------------------------------ drafts

    @Transactional
    public Detail create(Actor actor, DraftInput input) {
        Invoice invoice = new Invoice(actor.organizationId(), context.properties().currency());
        List<InvoiceMath.Line> amounts = apply(invoice, input);
        invoice = invoices.saveAndFlush(invoice);
        saveItems(invoice.getId(), input.items(), amounts);
        return detail(actor, invoice);
    }

    @Transactional
    public Detail update(Actor actor, UUID id, DraftInput input, long expectedVersion) {
        Invoice invoice = load(actor, id);
        if (invoice.getVersion() != expectedVersion) {
            throw new ConflictException("This invoice was changed by someone else. Reload it and apply your changes again.");
        }
        requireDraft(invoice, "changed. Use a credit note or a debit note instead");
        List<InvoiceMath.Line> amounts = apply(invoice, input);
        invoice = invoices.saveAndFlush(invoice);
        if (invoice.getVersion() == expectedVersion) {
            // Only the lines changed. The invoice must still count as changed, or two people editing
            // the same draft would not find out about each other.
            entityManager.lock(invoice, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        }
        items.deleteAllByInvoiceId(id);
        saveItems(id, input.items(), amounts);
        return detail(actor, invoice);
    }

    /** A draft is not yet a financial record, so it can simply be dropped. Nothing else can. */
    @Transactional
    public void discard(Actor actor, UUID id) {
        Invoice invoice = load(actor, id);
        requireDraft(invoice, "discarded. Cancel it instead");
        invoice.markDeleted(context.now());
    }

    // ------------------------------------------------------------------ issuing and cancelling

    /**
     * Turns the draft into an invoice: gives it the next number of its fiscal year, dates it and
     * freezes it.
     *
     * @param issueDate {@code null} for today
     * @param dueDate   {@code null} for the issue date plus the invoice's payment terms
     */
    @Transactional
    public Detail issue(Actor actor, UUID id, LocalDate issueDate, LocalDate dueDate) {
        Invoice invoice = lock(actor, id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("Invoice " + invoice.getInvoiceNumber() + " has already been issued");
        }
        if (invoice.getTotal().signum() <= 0) {
            throw new BusinessRuleException("An invoice for nothing cannot be issued. Give it an amount first.");
        }
        LocalDate issued = issueDate != null ? issueDate : context.today();
        context.requireNotInFuture("issueDate", issued);
        LocalDate due = dueDate != null ? dueDate : issued.plusDays(invoice.getPaymentTermsDays());
        if (due.isBefore(issued)) {
            throw new FieldValidationException("dueDate", "must not be before the issue date");
        }
        invoice.issue(numbers.next(actor.organizationId(), DocumentType.INVOICE, issued), issued, due,
                context.now(), context.nameOf(actor));
        return detail(actor, invoices.saveAndFlush(invoice));
    }

    /** Withdraws an issued invoice that nothing has been settled against. It keeps its number. */
    @Transactional
    public Detail cancel(Actor actor, UUID id, String reason) {
        Invoice invoice = lock(actor, id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("A draft is discarded, not cancelled");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessRuleException("Invoice " + invoice.getInvoiceNumber() + " is already cancelled");
        }
        if (invoice.getAmountPaid().signum() != 0 || invoice.getCreditTotal().signum() != 0
                || invoice.getDebitTotal().signum() != 0) {
            throw new BusinessRuleException("Invoice " + invoice.getInvoiceNumber() + " has payments or notes against "
                    + "it and cannot be cancelled. Release its payments, and credit whatever should not be owed.");
        }
        invoice.cancel(context.now(), reason);
        return detail(actor, invoices.saveAndFlush(invoice));
    }

    // ------------------------------------------------------------------ credit and debit notes

    /** Reduces what is owed on the invoice. Never by more than is outstanding. */
    @Transactional
    public Noted addCreditNote(Actor actor, UUID id, NoteInput input, String idempotencyKey) {
        UUID organizationId = actor.organizationId();
        String fingerprint = Idempotency.fingerprint(id, input.amount(), input.noteDate(), input.reason());
        if (idempotency.claim(organizationId, SCOPE_CREDIT_NOTE, idempotencyKey, fingerprint).isPresent()) {
            return new Noted(get(actor, id), true);
        }
        Invoice invoice = lock(actor, id);
        String number = invoice.getInvoiceNumber();
        switch (invoice.getStatus()) {
            case DRAFT -> throw new BusinessRuleException("A draft has nothing to credit. Change the draft instead.");
            case CANCELLED -> throw new BusinessRuleException("Invoice " + number + " is cancelled");
            case PAID -> throw new BusinessRuleException("Invoice " + number + " has nothing outstanding. To credit it, "
                    + "release that much of its payments first; the money can then be refunded or used elsewhere.");
            default -> { }
        }
        BigDecimal amount = Money.of(input.amount());
        if (amount.compareTo(invoice.getOutstanding()) > 0) {
            throw new BusinessRuleException("Invoice " + number + " has only " + Money.rupees(invoice.getOutstanding())
                    + " outstanding. To credit more, release that much of its payments first.");
        }
        LocalDate date = noteDate(invoice, input.noteDate());
        CreditNote note = creditNotes.save(new CreditNote(
                organizationId, numbers.next(organizationId, DocumentType.CREDIT_NOTE, date), id,
                invoice.getCustomerId(), date, amount, input.reason(), context.nameOf(actor)));
        invoice.applyCredit(amount);
        invoice = invoices.saveAndFlush(invoice);
        idempotency.complete(organizationId, SCOPE_CREDIT_NOTE, idempotencyKey, "CreditNote", note.getId());
        return new Noted(detail(actor, invoice), false);
    }

    /** Adds to what is owed on the invoice. A paid invoice becomes owing again. */
    @Transactional
    public Noted addDebitNote(Actor actor, UUID id, NoteInput input, String idempotencyKey) {
        UUID organizationId = actor.organizationId();
        String fingerprint = Idempotency.fingerprint(id, input.amount(), input.noteDate(), input.reason());
        if (idempotency.claim(organizationId, SCOPE_DEBIT_NOTE, idempotencyKey, fingerprint).isPresent()) {
            return new Noted(get(actor, id), true);
        }
        Invoice invoice = lock(actor, id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("A draft has nothing to debit. Change the draft instead.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessRuleException("Invoice " + invoice.getInvoiceNumber() + " is cancelled");
        }
        BigDecimal amount = Money.of(input.amount());
        if (invoice.getPayable().add(amount).compareTo(Money.MAX) > 0) {
            throw new FieldValidationException("amount", "is too large");
        }
        LocalDate date = noteDate(invoice, input.noteDate());
        DebitNote note = debitNotes.save(new DebitNote(
                organizationId, numbers.next(organizationId, DocumentType.DEBIT_NOTE, date), id,
                invoice.getCustomerId(), date, amount, input.reason(), context.nameOf(actor)));
        invoice.applyDebit(amount);
        invoice = invoices.saveAndFlush(invoice);
        idempotency.complete(organizationId, SCOPE_DEBIT_NOTE, idempotencyKey, "DebitNote", note.getId());
        return new Noted(detail(actor, invoice), false);
    }

    // ------------------------------------------------------------------ helpers

    private Invoice load(Actor actor, UUID id) {
        return invoices.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    private Invoice lock(Actor actor, UUID id) {
        return invoices.lockByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    private static void requireDraft(Invoice invoice, String cannotBe) {
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException(
                    "Invoice " + invoice.getInvoiceNumber() + " has been issued and can no longer be " + cannotBe + ".");
        }
    }

    /** Puts what was typed onto the draft and returns the amounts of its lines, in order. */
    private List<InvoiceMath.Line> apply(Invoice invoice, DraftInput input) {
        CustomerSummary customer = context.customer(invoice.getOrganizationId(), input.customerId());
        List<InvoiceMath.Line> amounts = amounts(input.items());
        invoice.setCustomer(customer.id(), customer.name());
        invoice.setBillingAddress(input.billingAddress() != null ? input.billingAddress() : customer.billingAddress());
        invoice.setCustomerTaxId(input.customerTaxId() != null ? input.customerTaxId() : customer.taxId());
        invoice.setReference(input.reference());
        invoice.setPaymentTermsDays(input.paymentTermsDays() != null
                ? input.paymentTermsDays() : context.properties().defaultPaymentTermsDays());
        invoice.setNotes(input.notes());
        invoice.setTerms(input.terms());
        invoice.setAmounts(InvoiceMath.totals(amounts));
        return amounts;
    }

    private static List<InvoiceMath.Line> amounts(List<ItemInput> lines) {
        if (lines.isEmpty()) {
            throw new FieldValidationException("items", "Add at least one line");
        }
        if (lines.size() > MAX_ITEMS) {
            throw new FieldValidationException("items", "At most " + MAX_ITEMS + " lines");
        }
        List<InvoiceMath.Line> amounts = lines.stream()
                .map(line -> InvoiceMath.line(line.quantity(), line.unitPrice(), percent(line.discountPercent()),
                        percent(line.taxPercent())))
                .toList();
        if (InvoiceMath.totals(amounts).subtotal().compareTo(Money.MAX) > 0) {
            throw new FieldValidationException("items", "The invoice adds up to more than can be recorded");
        }
        return amounts;
    }

    private static BigDecimal percent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void saveItems(UUID invoiceId, List<ItemInput> lines, List<InvoiceMath.Line> amounts) {
        List<InvoiceItem> rows = new ArrayList<>();
        for (int position = 0; position < lines.size(); position++) {
            ItemInput line = lines.get(position);
            rows.add(new InvoiceItem(invoiceId, position, line.serviceId(), line.description(), line.hsnSac(),
                    line.quantity(), line.unit(), Money.of(line.unitPrice()), percent(line.discountPercent()),
                    percent(line.taxPercent()), amounts.get(position)));
        }
        items.saveAllAndFlush(rows);
    }

    /** A note is dated today unless told otherwise, never before its invoice and never in the future. */
    private LocalDate noteDate(Invoice invoice, LocalDate given) {
        LocalDate date = given != null ? given : context.today();
        context.requireNotInFuture("noteDate", date);
        if (date.isBefore(invoice.getIssueDate())) {
            throw new FieldValidationException("noteDate", "must not be before the invoice was issued");
        }
        return date;
    }

    /** The filter for a status as people see it, where OVERDUE is a matter of dates. */
    private static Specification<Invoice> shownAs(InvoiceStatus status, LocalDate today) {
        if (status == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return switch (status) {
            case OVERDUE -> (root, query, cb) -> cb.and(root.get("status").in(OPEN), cb.lessThan(root.<LocalDate>get("dueDate"), today));
            case ISSUED, PARTIALLY_PAID -> (root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), status), cb.greaterThanOrEqualTo(root.<LocalDate>get("dueDate"), today));
            case DRAFT, PAID, CANCELLED -> Specs.equalTo("status", status);
        };
    }

    static Summary summary(Invoice invoice, LocalDate today) {
        InvoiceStatus shown = invoice.getStatus().on(invoice.getDueDate(), today);
        boolean owed = invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.CANCELLED;
        return new Summary(
                invoice.getId(), invoice.getInvoiceNumber(), shown, invoice.getCustomerId(), invoice.getCustomerName(),
                invoice.getIssueDate(), invoice.getDueDate(), invoice.getCurrency(), invoice.getTotal(),
                invoice.getAmountPaid(), owed ? invoice.getOutstanding() : Money.ZERO,
                shown == InvoiceStatus.OVERDUE ? Ageing.daysOverdue(invoice.getDueDate(), today) : null);
    }

    private Detail detail(Actor actor, Invoice invoice) {
        UUID id = invoice.getId();
        List<Item> lines = items.findAllByInvoiceIdOrderByPositionAsc(id).stream()
                .map(item -> new Item(item.getId(), item.getServiceId(), item.getDescription(), item.getHsnSac(),
                        plain(item.getQuantity()), item.getUnit(), item.getUnitPrice(), plain(item.getDiscountPercent()),
                        plain(item.getTaxPercent()), item.getLineSubtotal(), item.getDiscountAmount(),
                        item.getTaxAmount(), item.getLineTotal()))
                .toList();
        List<Settlement> payments = jdbc.query("""
                SELECT a.id, p.id, p.payment_number, p.received_on, p.method_name, a.amount, r.id IS NOT NULL,
                       a.allocated_by_name, a.created_at
                FROM payment_allocations a
                JOIN payments p ON p.id = a.payment_id
                LEFT JOIN payment_allocations r ON r.reverses_id = a.id
                WHERE a.invoice_id = ? AND a.amount > 0
                ORDER BY a.created_at, a.id
                """, (row, index) -> new Settlement(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getString(3), row.getObject(4, LocalDate.class), row.getString(5), row.getBigDecimal(6),
                row.getBoolean(7), row.getString(8), instant(row.getTimestamp(9))), id);
        InvoiceStatus stored = invoice.getStatus();
        boolean untouched = invoice.getAmountPaid().signum() == 0 && invoice.getCreditTotal().signum() == 0
                && invoice.getDebitTotal().signum() == 0;
        Actions actions = new Actions(
                actor.manage() && stored == InvoiceStatus.DRAFT,
                actor.manage() && stored == InvoiceStatus.DRAFT,
                actor.manage() && stored == InvoiceStatus.DRAFT,
                actor.manage() && OPEN.contains(stored),
                actor.approve() && OPEN.contains(stored),
                actor.approve() && (OPEN.contains(stored) || stored == InvoiceStatus.PAID),
                actor.approve() && stored == InvoiceStatus.ISSUED && untouched);
        return new Detail(
                summary(invoice, context.today()), invoice.getBillingAddress(), invoice.getCustomerTaxId(),
                invoice.getReference(), invoice.getPaymentTermsDays(), invoice.getNotes(), invoice.getTerms(),
                invoice.getSubtotal(), invoice.getDiscountTotal(), invoice.getTaxTotal(), invoice.getCreditTotal(),
                invoice.getDebitTotal(), lines, payments,
                creditNotes.findAllByInvoiceIdOrderByCreatedAtAscIdAsc(id).stream().map(InvoiceService::note).toList(),
                debitNotes.findAllByInvoiceIdOrderByCreatedAtAscIdAsc(id).stream().map(InvoiceService::note).toList(),
                organizations.letterhead(invoice.getOrganizationId())
                        .map(head -> new Seller(head.name(), head.taxId(), head.email(), head.phone(), head.address()))
                        .orElse(null),
                invoice.getIssuedAt(), invoice.getIssuedByName(), invoice.getCancelledAt(),
                invoice.getCancellationReason(), actions, invoice.getVersion());
    }

    private static Note note(AdjustmentNote note) {
        return new Note(note.getId(), note.getNoteNumber(), note.getNoteDate(), note.getAmount(), note.getReason(),
                note.getIssuedByName(), note.getCreatedAt());
    }

    /** 2 rather than 2.000, 18 rather than 18.00: quantities and percentages as people write them. */
    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** ISSUED as "issued", for messages. */
    static String words(Enum<?> status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
