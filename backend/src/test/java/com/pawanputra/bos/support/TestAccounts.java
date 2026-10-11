package com.pawanputra.bos.support;

import com.pawanputra.bos.identity.api.UserStatus;
import com.pawanputra.bos.identity.internal.Organization;
import com.pawanputra.bos.identity.internal.OrganizationRepository;
import com.pawanputra.bos.identity.internal.RoleRepository;
import com.pawanputra.bos.identity.internal.User;
import com.pawanputra.bos.identity.internal.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates committed users for integration tests that go through HTTP. Every organization code and
 * email is random, so tests never collide and nothing needs cleaning up.
 */
@Component
public class TestAccounts {

    /** Not a real credential: only ever hashed into a throwaway test database. */
    public static final String PASSWORD = "Test-Password-123";

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public TestAccounts(
            OrganizationRepository organizations,
            UserRepository users,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transaction) {
        this.organizations = organizations;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
    }

    public record Account(UUID id, UUID organizationId, String email, String password) {
    }

    /** A new organization containing one active user with the given roles. */
    public Account activeUser(String... roleCodes) {
        return create(newOrganization(), UserStatus.ACTIVE, true, roleCodes);
    }

    /** Another active user in the same organization as {@code colleague}. */
    public Account activeColleagueOf(Account colleague, String... roleCodes) {
        return create(colleague.organizationId(), UserStatus.ACTIVE, true, roleCodes);
    }

    public Account suspendedUser(String... roleCodes) {
        return create(newOrganization(), UserStatus.SUSPENDED, true, roleCodes);
    }

    /** Invited: exists, but has never set a password. */
    public Account invitedUser(String... roleCodes) {
        return create(newOrganization(), UserStatus.INVITED, false, roleCodes);
    }

    public UserStatus statusOf(Account account) {
        return transaction.execute(tx -> users.findById(account.id()).orElseThrow().getStatus());
    }

    private UUID newOrganization() {
        String code = "T" + random(11).toUpperCase();
        return transaction.execute(tx -> organizations.save(new Organization(code, "Test " + code)).getId());
    }

    private Account create(UUID organizationId, UserStatus status, boolean withPassword, String... roleCodes) {
        String email = "user-" + random(16) + "@example.com";
        UUID id = transaction.execute(tx -> {
            Organization organization = organizations.findById(organizationId).orElseThrow();
            User user = new User(organization, email, "Test User");
            if (withPassword) {
                user.changePassword(passwordEncoder.encode(PASSWORD), Instant.now());
            }
            user.setStatus(status);
            for (String code : roleCodes) {
                user.assignRole(roles.findByCode(code).orElseThrow());
            }
            return users.save(user).getId();
        });
        return new Account(id, organizationId, email, PASSWORD);
    }

    private static String random(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
