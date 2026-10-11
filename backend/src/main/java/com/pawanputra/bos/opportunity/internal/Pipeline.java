package com.pawanputra.bos.opportunity.internal;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Audited(module = "opportunities", entity = "Pipeline", label = "name")
@Table(name = "pipelines")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class Pipeline extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPipeline = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<PipelineStage> stages = new ArrayList<>();

    protected Pipeline() {
        // JPA
    }

    public Pipeline(
            UUID organizationId,
            String name,
            String code,
            String description,
            boolean defaultPipeline,
            boolean active) {
        this.organizationId = organizationId;
        this.name = name;
        this.code = code;
        this.description = description;
        this.defaultPipeline = defaultPipeline;
        this.active = active;
    }

    public UUID getOrganizationId() {
        return organizationId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDefaultPipeline() {
        return defaultPipeline;
    }

    public void setDefaultPipeline(boolean defaultPipeline) {
        this.defaultPipeline = defaultPipeline;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<PipelineStage> getStages() {
        return stages;
    }

    public void addStage(PipelineStage stage) {
        stages.add(stage);
        stage.setPipeline(this);
    }
}
