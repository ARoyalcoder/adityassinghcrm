package com.pawanputra.bos.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Inject this instead of calling {@code Instant.now()} so time can be controlled in tests. Always UTC. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
