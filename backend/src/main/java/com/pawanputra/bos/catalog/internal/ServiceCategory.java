package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/** A grouping of services inside one vertical, e.g. "Installation" under CCTV &amp; Security. */
@Entity
@Audited(module = "catalog", entity = "Category", label = "name")
@Table(name = "service_categories")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class ServiceCategory extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vertical_id", nullable = false, updatable = false)
    private ServiceVertical vertical;

    /** {@code UPPER_SNAKE_CASE}, unique within the vertical. */
    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ServiceCategory() {
        // for JPA
    }

    public ServiceCategory(ServiceVertical vertical, String code, String name) {
        this.vertical = vertical;
        this.code = code;
        this.name = name;
    }

    public ServiceVertical getVertical() {
        return vertical;
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
}
