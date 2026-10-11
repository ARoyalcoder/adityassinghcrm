package com.pawanputra.bos.project.internal.template;

import com.pawanputra.bos.audit.api.Audited;
import com.pawanputra.bos.platform.persistence.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * The plan an organization follows for projects of one service. Its stages are separate rows
 * ({@link ProjectTemplateStage}), replaced as a whole whenever the template is saved.
 */
@Entity
@Audited(module = "projects", entity = "ProjectTemplate", label = "name")
@Table(name = "project_templates")
@SQLRestriction(SoftDeletableEntity.NOT_DELETED)
public class ProjectTemplate extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /** A service of the catalog module, by id. Fixed: a template for another service is another template. */
    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "revision", nullable = false)
    private int revision = 1;

    protected ProjectTemplate() {
        // for JPA
    }

    public ProjectTemplate(UUID organizationId, UUID serviceId, String name) {
        this.organizationId = organizationId;
        this.serviceId = serviceId;
        this.name = name;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getRevision() {
        return revision;
    }

    /**
     * Marks that the stages were saved. The stages live in other rows, so this is what makes the
     * save show in the audit trail and what makes a concurrent edit of the same template fail.
     */
    public void stagesSaved() {
        this.revision++;
    }
}
