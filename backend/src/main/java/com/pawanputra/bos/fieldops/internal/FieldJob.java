package com.pawanputra.bos.fieldops.internal;

import com.pawanputra.bos.audit.api.AuditExclude;
import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.fieldops.api.JobStatus;
import com.pawanputra.bos.fieldops.api.JobType;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * A job or site visit at a customer's site. The workflow rules are in {@link JobStatus} and
 * {@link FieldJobService}; this class only holds the state and stamps the time of each step.
 */
@Entity
@Audited(module = "field-jobs", entity = "FieldJob", label = "title")
@Table(name = "field_jobs")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class FieldJob extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "job_number", nullable = false, updatable = false, length = 20)
    private String jobNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 20)
    private JobType jobType = JobType.JOB;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "site_address", nullable = false, length = 500)
    private String siteAddress;

    @Column(name = "site_contact_name", length = 200)
    private String siteContactName;

    @Column(name = "site_contact_phone", length = 30)
    private String siteContactPhone;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "technician_name", length = 200)
    private String technicianName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JobStatus status = JobStatus.UNASSIGNED;

    @AuditExclude
    @Column(name = "assigned_at")
    private Instant assignedAt;

    @AuditExclude
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @AuditExclude
    @Column(name = "en_route_at")
    private Instant enRouteAt;

    @AuditExclude
    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @AuditExclude
    @Column(name = "started_at")
    private Instant startedAt;

    @AuditExclude
    @Column(name = "completed_at")
    private Instant completedAt;

    @AuditExclude
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @AuditExclude
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completion_notes", columnDefinition = "text")
    private String completionNotes;

    @Column(name = "signed_by_name", length = 200)
    private String signedByName;

    @Column(name = "signoff_remarks", length = 1000)
    private String signoffRemarks;

    /** A PNG data URL; too large and too meaningless to put in the audit log. */
    @AuditExclude
    @Column(name = "signature", columnDefinition = "text")
    private String signature;

    protected FieldJob() {
        // for JPA
    }

    public FieldJob(UUID organizationId, String jobNumber) {
        this.organizationId = organizationId;
        this.jobNumber = jobNumber;
    }

    /** Gives the job to a technician (or to another one). It is theirs to accept. */
    public void assignTo(UUID technicianId, String technicianName, Instant now) {
        this.technicianId = technicianId;
        this.technicianName = technicianName;
        this.status = JobStatus.ASSIGNED;
        this.assignedAt = now;
        this.acceptedAt = null;
    }

    /** Moves to the given status and records when. The caller has checked that the step is allowed. */
    public void moveTo(JobStatus next, Instant now) {
        this.status = next;
        switch (next) {
            case ACCEPTED -> this.acceptedAt = now;
            case EN_ROUTE -> this.enRouteAt = now;
            case ARRIVED -> this.arrivedAt = now;
            case IN_PROGRESS -> this.startedAt = now;
            case COMPLETED -> this.completedAt = now;
            case CUSTOMER_CONFIRMED -> this.confirmedAt = now;
            case CANCELLED -> this.cancelledAt = now;
            case UNASSIGNED, ASSIGNED -> throw new IllegalArgumentException("Use assignTo for " + next);
        }
    }

    public void signOff(String signedByName, String remarks, String signature) {
        this.signedByName = signedByName;
        this.signoffRemarks = remarks;
        this.signature = signature;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getJobNumber() {
        return jobNumber;
    }

    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getSiteAddress() {
        return siteAddress;
    }

    public void setSiteAddress(String siteAddress) {
        this.siteAddress = siteAddress;
    }

    public String getSiteContactName() {
        return siteContactName;
    }

    public void setSiteContactName(String siteContactName) {
        this.siteContactName = siteContactName;
    }

    public String getSiteContactPhone() {
        return siteContactPhone;
    }

    public void setSiteContactPhone(String siteContactPhone) {
        this.siteContactPhone = siteContactPhone;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public void setScheduledStart(Instant scheduledStart) {
        this.scheduledStart = scheduledStart;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public void setScheduledEnd(Instant scheduledEnd) {
        this.scheduledEnd = scheduledEnd;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public String getTechnicianName() {
        return technicianName;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getEnRouteAt() {
        return enRouteAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCompletionNotes() {
        return completionNotes;
    }

    public void setCompletionNotes(String completionNotes) {
        this.completionNotes = completionNotes;
    }

    public String getSignedByName() {
        return signedByName;
    }

    public String getSignoffRemarks() {
        return signoffRemarks;
    }

    public String getSignature() {
        return signature;
    }
}
