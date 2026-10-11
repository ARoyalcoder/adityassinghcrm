package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One thing a user may be allowed to do. Permissions are defined by the application: each module
 * adds its own through a Flyway migration, and code checks them by {@link #getCode() code}.
 */
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    /** Dotted lower-case identifier, {@code <module>.<resource>.<action>}, e.g. {@code catalog.service.write}. */
    @Column(name = "code", nullable = false, updatable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Permission() {
        // for JPA
    }

    public Permission(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
