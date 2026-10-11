package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.catalog.api.BillingType;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;

/**
 * One service the company sells (a row of the {@code services} table). Named "offering" in code
 * because {@code Service} collides with Spring's {@code @Service}.
 *
 * <p>Everything that makes one service behave differently from another is a field here, edited by
 * administrators. Code must read these fields; it must never branch on a vertical or service code.
 */
@Entity
@Audited(module = "catalog", entity = "Service", label = "name")
@Table(name = "services")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class ServiceOffering extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    /** {@code UPPER_SNAKE_CASE}, unique across the whole catalog, never changed after creation. */
    @Column(name = "code", nullable = false, updatable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 20)
    private BillingType billingType = BillingType.QUOTE_BASED;

    /** What one unit is: "per camera", "per kW", "per month". */
    @Column(name = "unit_label", length = 50)
    private String unitLabel;

    /** Starting price per unit in INR; {@code null} when the service is always quoted. */
    @Column(name = "base_price", precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "requires_site_visit", nullable = false)
    private boolean requiresSiteVisit;

    @Column(name = "estimated_duration_days")
    private Integer estimatedDurationDays;

    protected ServiceOffering() {
        // for JPA
    }

    public ServiceOffering(ServiceCategory category, String code, String name) {
        this.category = category;
        this.code = code;
        this.name = name;
    }

    public ServiceCategory getCategory() {
        return category;
    }

    /** Move the service to another category (of any vertical). */
    public void setCategory(ServiceCategory category) {
        this.category = category;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BillingType getBillingType() {
        return billingType;
    }

    public void setBillingType(BillingType billingType) {
        this.billingType = billingType;
    }

    public String getUnitLabel() {
        return unitLabel;
    }

    public void setUnitLabel(String unitLabel) {
        this.unitLabel = unitLabel;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public boolean isRequiresSiteVisit() {
        return requiresSiteVisit;
    }

    public void setRequiresSiteVisit(boolean requiresSiteVisit) {
        this.requiresSiteVisit = requiresSiteVisit;
    }

    public Integer getEstimatedDurationDays() {
        return estimatedDurationDays;
    }

    public void setEstimatedDurationDays(Integer estimatedDurationDays) {
        this.estimatedDurationDays = estimatedDurationDays;
    }
}
