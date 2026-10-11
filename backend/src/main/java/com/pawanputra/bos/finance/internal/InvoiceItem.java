package com.pawanputra.bos.finance.internal;

import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One line of an invoice. The four amounts are worked out by {@link InvoiceMath} and stored. */
@Entity
@Table(name = "invoice_items")
public class InvoiceItem extends BaseEntity {

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "hsn_sac", length = 10)
    private String hsnSac;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "discount_percent", nullable = false)
    private BigDecimal discountPercent;

    @Column(name = "tax_percent", nullable = false)
    private BigDecimal taxPercent;

    @Column(name = "line_subtotal", nullable = false)
    private BigDecimal lineSubtotal;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    protected InvoiceItem() {
        // for JPA
    }

    public InvoiceItem(
            UUID invoiceId, int position, UUID serviceId, String description, String hsnSac, BigDecimal quantity,
            String unit, BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal taxPercent, InvoiceMath.Line amounts) {
        this.invoiceId = invoiceId;
        this.position = position;
        this.serviceId = serviceId;
        this.description = description;
        this.hsnSac = hsnSac;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.discountPercent = discountPercent;
        this.taxPercent = taxPercent;
        this.lineSubtotal = amounts.subtotal();
        this.discountAmount = amounts.discount();
        this.taxAmount = amounts.tax();
        this.lineTotal = amounts.total();
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public int getPosition() {
        return position;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getDescription() {
        return description;
    }

    public String getHsnSac() {
        return hsnSac;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getTaxPercent() {
        return taxPercent;
    }

    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
