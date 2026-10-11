package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a credit note and a debit note have in common: a numbered correction to one issued invoice,
 * for an amount, with a reason. A note is final the moment it is recorded; one entered by mistake
 * is put right with the opposite note. There are no setters.
 */
@MappedSuperclass
public abstract class AdjustmentNote extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "note_number", nullable = false, updatable = false, length = 20)
    private String noteNumber;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "note_date", nullable = false, updatable = false)
    private LocalDate noteDate;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "issued_by_name", nullable = false, updatable = false, length = 200)
    private String issuedByName;

    protected AdjustmentNote() {
        // for JPA
    }

    protected AdjustmentNote(
            UUID organizationId, String noteNumber, UUID invoiceId, UUID customerId, LocalDate noteDate,
            BigDecimal amount, String reason, String issuedByName) {
        this.organizationId = organizationId;
        this.noteNumber = noteNumber;
        this.invoiceId = invoiceId;
        this.customerId = customerId;
        this.noteDate = noteDate;
        this.amount = amount;
        this.reason = reason;
        this.issuedByName = issuedByName;
    }

    public String getNoteNumber() {
        return noteNumber;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public LocalDate getNoteDate() {
        return noteDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public String getIssuedByName() {
        return issuedByName;
    }
}
