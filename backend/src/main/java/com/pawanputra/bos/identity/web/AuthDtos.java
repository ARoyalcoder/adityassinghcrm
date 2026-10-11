package com.pawanputra.bos.identity.web;

import com.pawanputra.bos.identity.api.UserStatus;
import com.pawanputra.bos.identity.internal.AuthService.IssuedTokens;
import com.pawanputra.bos.identity.internal.User;
import com.pawanputra.bos.identity.internal.UserSession;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Request and response bodies of the authentication endpoints. */
final class AuthDtos {

    /** New passwords: long enough to resist guessing, bounded so hashing cannot be abused. */
    static final int PASSWORD_MIN = 10;
    static final int PASSWORD_MAX = 128;

    private AuthDtos() {
    }

    record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = PASSWORD_MAX) String password) {
    }

    record ChangePasswordRequest(
            @NotBlank @Size(max = PASSWORD_MAX) String currentPassword,
            @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String newPassword) {
    }

    record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String newPassword) {
    }

    /**
     * @param accessToken send as {@code Authorization: Bearer <accessToken>}; keep it in memory only
     * @param expiresIn   seconds until the access token expires
     */
    record TokenResponse(String accessToken, String tokenType, long expiresIn, CurrentUserResponse user) {

        static TokenResponse from(IssuedTokens tokens) {
            return new TokenResponse(
                    tokens.accessToken().value(),
                    "Bearer",
                    tokens.accessToken().expiresInSeconds(),
                    CurrentUserResponse.from(tokens.user()));
        }
    }

    /** Who is signed in and what they may do. The UI uses {@code permissions} to show or hide features. */
    record CurrentUserResponse(
            UUID id,
            String email,
            String fullName,
            UserStatus status,
            UUID organizationId,
            UUID branchId,
            Set<String> roles,
            Set<String> permissions) {

        static CurrentUserResponse from(User user) {
            return new CurrentUserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getStatus(),
                    user.getOrganization().getId(),
                    user.getBranch() != null ? user.getBranch().getId() : null,
                    user.activeRoleCodes(),
                    user.activePermissionCodes());
        }
    }

    /** @param current {@code true} for the session that made this request */
    record SessionResponse(
            UUID id, String ipAddress, String userAgent, Instant createdAt, Instant lastUsedAt, Instant expiresAt,
            boolean current) {

        static SessionResponse from(UserSession session, UUID currentSessionId) {
            return new SessionResponse(
                    session.getId(),
                    session.getIpAddress(),
                    session.getUserAgent(),
                    session.getCreatedAt(),
                    session.getLastUsedAt(),
                    session.getExpiresAt(),
                    session.getId().equals(currentSessionId));
        }
    }

    record UserAccountResponse(UUID id, String email, String fullName, UserStatus status) {

        static UserAccountResponse from(User user) {
            return new UserAccountResponse(user.getId(), user.getEmail(), user.getFullName(), user.getStatus());
        }
    }
}
