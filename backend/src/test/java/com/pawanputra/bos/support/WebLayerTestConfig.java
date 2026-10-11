package com.pawanputra.bos.support;

import com.pawanputra.bos.platform.config.TimeConfig;
import com.pawanputra.bos.platform.security.JwtConfig;
import com.pawanputra.bos.platform.security.SecurityConfig;
import com.pawanputra.bos.platform.web.ApiErrorWriter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Import into every {@code @WebMvcTest} so the slice runs with the production security rules and
 * error format. (The filter and the exception handler are picked up by the slice automatically.)
 */
@TestConfiguration
@Import({SecurityConfig.class, JwtConfig.class, ApiErrorWriter.class, TimeConfig.class})
public class WebLayerTestConfig {
}
