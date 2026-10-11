package com.pawanputra.bos.identity.api;

/** Persisted by name; must match the CHECK constraint on {@code users.status}. */
public enum UserStatus {
    /** Account created, password not yet set. Cannot sign in. */
    INVITED,
    ACTIVE,
    /** Temporarily blocked (e.g. by an administrator); can be reactivated. */
    SUSPENDED,
    /** Permanently switched off (e.g. the person left). */
    DISABLED
}
