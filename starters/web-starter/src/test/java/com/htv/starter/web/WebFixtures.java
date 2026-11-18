package com.htv.starter.web;

import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller dùng chung cho test của web-starter. */
final class WebFixtures {

    private WebFixtures() {
    }

    record Body(@NotBlank(message = "must not be blank") String name) {
    }

    @RestController
    @RequestMapping("/t")
    static class TestController {

        @GetMapping("/ctx")
        Map<String, String> ctx() {
            return Map.of(
                    "traceId", ContextHolder.traceId().orElse("-"),
                    "userId", ContextHolder.userId().orElse("-"),
                    "channel", ContextHolder.current().map(c -> String.valueOf(c.getChannel())).orElse("-"));
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "order 42 missing");
        }

        @GetMapping("/business5xx")
        void business5xx() {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "redis down");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("jdbc:postgresql://secret-host/db password=hunter2");
        }

        @GetMapping("/denied")
        void denied() {
            throw new org.springframework.security.access.AccessDeniedException("nope");
        }

        @GetMapping("/unauth")
        void unauth() {
            throw new org.springframework.security.authentication.BadCredentialsException("bad");
        }

        @PostMapping("/validate")
        Map<String, String> validate(@Valid @RequestBody Body body) {
            return Map.of("name", body.name());
        }
    }
}
