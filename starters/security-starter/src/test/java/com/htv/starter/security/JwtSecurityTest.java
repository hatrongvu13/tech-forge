package com.htv.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.auth.RequirePermission;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ContextHolder;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chuỗi bảo mật đầy đủ với JWT ký thật (RSA) và quyền tra qua PermissionProvider.
 */
@SpringBootTest(classes = JwtSecurityTest.App.class)
@AutoConfigureMockMvc
class JwtSecurityTest {

    static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b0");

    static class FakePermissions implements PermissionProvider {
        final Map<UUID, Set<String>> byUser = new ConcurrentHashMap<>();
        volatile RuntimeException failWith;

        @Override
        public Set<String> permissionsOf(UUID userId) {
            if (failWith != null) {
                throw failWith;
            }
            return byUser.getOrDefault(userId, Set.of());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({Controllers.class, ReportController.class})
    static class App {
        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey(Tokens.publicKey()).build();
        }

        @Bean
        FakePermissions permissions() {
            return new FakePermissions();
        }
    }

    @RestController
    static class Controllers {
        @GetMapping("/orders")
        @RequirePermission("order:read")
        Map<String, String> read() {
            return Map.of("user", ContextHolder.userId().orElse("-"));
        }

        @PostMapping("/orders/cancel")
        @RequirePermission("order:cancel")
        Map<String, String> cancel() {
            return Map.of("ok", "true");
        }

        @GetMapping("/open")
        Map<String, String> open() {
            return Map.of("user", ContextHolder.userId().orElse("-"));
        }

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }
    }

    @RestController
    @RequirePermission("report:view")
    static class ReportController {
        @GetMapping("/reports/a")
        Map<String, String> a() {
            return Map.of("r", "a");
        }

        @GetMapping("/reports/b")
        @RequirePermission("report:export")
        Map<String, String> b() {
            return Map.of("r", "b");
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FakePermissions permissions;

    @BeforeEach
    void reset() {
        permissions.byUser.clear();
        permissions.failWith = null;
    }

    private static String bearer(UUID user) {
        return "Bearer " + Tokens.valid(user.toString());
    }

    // ---------------------------------------------------------------- xác thực

    @Test
    void noTokenIs401WithBaseResponseBody() throws Exception {
        mvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("COMMON-4010"))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void invalidTokensAreAll401WithoutDetails() throws Exception {
        String expired = Tokens.sign(Tokens.KEYS, ALICE.toString(), Instant.now().minusSeconds(60));
        String forged = Tokens.sign(Tokens.OTHER_KEYS, ALICE.toString(), Instant.now().plusSeconds(300));
        for (String token : new String[]{expired, forged, "not.a.jwt", ""}) {
            String body = mvc.perform(get("/open").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON-4010"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("expired").doesNotContain("signature").doesNotContain("Jwt");
        }
    }

    @Test
    void validTokenReachesEndpointWithoutPermissionAnnotationAndUserContextIsSet() throws Exception {
        mvc.perform(get("/open").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(ALICE.toString()));
    }

    @Test
    void publicPathNeedsNoToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------- quyền

    @Test
    void grantedPermissionAllowsAndMissingOneIs403() throws Exception {
        permissions.byUser.put(ALICE, Set.of("order:read"));
        mvc.perform(get("/orders").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(ALICE.toString()));
        mvc.perform(post("/orders/cancel").header("Authorization", bearer(ALICE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON-4030"));
    }

    @Test
    void userWithNoPermissionIsForbidden() throws Exception {
        mvc.perform(get("/orders").header("Authorization", bearer(BOB))).andExpect(status().isForbidden());
    }

    @Test
    void wildcardsCover() throws Exception {
        permissions.byUser.put(ALICE, Set.of("order:*"));
        permissions.byUser.put(BOB, Set.of("*"));
        mvc.perform(get("/orders").header("Authorization", bearer(ALICE))).andExpect(status().isOk());
        mvc.perform(post("/orders/cancel").header("Authorization", bearer(ALICE))).andExpect(status().isOk());
        mvc.perform(get("/reports/a").header("Authorization", bearer(ALICE))).andExpect(status().isForbidden());
        mvc.perform(get("/reports/a").header("Authorization", bearer(BOB))).andExpect(status().isOk());
    }

    @Test
    void methodAnnotationOverridesClassAnnotation() throws Exception {
        permissions.byUser.put(ALICE, Set.of("report:view"));
        mvc.perform(get("/reports/a").header("Authorization", bearer(ALICE))).andExpect(status().isOk());
        mvc.perform(get("/reports/b").header("Authorization", bearer(ALICE))).andExpect(status().isForbidden());
        permissions.byUser.put(BOB, Set.of("report:export"));
        mvc.perform(get("/reports/b").header("Authorization", bearer(BOB))).andExpect(status().isOk());
        mvc.perform(get("/reports/a").header("Authorization", bearer(BOB))).andExpect(status().isForbidden());
    }

    @Test
    void subjectThatIsNotAUuidIsDeniedOnProtectedEndpointButFineOnOpenOne() throws Exception {
        String token = "Bearer " + Tokens.valid("service-account-42");
        mvc.perform(get("/orders").header("Authorization", token)).andExpect(status().isForbidden());
        mvc.perform(get("/open").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void failsClosedWhenPermissionsCannotBeDetermined() {
        permissions.byUser.put(ALICE, Set.of("order:read"));
        permissions.failWith = new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "redis down");
        // không có GlobalExceptionHandler trong test này nên lỗi lan ra; điều quan trọng là KHÔNG trả 200
        assertThatThrownBy(() -> mvc.perform(get("/orders").header("Authorization", bearer(ALICE))))
                .hasRootCauseInstanceOf(BusinessException.class)
                .rootCause().isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void contextIsCleanedAfterRequests() throws Exception {
        mvc.perform(get("/open").header("Authorization", bearer(ALICE))).andExpect(status().isOk());
        assertThat(ContextHolder.current()).isEmpty();
    }
}
