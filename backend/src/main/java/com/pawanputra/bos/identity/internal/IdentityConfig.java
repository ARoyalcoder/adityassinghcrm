package com.pawanputra.bos.identity.internal;

import com.pawanputra.bos.identity.api.PasswordResetNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, BootstrapAdminProperties.class})
public class IdentityConfig {

    private static final Logger log = LoggerFactory.getLogger(IdentityConfig.class);

    /**
     * Used until the notification module supplies a real {@link PasswordResetNotifier}.
     * Reset tokens are created and verified correctly, but nothing delivers them: on a developer
     * machine the token is written to the log so the flow can be exercised; everywhere else it is
     * deliberately dropped (a token in a production log would let any log reader take over the account).
     */
    @Bean
    @ConditionalOnMissingBean(PasswordResetNotifier.class)
    PasswordResetNotifier undeliveredPasswordResetNotifier(Environment environment) {
        boolean developerMachine = environment.acceptsProfiles(Profiles.of("local"));
        return (userId, email, fullName, token, expiresAt) -> {
            if (developerMachine) {
                log.info("LOCAL ONLY - password reset token for userId={}: {} (valid until {})",
                        userId, token, expiresAt);
            } else {
                log.warn("Password reset requested for userId={} but no delivery channel is configured; "
                        + "the token was not sent", userId);
            }
        };
    }
}
