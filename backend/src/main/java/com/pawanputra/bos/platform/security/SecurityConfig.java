package com.pawanputra.bos.platform.security;

import com.pawanputra.bos.platform.error.ErrorCode;
import com.pawanputra.bos.platform.logging.RequestCorrelationFilter;
import com.pawanputra.bos.platform.web.ApiErrorWriter;
import com.pawanputra.bos.platform.web.ApiPaths;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless, deny-by-default API security.
 *
 * <ul>
 *   <li><b>Authentication</b>: {@code Authorization: Bearer <access token>}. A missing, malformed,
 *       expired or wrongly signed token is 401.</li>
 *   <li><b>Authorisation</b>: every endpoint not listed below needs a valid token; what the user may
 *       do is decided per method with {@code @PreAuthorize("hasAuthority('MODULE_ACTION')")}.
 *       A valid token without the permission is 403.</li>
 * </ul>
 *
 * A new endpoint is therefore private until it is deliberately added to one of the lists here.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    /**
     * Roles and permissions are both granted authorities, so they must not be able to collide:
     * there is a real permission called {@code ROLE_VIEW}, and an administrator could one day create a
     * role called {@code VIEW}. Role authorities therefore use a prefix containing {@code ':'}, which
     * the database forbids in permission codes. {@code hasRole('ADMIN')} checks for {@code ROLE:ADMIN}.
     */
    public static final String ROLE_PREFIX = "ROLE:";

    static final String[] PUBLIC_GET_ENDPOINTS = {
        ApiPaths.V1 + "/system/**",
        ApiPaths.V1 + "/service-verticals/**",
    };

    /** Called before (or instead of) having an access token. Everything else under /auth needs one. */
    static final String[] PUBLIC_AUTH_ENDPOINTS = {
        ApiPaths.V1 + "/auth/login",
        ApiPaths.V1 + "/auth/refresh",
        ApiPaths.V1 + "/auth/logout",
        ApiPaths.V1 + "/auth/password/forgot",
        ApiPaths.V1 + "/auth/password/reset",
    };

    static final String[] OPERATIONAL_ENDPOINTS = {
        "/actuator/health/**",
        "/actuator/info",
        "/actuator/prometheus", // not routed by Nginx; reachable on the internal network only
    };

    static final String[] API_DOCS_ENDPOINTS = {
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
    };

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiErrorWriter errorWriter) throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, ex) -> errorWriter.write(
                request, response, ErrorCode.UNAUTHENTICATED,
                "Authentication is required to access this resource");
        AccessDeniedHandler forbidden = (request, response, ex) -> errorWriter.write(
                request, response, ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action");

        http
                // Bearer tokens are not sent automatically by browsers, so there is nothing for CSRF to ride on.
                // The refresh cookie is SameSite=Strict and only readable by the /auth endpoints.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(OPERATIONAL_ENDPOINTS).permitAll()
                        .requestMatchers(API_DOCS_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    /** Turns a verified token into the request's authentication: name = user id, authorities = permissions + roles. */
    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            for (String permission : CurrentUser.listClaim(jwt, AccessTokenIssuer.CLAIM_PERMISSIONS)) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
            for (String role : CurrentUser.listClaim(jwt, AccessTokenIssuer.CLAIM_ROLES)) {
                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    /** Makes {@code hasRole(...)} everywhere use {@link #ROLE_PREFIX} instead of Spring's default. */
    @Bean
    static GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults(ROLE_PREFIX);
    }

    /**
     * Hashes are stored with an algorithm prefix ({@code {bcrypt}...}), so the algorithm can be
     * changed later without invalidating existing passwords.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                RequestCorrelationFilter.HEADER));
        config.setExposedHeaders(List.of(RequestCorrelationFilter.HEADER, HttpHeaders.LOCATION));
        // Needed for the refresh cookie when the UI is served from another origin. Safe because
        // origins are an explicit list, never a wildcard.
        config.setAllowCredentials(!properties.allowedOrigins().isEmpty());
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Deliberately empty: users are authenticated by the identity module against the database, not
     * through Spring's UserDetailsService. Declaring it stops Spring Boot generating a default user.
     */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
