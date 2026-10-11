package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.opportunity.api.OpportunityStatus;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Audited(module = "opportunities", entity = "Opportunity", label = "title")
@Table(name = "opportunities")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class Opportunity extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "lead_name", length = 255)
    private String leadName;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "vertical_code", length = 50)
    private String verticalCode;

    @Column(name = "value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "probability", nullable = false)
    private int probability = 50;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private PipelineStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OpportunityStatus status = OpportunityStatus.OPEN;

    @Column(name = "loss_reason", length = 500)
    private String lossReason;

    @Column(name = "won_at")
    private Instant wonAt;

    @Column(name = "lost_at")
    private Instant lostAt;

    @Column(name = "stage_entered_at", nullable = false)
    private Instant stageEnteredAt = Instant.now();

    @OneToMany(mappedBy = "opportunity", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<OpportunityNote> notes = new ArrayList<>();

    protected Opportunity() {
        // JPA
    }

    public Opportunity(
            UUID organizationId,
            String title,
            UUID customerId,
            String customerName,
            UUID leadId,
            String leadName,
            UUID serviceId,
            String serviceName,
            String verticalCode,
            BigDecimal value,
            int probability,
            LocalDate expectedCloseDate,
            UUID ownerId,
            String ownerName,
            Pipeline pipeline,
            PipelineStage stage) {
        this.organizationId = organizationId;
        this.title = title;
        this.customerId = customerId;
        this.customerName = customerName;
        this.leadId = leadId;
        this.leadName = leadName;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.verticalCode = verticalCode;
        this.value = value != null ? value : BigDecimal.ZERO;
        this.probability = probability;
        this.expectedCloseDate = expectedCloseDate;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.pipeline = pipeline;
        this.stage = stage;
        this.stageEnteredAt = Instant.now();
        this.status = stage.isWon() ? OpportunityStatus.WON : (stage.isLost() ? OpportunityStatus.LOST : OpportunityStatus.OPEN);
        if (stage.isWon()) {
            this.wonAt = Instant.now();
        } else if (stage.isLost()) {
            this.lostAt = Instant.now();
        }
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public UUID getLeadId() {
        return leadId;
    }

    public void setLeadId(UUID leadId) {
        this.leadId = leadId;
    }

    public String getLeadName() {
        return leadName;
    }

    public void setLeadName(String leadName) {
        this.leadName = leadName;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getVerticalCode() {
        return verticalCode;
    }

    public void setVerticalCode(String verticalCode) {
        this.verticalCode = verticalCode;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value != null ? value : BigDecimal.ZERO;
    }

    public int getProbability() {
        return probability;
    }

    public void setProbability(int probability) {
        this.probability = probability;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public void setExpectedCloseDate(LocalDate expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public PipelineStage getStage() {
        return stage;
    }

    public void setStage(PipelineStage stage) {
        this.stage = stage;
    }

    public OpportunityStatus getStatus() {
        return status;
    }

    public void setStatus(OpportunityStatus status) {
        this.status = status;
    }

    public String getLossReason() {
        return lossReason;
    }

    public void setLossReason(String lossReason) {
        this.lossReason = lossReason;
    }

    public Instant getWonAt() {
        return wonAt;
    }

    public void setWonAt(Instant wonAt) {
        this.wonAt = wonAt;
    }

    public Instant getLostAt() {
        return lostAt;
    }

    public void setLostAt(Instant lostAt) {
        this.lostAt = lostAt;
    }

    public Instant getStageEnteredAt() {
        return stageEnteredAt;
    }

    public void setStageEnteredAt(Instant stageEnteredAt) {
        this.stageEnteredAt = stageEnteredAt;
    }

    public List<OpportunityNote> getNotes() {
        return notes;
    }

    public void transitionToStage(PipelineStage newStage, Instant when, String reason) {
        this.stage = newStage;
        this.stageEnteredAt = when;
        this.probability = newStage.getDefaultProbability();
        if (newStage.isWon()) {
            this.status = OpportunityStatus.WON;
            this.wonAt = when;
            this.lostAt = null;
            this.lossReason = null;
        } else if (newStage.isLost()) {
            this.status = OpportunityStatus.LOST;
            this.lostAt = when;
            this.wonAt = null;
            if (reason != null && !reason.isBlank()) {
                this.lossReason = reason;
            }
        } else {
            this.status = OpportunityStatus.OPEN;
            this.wonAt = null;
            this.lostAt = null;
            this.lossReason = null;
        }
    }
}
