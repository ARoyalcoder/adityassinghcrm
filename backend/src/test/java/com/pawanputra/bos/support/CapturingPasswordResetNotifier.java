package com.pawanputra.bos.support;

import com.pawanputra.bos.identity.api.PasswordResetNotifier;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Stands in for the notification module in tests: remembers the last reset token "sent" to each
 * email so a test can play the part of the user clicking the link.
 */
@Component
public class CapturingPasswordResetNotifier implements PasswordResetNotifier {

    private final Map<String, String> lastTokenByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendPasswordReset(UUID userId, String email, String fullName, String token, Instant expiresAt) {
        lastTokenByEmail.put(email, token);
    }

    /** {@code null} if nothing was sent to this address. */
    public String lastTokenFor(String email) {
        return lastTokenByEmail.get(email);
    }
}
