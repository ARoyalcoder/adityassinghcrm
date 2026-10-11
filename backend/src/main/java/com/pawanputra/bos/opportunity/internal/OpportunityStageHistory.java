package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "opportunity_stage_history")
public class OpportunityStageHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private Opportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_stage_id")
    private PipelineStage fromStage;

    @Column(name = "from_stage_name", length = 100)
    private String fromStageName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_stage_id", nullable = false)
    private PipelineStage toStage;

    @Column(name = "to_stage_name", nullable = false, length = 100)
    private String toStageName;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    @Column(name = "exited_at")
    private Instant exitedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    protected OpportunityStageHistory() {
        // JPA
    }

    public OpportunityStageHistory(
            Opportunity opportunity,
            PipelineStage fromStage,
            String fromStageName,
            PipelineStage toStage,
            String toStageName,
            Instant enteredAt,
            String changedBy,
            String notes) {
        this.opportunity = opportunity;
        this.fromStage = fromStage;
        this.fromStageName = fromStageName;
        this.toStage = toStage;
        this.toStageName = toStageName;
        this.enteredAt = enteredAt;
        this.changedBy = changedBy;
        this.notes = notes;
    }

    public Opportunity getOpportunity() {
        return opportunity;
    }

    public PipelineStage getFromStage() {
        return fromStage;
    }

    public String getFromStageName() {
        return fromStageName;
    }

    public PipelineStage getToStage() {
        return toStage;
    }

    public String getToStageName() {
        return toStageName;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public Instant getExitedAt() {
        return exitedAt;
    }

    public void setExitedAt(Instant exitedAt) {
        this.exitedAt = exitedAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getNotes() {
        return notes;
    }
}
