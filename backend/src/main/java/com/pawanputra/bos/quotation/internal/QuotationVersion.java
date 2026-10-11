package com.pawanputra.bos.quotation.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotation_versions")
public class QuotationVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_percentage", precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "final_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "terms", columnDefinition = "text")
    private String terms;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "pdf_storage_key", length = 500)
    private String pdfStorageKey;

    @Column(name = "pdf_generated_at")
    private Instant pdfGeneratedAt;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("itemOrder ASC")
    private List<QuotationItem> items = new ArrayList<>();

    protected QuotationVersion() {
    }

    public QuotationVersion(
            Quotation quotation,
            int versionNumber,
            BigDecimal discountPercentage,
            BigDecimal taxPercentage,
            String terms,
            LocalDate validUntil,
            String notes,
            String changeSummary,
            String createdBy) {
        this.id = UUID.randomUUID();
        this.quotation = quotation;
        this.versionNumber = versionNumber;
        this.subtotal = BigDecimal.ZERO;
        this.discountPercentage = discountPercentage != null ? discountPercentage : BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.taxPercentage = taxPercentage != null ? taxPercentage : BigDecimal.valueOf(18.00);
        this.taxAmount = BigDecimal.ZERO;
        this.finalAmount = BigDecimal.ZERO;
        this.terms = terms;
        this.validUntil = validUntil;
        this.notes = notes;
        this.changeSummary = changeSummary;
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
    }

    public void addItem(QuotationItem item) {
        this.items.add(item);
    }

    public void recalculateTotals() {
        BigDecimal sumSubtotal = BigDecimal.ZERO;
        for (QuotationItem item : items) {
            sumSubtotal = sumSubtotal.add(item.getTotalAmount());
        }
        this.subtotal = sumSubtotal;

        // Apply discount percentage
        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            this.discountAmount = sumSubtotal.multiply(discountPercentage)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        } else {
            this.discountAmount = BigDecimal.ZERO;
        }

        BigDecimal afterDiscount = sumSubtotal.subtract(this.discountAmount);
        if (afterDiscount.compareTo(BigDecimal.ZERO) < 0) {
            afterDiscount = BigDecimal.ZERO;
        }

        // Apply tax percentage
        if (taxPercentage != null && taxPercentage.compareTo(BigDecimal.ZERO) > 0) {
            this.taxAmount = afterDiscount.multiply(taxPercentage)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        } else {
            this.taxAmount = BigDecimal.ZERO;
        }

        this.finalAmount = afterDiscount.add(this.taxAmount);
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public Quotation getQuotation() { return quotation; }
    public int getVersionNumber() { return versionNumber; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(BigDecimal discountPercentage) { this.discountPercentage = discountPercentage; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal taxPercentage) { this.taxPercentage = taxPercentage; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }
    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPdfStorageKey() { return pdfStorageKey; }
    public void setPdfStorageKey(String pdfStorageKey) { this.pdfStorageKey = pdfStorageKey; }
    public Instant getPdfGeneratedAt() { return pdfGeneratedAt; }
    public void setPdfGeneratedAt(Instant pdfGeneratedAt) { this.pdfGeneratedAt = pdfGeneratedAt; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public List<QuotationItem> getItems() { return items; }
}
