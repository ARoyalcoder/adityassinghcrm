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
@Table(name = "project_task_dependencies")
public class TaskDependency implements Persistable<UUID> {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depends_on_task_id", nullable = false)
    private ProjectTask dependsOnTask;

    @Column(name = "dependency_type", nullable = false, length = 30)
    private String dependencyType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    protected TaskDependency() {
    }

    public TaskDependency(ProjectTask task, ProjectTask dependsOnTask, String dependencyType, String actor) {
        this.id = UUID.randomUUID();
        this.task = task;
        this.dependsOnTask = dependsOnTask;
        this.dependencyType = dependencyType != null ? dependencyType : "FINISH_TO_START";
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

    public ProjectTask getDependsOnTask() {
        return dependsOnTask;
    }

    public void setDependsOnTask(ProjectTask dependsOnTask) {
        this.dependsOnTask = dependsOnTask;
    }

    public String getDependencyType() {
        return dependencyType;
    }

    public void setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
