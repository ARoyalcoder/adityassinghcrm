package com.pawanputra.bos.identity.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param refreshTokenTtl       idle timeout: a session that is not refreshed for this long ends
 * @param sessionMaxLifetime    absolute limit from sign-in, however active the session is
 * @param passwordResetTokenTtl how long a reset link works
 * @param maxFailedLogins       consecutive wrong passwords before the account is locked
 * @param lockDuration          how long that lock lasts
 * @param cookieSecure          send the refresh cookie over HTTPS only (off only for local http)
 */
@Validated
@ConfigurationProperties("app.security.auth")
public record AuthProperties(
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration sessionMaxLifetime,
        @NotNull Duration passwordResetTokenTtl,
        @Min(1) int maxFailedLogins,
        @NotNull Duration lockDuration,
        boolean cookieSecure) {
}
