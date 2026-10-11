package com.pawanputra.bos.lead.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.lead.api.LeadHealth;
import com.pawanputra.bos.lead.api.LeadPriority;
import com.pawanputra.bos.lead.api.LeadStatus;
import com.pawanputra.bos.lead.api.QualificationStatus;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * An enquiry that may become business: who asked, about which service, and what it may be worth.
 *
 * <p>The assessment columns (score, priority, health, qualification status) are read-only here.
 * They are written only by {@link LeadQualificationService}, with a direct update that leaves the
 * lead's version alone, so a background rescore never makes a user's edit fail as "changed by
 * someone else".
 */
@Entity
@Audited(module = "leads", entity = "Lead", label = "contactName")
@Table(name = "leads")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class Lead extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "lead_number", nullable = false, updatable = false, length = 20)
    private String leadNumber;

    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    /** A service of the catalog module, by id. */
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "estimated_value", precision = 14, scale = 2)
    private BigDecimal estimatedValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeadStatus status = LeadStatus.OPEN;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "score", insertable = false, updatable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", insertable = false, updatable = false)
    private LeadPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "health", insertable = false, updatable = false)
    private LeadHealth health;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualification_status", insertable = false, updatable = false)
    private QualificationStatus qualificationStatus;

    @Column(name = "scored_at", insertable = false, updatable = false)
    private Instant scoredAt;

    protected Lead() {
        // for JPA
    }

    public Lead(UUID organizationId, String leadNumber) {
        this.organizationId = organizationId;
        this.leadNumber = leadNumber;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getLeadNumber() {
        return leadNumber;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public void setEstimatedValue(BigDecimal estimatedValue) {
        this.estimatedValue = estimatedValue;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    /** Closing a lead records when; reopening it clears that. Closing an already closed lead keeps the first time. */
    public void setStatus(LeadStatus status, Instant now) {
        if (!status.isClosed()) {
            this.closedAt = null;
        } else if (this.closedAt == null) {
            this.closedAt = now;
        }
        this.status = status;
    }

    public int getScore() {
        return score;
    }

    public LeadPriority getPriority() {
        return priority;
    }

    public LeadHealth getHealth() {
        return health;
    }

    public QualificationStatus getQualificationStatus() {
        return qualificationStatus;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }
}
