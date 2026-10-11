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
@Table(name = "project_members")
public class ProjectMember implements Persistable<UUID> {

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

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "assigned_by", nullable = false, length = 100)
    private String assignedBy;

    protected ProjectMember() {
    }

    public ProjectMember(
            Project project,
            UUID userId,
            String userName,
            String userEmail,
            String role,
            String assignedBy) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.role = role != null ? role : "CONTRIBUTOR";
        this.joinedAt = Instant.now();
        this.assignedBy = assignedBy;
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

    public UUID getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public String getAssignedBy() {
        return assignedBy;
    }
}
