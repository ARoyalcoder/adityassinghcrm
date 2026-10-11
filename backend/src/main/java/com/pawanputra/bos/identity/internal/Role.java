package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A named set of permissions that is assigned to users. Deactivated with {@code active}, not deleted. */
@Entity
@Audited(module = "identity", entity = "Role", label = "name")
@Table(name = "roles")
public class Role extends AuditableEntity {

    /** Stable identifier, {@code UPPER_SNAKE_CASE}, e.g. {@code BRANCH_MANAGER}. */
    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Ships with the product; administrators cannot edit or remove it. */
    @Column(name = "system_role", nullable = false, updatable = false)
    private boolean systemRole;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToMany
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
        // for JPA
    }

    public Role(String code, String name, boolean systemRole) {
        this.code = code;
        this.name = name;
        this.systemRole = systemRole;
    }

    public void grant(Permission permission) {
        permissions.add(permission);
    }

    public void revoke(Permission permission) {
        permissions.remove(permission);
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
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

    public boolean isSystemRole() {
        return systemRole;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
