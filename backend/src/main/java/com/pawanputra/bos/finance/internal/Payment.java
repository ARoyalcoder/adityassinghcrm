package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.audit.api.AuditExclude;
import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.finance.api.PaymentStatus;
import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Money received from a customer. What was received never changes; what moves is how much of it
 * has been allocated to invoices or refunded. A payment that should not have been recorded is
 * reversed, not deleted.
 */
@Entity
@Audited(module = "finance", entity = "Payment", label = "paymentNumber")
@Table(name = "payments")
public class Payment extends AuditableEntity {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_GATEWAY = "GATEWAY";

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "payment_number", nullable = false, updatable = false, length = 20)
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.RECEIVED;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, updatable = false, length = 200)
    private String customerName;

    @Column(name = "received_on", nullable = false, updatable = false)
    private LocalDate receivedOn;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "method_id", nullable = false, updatable = false)
    private UUID methodId;

    @Column(name = "method_name", nullable = false, updatable = false, length = 100)
    private String methodName;

    @Column(name = "reference", updatable = false, length = 100)
    private String reference;

    @Column(name = "notes", updatable = false, length = 1000)
    private String notes;

    @Column(name = "allocated_amount", nullable = false)
    private BigDecimal allocatedAmount = Money.ZERO;

    @Column(name = "refunded_amount", nullable = false)
    private BigDecimal refundedAmount = Money.ZERO;

    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private String source = SOURCE_MANUAL;

    @Column(name = "gateway", updatable = false, length = 50)
    private String gateway;

    @Column(name = "gateway_payment_id", updatable = false, length = 200)
    private String gatewayPaymentId;

    @Column(name = "recorded_by_name", nullable = false, updatable = false, length = 200)
    private String recordedByName;

    @AuditExclude
    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by_name", length = 200)
    private String reversedByName;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    protected Payment() {
        // for JPA
    }

    public Payment(
            UUID organizationId, String paymentNumber, UUID customerId, String customerName, LocalDate receivedOn,
            BigDecimal amount, String currency, UUID methodId, String methodName, String reference, String notes,
            String recordedByName) {
        this.organizationId = organizationId;
        this.paymentNumber = paymentNumber;
        this.customerId = customerId;
        this.customerName = customerName;
        this.receivedOn = receivedOn;
        this.amount = amount;
        this.currency = currency;
        this.methodId = methodId;
        this.methodName = methodName;
        this.reference = reference;
        this.notes = notes;
        this.recordedByName = recordedByName;
    }

    /** Marks the payment as reported by a gateway, under the gateway's own id for it. */
    public void fromGateway(String gateway, String gatewayPaymentId) {
        this.source = SOURCE_GATEWAY;
        this.gateway = gateway;
        this.gatewayPaymentId = gatewayPaymentId;
    }

    /** @param amount what was allocated to an invoice, or the negative of what was released */
    public void allocate(BigDecimal amount) {
        this.allocatedAmount = allocatedAmount.add(amount);
    }

    public void refund(BigDecimal amount) {
        this.refundedAmount = refundedAmount.add(amount);
    }

    /** The caller has released every allocation first. */
    public void reverse(Instant now, String byName, String reason) {
        this.status = PaymentStatus.REVERSED;
        this.reversedAt = now;
        this.reversedByName = byName;
        this.reversalReason = reason;
    }

    /** What is left to allocate or refund. Nothing, once the payment is reversed. */
    public BigDecimal getUnallocated() {
        return status == PaymentStatus.REVERSED
                ? Money.ZERO : amount.subtract(allocatedAmount).subtract(refundedAmount);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getMethodId() {
        return methodId;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReference() {
        return reference;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public String getSource() {
        return source;
    }

    public String getGateway() {
        return gateway;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public String getRecordedByName() {
        return recordedByName;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public String getReversedByName() {
        return reversedByName;
    }

    public String getReversalReason() {
        return reversalReason;
    }
}
