package com.pawanputra.bos.platform.security;

import com.pawanputra.bos.platform.error.ApiException;
import com.pawanputra.bos.platform.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The signed-in user of the current request, as stated by their access token. Any module may use it:
 *
 * <pre>{@code CurrentUser user = CurrentUser.require();}</pre>
 *
 * <p>Roles and permissions are a snapshot taken when the token was issued (at most one access-token
 * lifetime old). Authorise with {@code @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")} on the
 * controller or service method rather than by calling {@link #hasPermission(String)} by hand.
 *
 * @param id             the user's id
 * @param sessionId      the sign-in (device) this request belongs to
 * @param organizationId the organization the user belongs to; use it to scope data access
 * @param roles          role codes, e.g. {@code SALES_MANAGER}
 * @param permissions    permission codes, e.g. {@code CUSTOMER_VIEW}
 */
public record CurrentUser(
        UUID id, UUID sessionId, UUID organizationId, String email, Set<String> roles, Set<String> permissions) {

    public CurrentUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /** Empty when the request is anonymous or runs outside a request (scheduled job, start-up). */
    public static Optional<CurrentUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            return Optional.of(fromJwt(token.getToken()));
        }
        return Optional.empty();
    }

    /** For code that only runs for signed-in users. */
    public static CurrentUser require() {
        return get().orElseThrow(() -> new ApiException(
                ErrorCode.UNAUTHENTICATED, "Authentication is required to access this resource"));
    }

    static CurrentUser fromJwt(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(AccessTokenIssuer.CLAIM_SESSION)),
                UUID.fromString(jwt.getClaimAsString(AccessTokenIssuer.CLAIM_ORGANIZATION)),
                jwt.getClaimAsString(AccessTokenIssuer.CLAIM_EMAIL),
                Set.copyOf(listClaim(jwt, AccessTokenIssuer.CLAIM_ROLES)),
                Set.copyOf(listClaim(jwt, AccessTokenIssuer.CLAIM_PERMISSIONS)));
    }

    static List<String> listClaim(Jwt jwt, String name) {
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? List.of() : values;
    }
}
