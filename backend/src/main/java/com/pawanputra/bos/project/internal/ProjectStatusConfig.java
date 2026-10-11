package com.pawanputra.bos.project.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_statuses")
public class ProjectStatusConfig {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "color", nullable = false, length = 30)
    private String color;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted;

    @Column(name = "is_cancelled", nullable = false)
    private boolean isCancelled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected ProjectStatusConfig() {
    }

    public ProjectStatusConfig(
            UUID organizationId,
            String code,
            String name,
            int displayOrder,
            String color,
            String category,
            boolean isDefault,
            boolean isCompleted,
            boolean isCancelled,
            String actor) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.code = code;
        this.name = name;
        this.displayOrder = displayOrder;
        this.color = color != null ? color : "blue";
        this.category = category != null ? category : "IN_PROGRESS";
        this.isDefault = isDefault;
        this.isCompleted = isCompleted;
        this.isCancelled = isCancelled;
        this.createdAt = Instant.now();
        this.createdBy = actor;
        this.updatedAt = this.createdAt;
        this.updatedBy = actor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
