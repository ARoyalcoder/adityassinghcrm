package com.pawanputra.bos.platform.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Creates signed access tokens. The token carries everything needed to authorise a request
 * (see {@link CurrentUser}), so the API does not hit the database to authenticate.
 */
public class AccessTokenIssuer {

    static final String CLAIM_SESSION = "sid";
    static final String CLAIM_ORGANIZATION = "org";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_PERMISSIONS = "permissions";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param value            the compact JWT to send as {@code Authorization: Bearer <value>}
     * @param expiresInSeconds lifetime from the moment of issue
     */
    public record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }

    public AccessToken issue(CurrentUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_SESSION, user.sessionId().toString())
                .claim(CLAIM_ORGANIZATION, user.organizationId().toString())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLES, List.copyOf(user.roles()))
                .claim(CLAIM_PERMISSIONS, List.copyOf(user.permissions()))
                .build();
        String value = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AccessToken(value, expiresAt, properties.accessTokenTtl().toSeconds());
    }
}
