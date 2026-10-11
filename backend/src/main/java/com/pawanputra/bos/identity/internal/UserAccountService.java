package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.identity.api.RoleCodes;
import com.pawanputra.bos.identity.api.SessionRevocationReason;
import com.pawanputra.bos.identity.api.UserStatus;
import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.platform.security.CurrentUser;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Switching accounts on and off. Callers must already hold {@code USER_UPDATE}. */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserRepository users;
    private final SessionService sessionService;

    public UserAccountService(UserRepository users, SessionService sessionService) {
        this.users = users;
        this.sessionService = sessionService;
    }

    /** Lets the user sign in again. */
    @Transactional
    public User activate(UUID userId, CurrentUser actor) {
        User user = loadManageable(userId, actor);
        if (user.getPasswordHash() == null) {
            throw new BusinessRuleException(
                    "This user has not set a password yet. They become active when they accept their invitation.");
        }
        user.setStatus(UserStatus.ACTIVE);
        log.info("User activated: userId={} by={}", userId, actor.id());
        return user;
    }

    /** Blocks sign-in and ends every session the user has. */
    @Transactional
    public User deactivate(UUID userId, CurrentUser actor) {
        if (userId.equals(actor.id())) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        User user = loadManageable(userId, actor);
        user.setStatus(UserStatus.SUSPENDED);
        sessionService.revokeAll(userId, SessionRevocationReason.ACCOUNT_DEACTIVATED, null);
        log.info("User deactivated: userId={} by={}", userId, actor.id());
        return user;
    }

    /**
     * Users of other organizations are reported as not found, so their existence is not revealed.
     * Only a super admin may switch another super admin on or off; otherwise anyone holding
     * {@code USER_UPDATE} could lock the owners out.
     */
    private User loadManageable(UUID userId, CurrentUser actor) {
        User user = users.findWithRolesById(userId)
                .filter(candidate -> candidate.getOrganization().getId().equals(actor.organizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.activeRoleCodes().contains(RoleCodes.SUPER_ADMIN) && !actor.hasRole(RoleCodes.SUPER_ADMIN)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only a super admin can manage a super admin");
        }
        return user;
    }
}
