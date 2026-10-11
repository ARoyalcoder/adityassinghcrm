package com.pawanputra.bos.project.internal.template;

import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * One stage of a template. The lists inside a stage are JSON, read and written through
 * {@link TemplateJson}; they are always edited and read together with their stage.
 */
@Entity
@Table(name = "project_template_stages")
public class ProjectTemplateStage extends BaseEntity {

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Column(name = "stage_key", nullable = false, updatable = false, length = 60)
    private String stageKey;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "responsible_role", length = 50)
    private String responsibleRole;

    @Column(name = "depends_on", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String dependsOnJson;

    @Column(name = "milestones", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String milestonesJson;

    @Column(name = "tasks", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String tasksJson;

    @Column(name = "documents", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String documentsJson;

    protected ProjectTemplateStage() {
        // for JPA
    }

    @SuppressWarnings("java:S107") // one constructor argument per column; a stage is created whole
    public ProjectTemplateStage(
            UUID templateId, String stageKey, String name, String description, int position, int durationDays,
            String responsibleRole, String dependsOnJson, String milestonesJson, String tasksJson,
            String documentsJson) {
        this.templateId = templateId;
        this.stageKey = stageKey;
        this.name = name;
        this.description = description;
        this.position = position;
        this.durationDays = durationDays;
        this.responsibleRole = responsibleRole;
        this.dependsOnJson = dependsOnJson;
        this.milestonesJson = milestonesJson;
        this.tasksJson = tasksJson;
        this.documentsJson = documentsJson;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getStageKey() {
        return stageKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getPosition() {
        return position;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public String getResponsibleRole() {
        return responsibleRole;
    }

    public String getDependsOnJson() {
        return dependsOnJson;
    }

    public String getMilestonesJson() {
        return milestonesJson;
    }

    public String getTasksJson() {
        return tasksJson;
    }

    public String getDocumentsJson() {
        return documentsJson;
    }
}
