package com.pawanputra.bos.identity.web;

import com.pawanputra.bos.identity.internal.AuthProperties;
import com.pawanputra.bos.platform.web.ApiPaths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The cookie that carries the refresh token.
 *
 * <ul>
 *   <li>{@code HttpOnly}: page scripts cannot read it, so an XSS bug cannot steal the long-lived token.</li>
 *   <li>{@code SameSite=Strict}: other sites cannot make the browser send it (CSRF).</li>
 *   <li>{@code Path=/api/v1/auth}: it is only ever sent to the authentication endpoints.</li>
 *   <li>{@code Secure}: HTTPS only, except in local development.</li>
 * </ul>
 */
@Component
class RefreshCookie {

    static final String NAME = "bos_refresh";
    private static final String PATH = ApiPaths.V1 + "/auth";

    private final AuthProperties properties;
    private final Clock clock;

    RefreshCookie(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    String issue(String refreshToken, Instant expiresAt) {
        return build(refreshToken, Duration.between(clock.instant(), expiresAt)).toString();
    }

    /** Tells the browser to delete the cookie. */
    String clear() {
        return build("", Duration.ZERO).toString();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }
}
