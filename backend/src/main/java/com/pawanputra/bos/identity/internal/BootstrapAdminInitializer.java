package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.identity.api.RoleCodes;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solves the "nobody can sign in to create the first user" problem: on start-up, if the database
 * has no users at all and bootstrap credentials are configured, creates one organization and one
 * active SUPER_ADMIN. It never touches a database that already has a user, so leaving the
 * variables set is harmless, but remove them (and change the password) after the first sign-in.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 10;

    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            UserRepository users,
            OrganizationRepository organizations,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.properties = properties;
        this.users = users;
        this.organizations = organizations;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured() || users.count() > 0) {
            return;
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        Role superAdmin = roles.findByCode(RoleCodes.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role is missing; migrations did not run"));
        Organization organization = organizations.findByCode(properties.organizationCode())
                .orElseGet(() -> organizations.save(
                        new Organization(properties.organizationCode(), properties.organizationName())));

        User admin = new User(organization, properties.email(), properties.fullName());
        admin.changePassword(passwordEncoder.encode(properties.password()), clock.instant());
        admin.assignRole(superAdmin);
        users.save(admin);
        log.warn("Created the first administrator ({}). Sign in, change the password, "
                + "then remove BOOTSTRAP_ADMIN_* from the environment.", admin.getEmail());
    }
}
