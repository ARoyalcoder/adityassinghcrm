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
import java.util.UUID;

@Entity
@Table(name = "project_checklist_items")
public class ChecklistItem implements Persistable<UUID> {

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
    @JoinColumn(name = "task_id", nullable = false)
    private ProjectTask task;

    @Column(name = "item_text", nullable = false, length = 500)
    private String itemText;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    protected ChecklistItem() {
    }

    public ChecklistItem(ProjectTask task, String itemText, int displayOrder, String actor) {
        this.id = UUID.randomUUID();
        this.task = task;
        this.itemText = itemText;
        this.completed = false;
        this.displayOrder = displayOrder;
        this.createdAt = Instant.now();
        this.createdBy = actor;
    }

    public UUID getId() {
        return id;
    }

    public ProjectTask getTask() {
        return task;
    }

    public void setTask(ProjectTask task) {
        this.task = task;
    }

    public String getItemText() {
        return itemText;
    }

    public void setItemText(String itemText) {
        this.itemText = itemText;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed, String actor) {
        this.completed = completed;
        if (completed) {
            this.completedAt = Instant.now();
            this.completedBy = actor;
        } else {
            this.completedAt = null;
            this.completedBy = null;
        }
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
