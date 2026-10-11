package com.pawanputra.bos.project.internal;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_milestones")
public class Milestone implements Persistable<UUID> {

    // The id is assigned when the object is created, so Spring Data cannot tell a new row from an
    // existing one by looking at it, and would merge a copy instead of inserting this object. Since
    // the object is also in its parent's collection, that copy and this object then collide.
    @Transient
    private boolean newRow = true;

    @Override
    public boolean isNew() {
        return newRow;
    }

    @PostPersist
    @PostLoad
    void markStored() {
        this.newRow = false;
    }


    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "progress_percentage", nullable = false)
    private int progressPercentage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected Milestone() {
    }

    public Milestone(
            Project project,
            String title,
            String description,
            LocalDate dueDate,
            int displayOrder,
            String actor) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.displayOrder = displayOrder;
        this.status = "PENDING";
        this.progressPercentage = 0;
        this.createdAt = Instant.now();
        this.createdBy = actor;
        this.updatedAt = this.createdAt;
        this.updatedBy = actor;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(LocalDate completedDate) {
        this.completedDate = completedDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(int progressPercentage) {
        this.progressPercentage = Math.max(0, Math.min(100, progressPercentage));
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
