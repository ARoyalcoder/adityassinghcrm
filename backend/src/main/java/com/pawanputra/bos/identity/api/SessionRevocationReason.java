package com.pawanputra.bos.identity.api;

/** Persisted by name; must match the CHECK constraint on {@code user_sessions.revoked_reason}. */
public enum SessionRevocationReason {
    LOGOUT,
    /** The user signed another of their devices out. */
    REVOKED_BY_USER,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    ACCOUNT_DEACTIVATED,
    /** An already-used refresh token was presented again: treated as theft. */
    TOKEN_REUSE
}
