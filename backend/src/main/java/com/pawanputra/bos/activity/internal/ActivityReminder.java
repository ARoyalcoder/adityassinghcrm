package com.pawanputra.bos.activity.internal;

import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_reminders")
public class ActivityReminder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // PENDING, SENT, DISMISSED

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel = "IN_APP";

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    protected ActivityReminder() {
        // JPA
    }

    public ActivityReminder(
            Activity activity, UUID organizationId, UUID userId, Instant remindAt, String channel, String createdBy) {
        this.activity = activity;
        this.organizationId = organizationId;
        this.userId = userId;
        this.remindAt = remindAt;
        this.channel = channel != null ? channel : "IN_APP";
        this.createdBy = createdBy != null ? createdBy : "system";
        this.status = "PENDING";
    }

    public Activity getActivity() {
        return activity;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public String getStatus() {
        return status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getChannel() {
        return channel;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void markSent(Instant when) {
        this.status = "SENT";
        this.sentAt = when;
    }

    public void markDismissed() {
        this.status = "DISMISSED";
    }
}
