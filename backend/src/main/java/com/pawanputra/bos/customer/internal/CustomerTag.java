package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A label an organization invents to group its customers ("VIP", "Builder"). Tags are created the
 * first time someone uses a new name; there is nothing to set up.
 */
@Entity
@Audited(module = "customers", entity = "Tag", label = "name")
@Table(name = "customer_tags")
public class CustomerTag extends AuditableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /** Unique within the organization, ignoring case. Shown as first typed. */
    @Column(name = "name", nullable = false, updatable = false, length = 50)
    private String name;

    protected CustomerTag() {
        // for JPA
    }

    public CustomerTag(UUID organizationId, String name) {
        this.organizationId = organizationId;
        this.name = name;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }
}
