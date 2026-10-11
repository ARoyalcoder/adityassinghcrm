package com.pawanputra.bos.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Details of the first administrator, read from the environment. Nothing happens unless both
 * {@code email} and {@code password} are set and the database has no users.
 */
@ConfigurationProperties("app.security.bootstrap-admin")
public record BootstrapAdminProperties(
        String email, String password, String fullName, String organizationCode, String organizationName) {

    boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
