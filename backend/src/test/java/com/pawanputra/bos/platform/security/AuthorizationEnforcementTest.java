package com.pawanputra.bos.platform.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pawanputra.bos.support.WebLayerTestConfig;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves that authorisation is enforced by the backend: real signed tokens go through the real
 * security filter chain and method security. 401 = "we do not know who you are",
 * 403 = "we know who you are and you may not do this".
 */
@WebMvcTest(AuthorizationEnforcementTest.ProbeController.class)
@Import({WebLayerTestConfig.class, AuthorizationEnforcementTest.ProbeController.class})
@ActiveProfiles("test")
class AuthorizationEnforcementTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccessTokenIssuer issuer;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties properties;

    // ---------------------------------------------------------------- 401: not authenticated

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/secure/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void endpointWithNoPermissionRuleStillNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/v1/secure/anyone")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/secure/anyone").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tamperedTokenIsUnauthorized() throws Exception {
        String token = tokenFor(Set.of(), Set.of("CUSTOMER_VIEW"));
        // Change the first character of the signature. (Not the last: its two lowest bits are padding,
        // so some changes there decode to the very same signature and the token is rightly accepted.)
        int at = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, at) + (token.charAt(at) == 'A' ? 'B' : 'A') + token.substring(at + 1);

        mockMvc.perform(get("/api/v1/secure/customers").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        JwtEncoder foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "a-completely-different-signing-key-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = new AccessTokenIssuer(foreignEncoder, properties, Clock.systemUTC())
                .issue(user(Set.of("SUPER_ADMIN"), Set.of("CUSTOMER_VIEW"))).value();

        mockMvc.perform(get("/api/v1/secure/customers").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expired = new AccessTokenIssuer(encoder, properties, twoHoursAgo)
                .issue(user(Set.of(), Set.of("CUSTOMER_VIEW"))).value();

        mockMvc.perform(get("/api/v1/secure/customers").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsUnauthorized() throws Exception {
        JwtProperties otherIssuer = new JwtProperties(properties.secret(), "someone-else", properties.accessTokenTtl());
        String token = new AccessTokenIssuer(encoder, otherIssuer, Clock.systemUTC())
                .issue(user(Set.of(), Set.of("CUSTOMER_VIEW"))).value();

        mockMvc.perform(get("/api/v1/secure/customers").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 403: authenticated, not allowed

    @Test
    void validTokenWithoutThePermissionIsForbidden() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/secure/customers"), Set.of("SALES_EXECUTIVE"), Set.of("PROJECT_VIEW")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void viewPermissionDoesNotAllowAWrite() throws Exception {
        mockMvc.perform(authorized(post("/api/v1/secure/finance/approve"), Set.of("FINANCE"), Set.of("FINANCE_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validTokenWithNoPermissionsAtAllIsForbidden() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/secure/customers"), Set.of("VENDOR"), Set.of()))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleCheckRejectsOtherRoles() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/secure/admins-only"), Set.of("SALES_MANAGER"), Set.of("CUSTOMER_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesAndPermissionsCannotStandInForEachOther() throws Exception {
        // A role named like a permission does not grant the permission...
        mockMvc.perform(authorized(get("/api/v1/secure/customers"), Set.of("CUSTOMER_VIEW"), Set.of()))
                .andExpect(status().isForbidden());
        // ...not even via Spring's conventional ROLE_ prefix (role "VIEW" vs permission "ROLE_VIEW")...
        mockMvc.perform(authorized(get("/api/v1/secure/roles"), Set.of("VIEW"), Set.of()))
                .andExpect(status().isForbidden());
        // ...and a permission named like a role does not grant the role.
        mockMvc.perform(authorized(get("/api/v1/secure/admins-only"), Set.of(), Set.of("ADMIN", "ROLE_ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(authorized(get("/api/v1/secure/roles"), Set.of(), Set.of("ROLE_VIEW")))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- 200: allowed

    @Test
    void validTokenWithThePermissionIsAllowed() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/secure/customers"), Set.of("SALES_EXECUTIVE"), Set.of("CUSTOMER_VIEW")))
                .andExpect(status().isOk());
        mockMvc.perform(authorized(post("/api/v1/secure/finance/approve"), Set.of("FINANCE"),
                        Set.of("FINANCE_VIEW", "FINANCE_APPROVE")))
                .andExpect(status().isOk());
        mockMvc.perform(authorized(get("/api/v1/secure/admins-only"), Set.of("ADMIN"), Set.of()))
                .andExpect(status().isOk());
    }

    @Test
    void anyValidTokenReachesAnEndpointWithoutAPermissionRule() throws Exception {
        mockMvc.perform(authorized(get("/api/v1/secure/anyone"), Set.of("VENDOR"), Set.of()))
                .andExpect(status().isOk());
    }

    @Test
    void securityContextExposesTheTokensUser() throws Exception {
        CurrentUser user = user(Set.of("SALES_MANAGER"), Set.of("CUSTOMER_VIEW", "CUSTOMER_UPDATE"));

        mockMvc.perform(get("/api/v1/secure/whoami")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue(user).value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id().toString()))
                .andExpect(jsonPath("$.sessionId").value(user.sessionId().toString()))
                .andExpect(jsonPath("$.organizationId").value(user.organizationId().toString()))
                .andExpect(jsonPath("$.email").value("someone@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("SALES_MANAGER"))
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.auditor").value(user.id().toString()));
    }

    private MockHttpServletRequestBuilder authorized(
            MockHttpServletRequestBuilder request, Set<String> roles, Set<String> permissions) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(roles, permissions));
    }

    private String tokenFor(Set<String> roles, Set<String> permissions) {
        return issuer.issue(user(roles, permissions)).value();
    }

    private static CurrentUser user(Set<String> roles, Set<String> permissions) {
        return new CurrentUser(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "someone@example.com", roles, permissions);
    }

    @RestController
    @RequestMapping("/api/v1/secure")
    static class ProbeController {

        @GetMapping("/anyone")
        String anyone() {
            return "ok";
        }

        @GetMapping("/customers")
        @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
        String customers() {
            return "ok";
        }

        @PostMapping("/finance/approve")
        @PreAuthorize("hasAuthority('FINANCE_APPROVE')")
        String approve() {
            return "ok";
        }

        @GetMapping("/roles")
        @PreAuthorize("hasAuthority('ROLE_VIEW')")
        String roles() {
            return "ok";
        }

        @GetMapping("/admins-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminsOnly() {
            return "ok";
        }

        @GetMapping("/whoami")
        Map<String, Object> whoAmI(org.springframework.security.core.Authentication authentication) {
            CurrentUser user = CurrentUser.require();
            return Map.of(
                    "id", user.id(),
                    "sessionId", user.sessionId(),
                    "organizationId", user.organizationId(),
                    "email", user.email(),
                    "roles", user.roles(),
                    "permissions", user.permissions(),
                    // What JPA auditing records in created_by / updated_by.
                    "auditor", authentication.getName());
        }
    }
}
