package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.identity.api.SessionRevocationReason;
import com.pawanputra.bos.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** One sign-in on one browser or device. Lives until it is revoked, idles out, or hits its absolute limit. */
@Entity
@Table(name = "user_sessions")
public class UserSession extends BaseEntity {

    private static final int MAX_USER_AGENT_LENGTH = 500;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = MAX_USER_AGENT_LENGTH)
    private String userAgent;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 30)
    private SessionRevocationReason revokedReason;

    protected UserSession() {
        // for JPA
    }

    public UserSession(User user, ClientInfo client, Instant now, Instant expiresAt) {
        this.user = user;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
        seenFrom(client, now);
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** Idempotent: the first revocation wins. */
    public void revoke(SessionRevocationReason reason, Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = reason;
        }
    }

    /** Records where the session was last used from. */
    public void seenFrom(ClientInfo client, Instant now) {
        this.lastUsedAt = now;
        this.ipAddress = client.ipAddress();
        String agent = client.userAgent();
        this.userAgent = agent != null && agent.length() > MAX_USER_AGENT_LENGTH
                ? agent.substring(0, MAX_USER_AGENT_LENGTH)
                : agent;
    }

    public User getUser() {
        return user;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public SessionRevocationReason getRevokedReason() {
        return revokedReason;
    }
}
