package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * How much of a payment settles which invoice. Rows are only ever added: releasing an allocation
 * adds a second row with the amount negated, pointing at the first. There are no setters.
 */
@Entity
@Audited(module = "finance", entity = "PaymentAllocation")
@Table(name = "payment_allocations")
public class PaymentAllocation extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "reverses_id", updatable = false)
    private UUID reversesId;

    @Column(name = "reason", updatable = false, length = 500)
    private String reason;

    @Column(name = "allocated_by_name", nullable = false, updatable = false, length = 200)
    private String allocatedByName;

    protected PaymentAllocation() {
        // for JPA
    }

    public PaymentAllocation(UUID organizationId, UUID paymentId, UUID invoiceId, BigDecimal amount, String byName) {
        this.organizationId = organizationId;
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.allocatedByName = byName;
    }

    /** The row that undoes this one. */
    public PaymentAllocation release(String reason, String byName) {
        PaymentAllocation release = new PaymentAllocation(organizationId, paymentId, invoiceId, amount.negate(), byName);
        release.reversesId = getId();
        release.reason = reason;
        return release;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getReversesId() {
        return reversesId;
    }

    public String getReason() {
        return reason;
    }

    public String getAllocatedByName() {
        return allocatedByName;
    }
}
