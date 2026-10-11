package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.identity.api.SessionRevocationReason;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A user's sign-ins (devices): listing them and signing them out. */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final UserSessionRepository sessions;
    private final Clock clock;

    public SessionService(UserSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserSession> listActive(UUID userId) {
        return sessions.findActiveByUserId(userId, clock.instant());
    }

    /** Signs one of the user's own devices out. Another user's session id is reported as not found. */
    @Transactional
    public void revokeOwn(UUID userId, UUID sessionId) {
        UserSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        session.revoke(SessionRevocationReason.REVOKED_BY_USER, clock.instant());
    }

    /**
     * Signs the user out everywhere, optionally keeping one session (the one making the request).
     * Joins the caller's transaction.
     *
     * @return how many sessions were ended
     */
    @Transactional
    public int revokeAll(UUID userId, SessionRevocationReason reason, UUID exceptSessionId) {
        Instant now = clock.instant();
        int revoked = 0;
        for (UserSession session : sessions.findActiveByUserId(userId, now)) {
            if (!session.getId().equals(exceptSessionId)) {
                session.revoke(reason, now);
                revoked++;
            }
        }
        if (revoked > 0) {
            log.info("Sessions revoked: userId={} reason={} count={}", userId, reason, revoked);
        }
        return revoked;
    }
}
