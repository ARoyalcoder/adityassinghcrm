package com.pawanputra.bos.customer.internal;

import com.pawanputra.bos.audit.api.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Something a member of staff wrote down about a customer. Written once; it can be removed but not edited. */
@Entity
@Audited(module = "customers", entity = "Note")
@Table(name = "customer_notes")
public class CustomerNote extends CustomerPart {

    @Column(name = "body", nullable = false, updatable = false, length = 4000)
    private String body;

    /** The author's name at the time of writing. */
    @Column(name = "author_name", nullable = false, updatable = false, length = 200)
    private String authorName;

    protected CustomerNote() {
        // for JPA
    }

    public CustomerNote(Customer customer, String body, String authorName) {
        super(customer);
        this.body = body;
        this.authorName = authorName;
    }

    public String getBody() {
        return body;
    }

    public String getAuthorName() {
        return authorName;
    }
}
