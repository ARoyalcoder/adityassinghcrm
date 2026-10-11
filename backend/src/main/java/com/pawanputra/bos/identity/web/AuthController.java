package com.pawanputra.bos.identity.web;

import com.pawanputra.bos.identity.internal.AuthService;
import com.pawanputra.bos.identity.internal.AuthService.IssuedTokens;
import com.pawanputra.bos.identity.internal.ClientInfo;
import com.pawanputra.bos.identity.internal.SessionService;
import com.pawanputra.bos.identity.web.AuthDtos.ChangePasswordRequest;
import com.pawanputra.bos.identity.web.AuthDtos.CurrentUserResponse;
import com.pawanputra.bos.identity.web.AuthDtos.ForgotPasswordRequest;
import com.pawanputra.bos.identity.web.AuthDtos.LoginRequest;
import com.pawanputra.bos.identity.web.AuthDtos.ResetPasswordRequest;
import com.pawanputra.bos.identity.web.AuthDtos.SessionResponse;
import com.pawanputra.bos.identity.web.AuthDtos.TokenResponse;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in and account self-service. Which of these are reachable without an access token is decided
 * in {@code SecurityConfig.PUBLIC_AUTH_ENDPOINTS}, not here.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, SessionService sessionService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in. Returns an access token and sets the refresh-token cookie. Public.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return tokenResponse(authService.login(request.email(), request.password(), clientInfo(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a new access token and a new cookie. "
            + "Each cookie works once. Public (authenticated by the cookie).")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken, HttpServletRequest http) {
        return tokenResponse(authService.refresh(refreshToken, clientInfo(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "End the session of the refresh-token cookie and delete the cookie. "
            + "Always succeeds. Public (authenticated by the cookie).")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.clear()).build();
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "The signed-in user with their current roles and permissions.")
    public CurrentUserResponse me() {
        return CurrentUserResponse.from(authService.loadCurrentUser(CurrentUser.require()));
    }

    @PostMapping("/password/change")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Change your own password. Signs out every other device.")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.require(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Ask for a password-reset link. Always answers 202, whether or not the email is known. Public.")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password using a reset token. Signs out every device. "
            + "Also how an invited user sets their first password. Public.")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.clear()).build();
    }

    @GetMapping("/sessions")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "The devices you are signed in on.")
    public List<SessionResponse> sessions() {
        CurrentUser user = CurrentUser.require();
        return sessionService.listActive(user.id()).stream()
                .map(session -> SessionResponse.from(session, user.sessionId()))
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Sign one of your devices out.")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId) {
        sessionService.revokeOwn(CurrentUser.require().id(), sessionId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie.issue(tokens.refreshToken(), tokens.refreshTokenExpiresAt()))
                .cacheControl(CacheControl.noStore())
                .body(TokenResponse.from(tokens));
    }

    private static ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
