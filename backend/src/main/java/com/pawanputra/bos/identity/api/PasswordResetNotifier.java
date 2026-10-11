package com.pawanputra.bos.identity.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivers a password-reset (or first-password) token to its owner. The identity module creates and
 * verifies tokens; getting the token to the person is someone else's job.
 *
 * <p>The notification module will provide the real implementation (email / SMS / WhatsApp) by
 * declaring a bean of this type, which replaces the built-in fallback.
 */
public interface PasswordResetNotifier {

    /**
     * @param token     the secret to put in the reset link. It is shown exactly once, here: only its
     *                  hash is stored. Never log it outside local development.
     * @param expiresAt when the token stops working
     */
    void sendPasswordReset(UUID userId, String email, String fullName, String token, Instant expiresAt);
}
