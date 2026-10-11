package com.pawanputra.bos.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes sessions and reset tokens that ended long ago, so the tables do not grow forever.
 * Rows are kept for a while after they end because they are the record of who signed in from where.
 * Safe to run on several instances at once: the deletes are idempotent.
 */
@Component
public class AuthHousekeepingJob {

    private static final Logger log = LoggerFactory.getLogger(AuthHousekeepingJob.class);
    private static final Duration RETENTION = Duration.ofDays(90);

    private final UserSessionRepository sessions;
    private final PasswordResetTokenRepository resetTokens;
    private final Clock clock;

    public AuthHousekeepingJob(UserSessionRepository sessions, PasswordResetTokenRepository resetTokens, Clock clock) {
        this.sessions = sessions;
        this.resetTokens = resetTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    @Transactional
    public void purgeEndedSessionsAndTokens() {
        Instant cutoff = clock.instant().minus(RETENTION);
        int removedSessions = sessions.deleteEndedBefore(cutoff);
        int removedTokens = resetTokens.deleteExpiredBefore(cutoff);
        log.info("Auth housekeeping finished: sessionsRemoved={} resetTokensRemoved={}", removedSessions, removedTokens);
    }
}
