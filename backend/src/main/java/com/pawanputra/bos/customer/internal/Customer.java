package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.audit.api.AuditExclude;
import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.customer.api.CustomerSource;
import com.pawanputra.bos.customer.api.CustomerStatus;
import com.pawanputra.bos.customer.api.CustomerType;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

/**
 * Someone the company does business with: a person or a company. Other modules (leads, quotes,
 * projects, invoices) will refer to a customer by its id.
 */
@Entity
@Audited(module = "customers", entity = "Customer", label = "displayName")
@Table(name = "customers")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class Customer extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "customer_number", nullable = false, updatable = false, length = 20)
    private String customerNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CustomerType type;

    /** Derived from the names below; never set directly. */
    @AuditExclude
    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @AuditExclude
    @Column(name = "company_name_key", length = 200)
    private String companyNameKey;

    @Column(name = "tax_id", length = 30)
    private String taxId;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @AuditExclude
    @Column(name = "phone_key", length = 20)
    private String phoneKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 30)
    private CustomerSource source;

    /** The employee who looks after this customer; an id in the identity module. */
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    // Loaded a page at a time for lists (one extra query per page, not one per customer).
    @ManyToMany
    @BatchSize(size = 100)
    @JoinTable(
            name = "customer_tag_assignments",
            joinColumns = @JoinColumn(name = "customer_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<CustomerTag> tags = new HashSet<>();

    protected Customer() {
        // for JPA
    }

    public Customer(UUID organizationId, String customerNumber) {
        this.organizationId = organizationId;
        this.customerNumber = customerNumber;
    }

    /**
     * Sets who the customer is. For an individual the person's name is required; for a business the
     * company name is. The display name and the duplicate-detection key follow automatically.
     */
    public void identify(CustomerType type, String firstName, String lastName, String companyName) {
        this.type = type;
        this.firstName = firstName;
        this.lastName = lastName;
        this.companyName = companyName;
        this.companyNameKey = CustomerKeys.company(companyName);
        this.displayName = type == CustomerType.BUSINESS
                ? companyName
                : (lastName == null ? firstName : firstName + " " + lastName);
    }

    public void setEmail(String email) {
        this.email = CustomerKeys.email(email);
    }

    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneKey = CustomerKeys.phone(phone);
    }

    public void replaceTags(Set<CustomerTag> newTags) {
        tags.retainAll(newTags);
        tags.addAll(newTags);
    }

    public Set<CustomerTag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public CustomerType getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCompanyNameKey() {
        return companyNameKey;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPhoneKey() {
        return phoneKey;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public void setStatus(CustomerStatus status) {
        this.status = status;
    }

    public CustomerSource getSource() {
        return source;
    }

    public void setSource(CustomerSource source) {
        this.source = source;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(UUID assignedUserId) {
        this.assignedUserId = assignedUserId;
    }
}
