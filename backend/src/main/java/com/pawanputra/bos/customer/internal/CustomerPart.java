package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.audit.api.ActivityOwner;
import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * Something that belongs to one customer and has no life of its own: a contact, an address, a note.
 * Its changes are shown in the customer's activity timeline.
 */
@MappedSuperclass
public abstract class CustomerPart extends AuditableEntity implements ActivityOwner {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    protected CustomerPart() {
        // for JPA
    }

    protected CustomerPart(Customer customer) {
        this.customer = customer;
    }

    public Customer getCustomer() {
        return customer;
    }

    @Override
    public String activityOwnerType() {
        return "Customer";
    }

    @Override
    public Object activityOwnerId() {
        return customer.getId();
    }
}
