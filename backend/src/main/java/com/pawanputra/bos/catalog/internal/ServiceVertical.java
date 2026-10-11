package com.pawanputra.bos.catalog.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.catalog.api.ServiceVerticalCode;
import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * One of the six service verticals. Rows are created only by migration and can never be added or
 * removed by the application: there is no constructor and no delete. Administrators may change how
 * a vertical is presented (name, description, order) and switch it off.
 */
@Entity
@Audited(module = "catalog", entity = "Vertical", label = "name")
@Table(name = "service_verticals")
public class ServiceVertical extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, updatable = false, length = 40)
    private ServiceVerticalCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ServiceVertical() {
        // for JPA; verticals are never created by application code
    }

    public ServiceVerticalCode getCode() {
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

    public long getVersion() {
        return version;
    }
}
