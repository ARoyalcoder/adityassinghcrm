package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.audit.api.AuditExclude;
import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.identity.api.UserStatus;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.hibernate.annotations.SQLRestriction;

/**
 * A person who can sign in. Belongs to exactly one organization and optionally to one of its branches
 * (the database rejects a branch from another organization).
 */
@Entity
@Audited(module = "identity", entity = "User", label = "email")
@Table(name = "users")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class User extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** Login identifier. Always stored lower-case. */
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "phone", length = 30)
    private String phone;

    /** Only ever a password hash. {@code null} while the user is {@link UserStatus#INVITED}. */
    @AuditExclude
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.INVITED;

    @AuditExclude
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @AuditExclude
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @AuditExclude
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @AuditExclude
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> roleAssignments = new HashSet<>();

    protected User() {
        // for JPA
    }

    public User(Organization organization, String email, String fullName) {
        this.organization = organization;
        this.email = normalizeEmail(email);
        this.fullName = fullName;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** No-op if the user already has the role. */
    public void assignRole(Role role) {
        boolean alreadyAssigned = roleAssignments.stream().anyMatch(a -> a.getRole().equals(role));
        if (!alreadyAssigned) {
            roleAssignments.add(new UserRole(this, role));
        }
    }

    public void removeRole(Role role) {
        roleAssignments.removeIf(a -> a.getRole().equals(role));
    }

    /** Sign-in is refused while locked, even with the right password. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a wrong password; the {@code maxAttempts}-th one in a row locks the account for {@code lockFor}. */
    public void recordFailedLogin(Instant now, int maxAttempts, Duration lockFor) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockFor);
            failedLoginAttempts = 0;
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /**
     * Sets a new password hash and clears any lock. A user who was only {@link UserStatus#INVITED}
     * becomes {@link UserStatus#ACTIVE}: setting the first password is how an invitation is accepted.
     */
    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = newPasswordHash;
        passwordChangedAt = now;
        failedLoginAttempts = 0;
        lockedUntil = null;
        if (status == UserStatus.INVITED) {
            status = UserStatus.ACTIVE;
        }
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE && !isDeleted();
    }

    /** Codes of the user's active roles. */
    public Set<String> activeRoleCodes() {
        return getRoles().stream().filter(Role::isActive).map(Role::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Codes of every active permission granted through an active role. */
    public Set<String> activePermissionCodes() {
        return getRoles().stream().filter(Role::isActive)
                .flatMap(role -> role.getPermissions().stream())
                .filter(Permission::isActive).map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public Set<Role> getRoles() {
        return roleAssignments.stream().map(UserRole::getRole).collect(Collectors.toUnmodifiableSet());
    }

    public Organization getOrganization() {
        return organization;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }
}
