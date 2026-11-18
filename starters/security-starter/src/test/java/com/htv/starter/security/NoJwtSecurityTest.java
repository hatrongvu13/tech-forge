package com.htv.starter.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chưa cấu hình JWT: an toàn mặc định là từ chối tất cả, trừ đường dẫn công khai.
 */
@SpringBootTest(classes = NoJwtSecurityTest.App.class)
@AutoConfigureMockMvc
class NoJwtSecurityTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Controllers.class)
    static class App {
    }

    @RestController
    static class Controllers {
        @GetMapping("/anything")
        Map<String, String> anything() {
            return Map.of("a", "b");
        }

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    UserDetailsService users;

    @Test
    void everythingExceptPublicPathsIsDenied() throws Exception {
        mvc.perform(get("/anything")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-4010"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void noDefaultUserWithGeneratedPassword() {
        assertThat(users).isInstanceOf(org.springframework.security.provisioning.InMemoryUserDetailsManager.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> users.loadUserByUsername("user"))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }
}
