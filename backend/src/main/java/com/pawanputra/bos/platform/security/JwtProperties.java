package com.pawanputra.bos.platform.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret         HMAC-SHA256 signing key; start-up fails if it is shorter than 32 characters
 * @param issuer         value of the {@code iss} claim; tokens from any other issuer are rejected
 * @param accessTokenTtl how long an access token is valid. Keep it short: a revoked session or a
 *                       changed permission takes effect for API calls only when the token expires
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {
}
