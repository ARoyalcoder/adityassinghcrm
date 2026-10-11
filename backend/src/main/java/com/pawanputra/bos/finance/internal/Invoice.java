package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.audit.api.AuditExclude;
import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.finance.api.InvoiceStatus;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * What a customer is billed. A draft can be edited freely; once issued, the invoice itself never
 * changes again. What changes is what has been settled against it: {@link #applyPayment},
 * {@link #applyCredit} and {@link #applyDebit} keep the running totals in step with the payment
 * allocations and notes, and the status follows from them.
 *
 * <p>{@code status} here is the stored status. It is never {@link InvoiceStatus#OVERDUE}: use
 * {@link InvoiceStatus#on} with today's date to know whether the invoice is overdue.
 */
@Entity
@Audited(module = "finance", entity = "Invoice", label = "invoiceNumber")
@Table(name = "invoices")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class Invoice extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "invoice_number", length = 20)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "billing_address", length = 1000)
    private String billingAddress;

    @Column(name = "customer_tax_id", length = 30)
    private String customerTaxId;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal = Money.ZERO;

    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal = Money.ZERO;

    @Column(name = "tax_total", nullable = false)
    private BigDecimal taxTotal = Money.ZERO;

    @Column(name = "total", nullable = false)
    private BigDecimal total = Money.ZERO;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid = Money.ZERO;

    @Column(name = "credit_total", nullable = false)
    private BigDecimal creditTotal = Money.ZERO;

    @Column(name = "debit_total", nullable = false)
    private BigDecimal debitTotal = Money.ZERO;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "terms", columnDefinition = "text")
    private String terms;

    @AuditExclude
    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by_name", length = 200)
    private String issuedByName;

    @AuditExclude
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    protected Invoice() {
        // for JPA
    }

    public Invoice(UUID organizationId, String currency) {
        this.organizationId = organizationId;
        this.currency = currency;
    }

    /** Gives the draft its number and dates. From here on the invoice is part of the books. */
    public void issue(String invoiceNumber, LocalDate issueDate, LocalDate dueDate, Instant now, String byName) {
        this.invoiceNumber = invoiceNumber;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.issuedAt = now;
        this.issuedByName = byName;
        this.status = InvoiceStatus.settlement(getOutstanding(), amountPaid);
    }

    /** @param amount what was allocated to this invoice, or the negative of what was released from it */
    public void applyPayment(BigDecimal amount) {
        this.amountPaid = amountPaid.add(amount);
        resettle();
    }

    public void applyCredit(BigDecimal amount) {
        this.creditTotal = creditTotal.add(amount);
        resettle();
    }

    public void applyDebit(BigDecimal amount) {
        this.debitTotal = debitTotal.add(amount);
        resettle();
    }

    public void cancel(Instant now, String reason) {
        this.status = InvoiceStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancellationReason = reason;
    }

    private void resettle() {
        this.status = InvoiceStatus.settlement(getOutstanding(), amountPaid);
    }

    /** What the customer owes on this invoice once credit and debit notes are counted. */
    public BigDecimal getPayable() {
        return total.add(debitTotal).subtract(creditTotal);
    }

    /** What is still owed. Meaningful for an issued invoice that has not been cancelled. */
    public BigDecimal getOutstanding() {
        return getPayable().subtract(amountPaid);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomer(UUID customerId, String customerName) {
        this.customerId = customerId;
        this.customerName = customerName;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getCustomerTaxId() {
        return customerTaxId;
    }

    public void setCustomerTaxId(String customerTaxId) {
        this.customerTaxId = customerTaxId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public int getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public void setPaymentTermsDays(int paymentTermsDays) {
        this.paymentTermsDays = paymentTermsDays;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setAmounts(InvoiceMath.Totals totals) {
        this.subtotal = totals.subtotal();
        this.discountTotal = totals.discountTotal();
        this.taxTotal = totals.taxTotal();
        this.total = totals.total();
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getCreditTotal() {
        return creditTotal;
    }

    public BigDecimal getDebitTotal() {
        return debitTotal;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTerms() {
        return terms;
    }

    public void setTerms(String terms) {
        this.terms = terms;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getIssuedByName() {
        return issuedByName;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
