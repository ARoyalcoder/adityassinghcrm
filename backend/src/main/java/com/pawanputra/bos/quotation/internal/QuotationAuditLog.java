package com.pawanputra.bos.quotation.internal;

import com.pawanputra.bos.quotation.api.QuotationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quotation_audit_logs")
public class QuotationAuditLog {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private QuotationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30)
    private QuotationStatus toStatus;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    protected QuotationAuditLog() {
    }

    public QuotationAuditLog(
            Quotation quotation,
            String action,
            QuotationStatus fromStatus,
            QuotationStatus toStatus,
            Integer versionNumber,
            String notes,
            String performedBy) {
        this.id = UUID.randomUUID();
        this.quotation = quotation;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.versionNumber = versionNumber;
        this.notes = notes;
        this.performedBy = performedBy;
        this.performedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Quotation getQuotation() { return quotation; }
    public String getAction() { return action; }
    public QuotationStatus getFromStatus() { return fromStatus; }
    public QuotationStatus getToStatus() { return toStatus; }
    public Integer getVersionNumber() { return versionNumber; }
    public String getNotes() { return notes; }
    public String getPerformedBy() { return performedBy; }
    public Instant getPerformedAt() { return performedAt; }
}
