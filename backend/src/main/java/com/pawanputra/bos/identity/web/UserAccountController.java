package com.pawanputra.bos.identity.web;

import com.pawanputra.bos.identity.api.Permissions;
import com.pawanputra.bos.identity.internal.UserAccountService;
import com.pawanputra.bos.identity.web.AuthDtos.UserAccountResponse;
import com.pawanputra.bos.platform.config.OpenApiConfig;
import com.pawanputra.bos.platform.security.CurrentUser;
import com.pawanputra.bos.platform.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account activation and deactivation by an administrator. (User CRUD comes with the user-management module.) */
@RestController
@RequestMapping(ApiPaths.V1 + "/users")
@Tag(name = "User accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class UserAccountController {

    private final UserAccountService accounts;

    public UserAccountController(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/{userId}/activate")
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.USER_UPDATE + "')")
    @Operation(summary = "Let a user sign in again. Requires USER_UPDATE.")
    public UserAccountResponse activate(@PathVariable UUID userId) {
        return UserAccountResponse.from(accounts.activate(userId, CurrentUser.require()));
    }

    @PostMapping("/{userId}/deactivate")
    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.USER_UPDATE + "')")
    @Operation(summary = "Block a user from signing in and end all their sessions. Requires USER_UPDATE.")
    public UserAccountResponse deactivate(@PathVariable UUID userId) {
        return UserAccountResponse.from(accounts.deactivate(userId, CurrentUser.require()));
    }
}
