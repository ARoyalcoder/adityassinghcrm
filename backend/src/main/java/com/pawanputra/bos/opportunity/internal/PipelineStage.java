package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.platform.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pipeline_stages")
public class PipelineStage extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "default_probability", nullable = false)
    private int defaultProbability = 50;

    @Column(name = "is_won", nullable = false)
    private boolean won = false;

    @Column(name = "is_lost", nullable = false)
    private boolean lost = false;

    @Column(name = "color", length = 30)
    private String color = "blue";

    protected PipelineStage() {
        // JPA
    }

    public PipelineStage(
            Pipeline pipeline,
            String name,
            String code,
            int displayOrder,
            int defaultProbability,
            boolean won,
            boolean lost,
            String color) {
        this.pipeline = pipeline;
        this.name = name;
        this.code = code;
        this.displayOrder = displayOrder;
        this.defaultProbability = defaultProbability;
        this.won = won;
        this.lost = lost;
        this.color = color != null ? color : "blue";
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public int getDefaultProbability() {
        return defaultProbability;
    }

    public void setDefaultProbability(int defaultProbability) {
        this.defaultProbability = defaultProbability;
    }

    public boolean isWon() {
        return won;
    }

    public void setWon(boolean won) {
        this.won = won;
    }

    public boolean isLost() {
        return lost;
    }

    public void setLost(boolean lost) {
        this.lost = lost;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
