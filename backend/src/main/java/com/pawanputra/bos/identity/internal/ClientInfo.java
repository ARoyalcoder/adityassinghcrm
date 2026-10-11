package com.pawanputra.bos.identity.internal;

/**
 * Where a sign-in or refresh came from. Shown to the user in their list of sessions; never used to
 * make a security decision (both values are easy to fake).
 */
public record ClientInfo(String ipAddress, String userAgent) {
}
