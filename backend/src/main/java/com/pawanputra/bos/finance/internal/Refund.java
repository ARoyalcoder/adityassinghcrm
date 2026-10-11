package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Money returned to a customer, out of the unallocated part of the payment it came in with.
 * Final the moment it is recorded. There are no setters.
 */
@Entity
@Audited(module = "finance", entity = "Refund", label = "refundNumber")
@Table(name = "refunds")
public class Refund extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "refund_number", nullable = false, updatable = false, length = 20)
    private String refundNumber;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "refund_date", nullable = false, updatable = false)
    private LocalDate refundDate;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "method_id", nullable = false, updatable = false)
    private UUID methodId;

    @Column(name = "method_name", nullable = false, updatable = false, length = 100)
    private String methodName;

    @Column(name = "reference", updatable = false, length = 100)
    private String reference;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "refunded_by_name", nullable = false, updatable = false, length = 200)
    private String refundedByName;

    protected Refund() {
        // for JPA
    }

    public Refund(
            UUID organizationId, String refundNumber, UUID paymentId, UUID customerId, LocalDate refundDate,
            BigDecimal amount, UUID methodId, String methodName, String reference, String reason, String refundedByName) {
        this.organizationId = organizationId;
        this.refundNumber = refundNumber;
        this.paymentId = paymentId;
        this.customerId = customerId;
        this.refundDate = refundDate;
        this.amount = amount;
        this.methodId = methodId;
        this.methodName = methodName;
        this.reference = reference;
        this.reason = reason;
        this.refundedByName = refundedByName;
    }

    public String getRefundNumber() {
        return refundNumber;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public LocalDate getRefundDate() {
        return refundDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReference() {
        return reference;
    }

    public String getReason() {
        return reason;
    }

    public String getRefundedByName() {
        return refundedByName;
    }
}
