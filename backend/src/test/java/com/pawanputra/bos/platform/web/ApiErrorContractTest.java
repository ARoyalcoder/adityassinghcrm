package com.pawanputra.bos.platform.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pawanputra.bos.platform.error.BusinessRuleException;
import com.pawanputra.bos.platform.error.ResourceNotFoundException;
import com.pawanputra.bos.support.WebLayerTestConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pins the error contract documented in docs/api-conventions.md, including errors raised by security. */
@WebMvcTest(ApiErrorContractTest.ProbeController.class)
@Import({WebLayerTestConfig.class, ApiErrorContractTest.ProbeController.class})
@ActiveProfiles("test")
class ApiErrorContractTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    MockMvc mockMvc;

    @Test
    void unauthenticatedRequestGetsProblemJsonWithRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/probe/ok"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.type").value("urn:bos:error:unauthenticated"))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/probe/ok"))
                .andExpect(jsonPath("$.requestId").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @WithMockUser
    void domainExceptionsMapToTheirErrorCode() throws Exception {
        mockMvc.perform(get("/api/v1/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Lead '42' was not found"));

        mockMvc.perform(get("/api/v1/probe/rule"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @WithMockUser
    void unexpectedExceptionsNeverLeakInternals() throws Exception {
        mockMvc.perform(get("/api/v1/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret-internal-detail"))));
    }

    @Test
    @WithMockUser
    void bodyValidationListsEveryInvalidField() throws Exception {
        mockMvc.perform(post("/api/v1/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "quantity")))
                .andExpect(jsonPath("$.errors[0].message").isString());
    }

    @Test
    @WithMockUser
    void unreadableBodyIsMalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @WithMockUser
    void frameworkErrorsUseTheSameBody() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/probe/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/probe/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @WithMockUser
    void aSafeCallerSuppliedRequestIdIsKept() throws Exception {
        mockMvc.perform(get("/api/v1/probe/not-found").header("X-Request-Id", "client-abc-12345"))
                .andExpect(header().string("X-Request-Id", "client-abc-12345"))
                .andExpect(jsonPath("$.requestId").value("client-abc-12345"));
    }

    @Test
    @WithMockUser
    void anUnsafeCallerSuppliedRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/api/v1/probe/ok").header("X-Request-Id", "bad id with spaces"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));
    }

    @RestController
    @RequestMapping("/api/v1/probe")
    static class ProbeController {

        record ProbeRequest(@NotBlank String name, @Min(1) int quantity) {
        }

        @GetMapping("/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/not-found")
        String notFound() {
            throw new ResourceNotFoundException("Lead", 42);
        }

        @GetMapping("/rule")
        String rule() {
            throw new BusinessRuleException("A closed deal cannot be reopened");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret-internal-detail");
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody ProbeRequest request) {
            return request.name();
        }
    }
}
