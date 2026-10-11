package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.audit.api.AuditExclude;
import com.pawanputra.bos.audit.api.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A person at the customer. One contact per customer may be marked as the primary one. */
@Entity
@Audited(module = "customers", entity = "Contact", label = "name")
@Table(name = "customer_contacts")
public class CustomerContact extends CustomerPart {

    @AuditExclude
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @AuditExclude
    @Column(name = "phone_key", length = 20)
    private String phoneKey;

    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact;

    protected CustomerContact() {
        // for JPA
    }

    public CustomerContact(Customer customer, String name) {
        super(customer);
        this.organizationId = customer.getOrganizationId();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = CustomerKeys.email(email);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneKey = CustomerKeys.phone(phone);
    }

    public boolean isPrimaryContact() {
        return primaryContact;
    }

    public void setPrimaryContact(boolean primaryContact) {
        this.primaryContact = primaryContact;
    }
}
