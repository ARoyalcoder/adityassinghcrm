package com.pawanputra.bos.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** The sessions a user is currently signed in with, most recently used first. */
    @Query("select s from UserSession s where s.user.id = :userId and s.revokedAt is null "
            + "and s.expiresAt > :now order by s.lastUsedAt desc")
    List<UserSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

    /** Housekeeping: sessions that ended before the cut-off (their refresh tokens go with them). */
    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :cutoff or s.revokedAt < :cutoff")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
