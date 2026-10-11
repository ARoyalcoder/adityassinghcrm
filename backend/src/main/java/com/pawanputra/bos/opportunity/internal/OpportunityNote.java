package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "opportunity_notes")
public class OpportunityNote extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private Opportunity opportunity;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 255)
    private String authorName;

    protected OpportunityNote() {
        // JPA
    }

    public OpportunityNote(Opportunity opportunity, String body, UUID authorId, String authorName) {
        this.opportunity = opportunity;
        this.body = body;
        this.authorId = authorId;
        this.authorName = authorName;
    }

    public Opportunity getOpportunity() {
        return opportunity;
    }

    public String getBody() {
        return body;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }
}
