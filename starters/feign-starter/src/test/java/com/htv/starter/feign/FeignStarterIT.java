package com.htv.starter.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Gọi Feign thật tới một web server chạy trong test: truyền ngữ cảnh, chỉ client nội bộ nhận token, dịch lỗi,
 * timeout mặc định, và tự quét {@code @FeignClient} không cần {@code @EnableFeignClients}.
 */
@SpringBootTest(classes = FeignStarterIT.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"htv.feign.trusted-clients=internal", "htv.feign.read-timeout=700ms"})
class FeignStarterIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Downstream.class)
    static class App {
    }

    /** "Service phía sau": ghi lại header nhận được và trả lời theo đường dẫn. */
    @RestController
    static class Downstream {
        static final AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        @GetMapping("/echo")
        Map<String, String> echo(HttpServletRequest r) {
            Map<String, String> h = new java.util.HashMap<>();
            for (String name : new String[] {"X-Trace-Id", "X-Channel", "X-User-Id", "Authorization", "X-Custom"}) {
                if (r.getHeader(name) != null) {
                    h.put(name, r.getHeader(name));
                }
            }
            seen.set(h);
            return Map.of("ok", "true");
        }

        @GetMapping("/not-found")
        ResponseEntity<String> notFound() {
            return ResponseEntity.status(404).header("Content-Type", "application/json")
                    .body("{\"traceId\":\"t\",\"code\":\"IDENTITY-4041\",\"message\":\"User not found\",\"data\":null}");
        }

        @GetMapping("/plain-error")
        ResponseEntity<String> plainError() {
            return ResponseEntity.status(503).body("<html>upstream down, db=secret-host</html>");
        }

        @GetMapping("/forged-code")
        ResponseEntity<String> forged() {
            return ResponseEntity.status(409).header("Content-Type", "application/json")
                    .body("{\"code\":\"evil code\\nX-Injected: 1\",\"message\":\"x\"}");
        }

        @GetMapping("/slow")
        String slow() throws InterruptedException {
            Thread.sleep(2000);
            return "late";
        }
    }

    interface Peer {
        @GetMapping("/echo")
        Map<String, String> echo();

        @GetMapping(value = "/echo", headers = "Authorization=Bearer chosen-by-client")
        Map<String, String> echoWithOwnAuth();

        @GetMapping("/not-found")
        String notFound();

        @GetMapping("/plain-error")
        String plainError();

        @GetMapping("/forged-code")
        String forgedCode();

        @GetMapping("/slow")
        String slow();
    }

    /** Client được khai báo bình thường: phải được quét mà không cần @EnableFeignClients. */
    @FeignClient(name = "scanned", url = "http://localhost:1")
    interface ScannedClient {
        @GetMapping("/x")
        String x();
    }

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    ScannedClient scanned;

    private Peer client(String name) {
        return new FeignClientBuilder(context).forType(Peer.class, name).url("http://localhost:" + port).build();
    }

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
        RequestContextHolder.resetRequestAttributes();
        Downstream.seen.set(null);
    }

    private void incomingRequestWithToken(String token) {
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader("Authorization", token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
    }

    private static ContextHolder.Scope context() {
        return ContextHolder.open(RequestContext.builder().traceId("trace-9").userId("user-9").channel("web").build());
    }

    // ------------------------------------------------------------------- ngữ cảnh

    @Test
    void internalClientGetsTraceChannelUserAndForwardedToken() {
        incomingRequestWithToken("Bearer user-token");
        try (ContextHolder.Scope ignored = context()) {
            client("internal").echo();
        }
        assertThat(Downstream.seen.get()).containsEntry("X-Trace-Id", "trace-9").containsEntry("X-Channel", "web")
                .containsEntry("X-User-Id", "user-9").containsEntry("Authorization", "Bearer user-token");
    }

    @Test
    void externalClientNeverReceivesTheUsersTokenOrId() {
        incomingRequestWithToken("Bearer user-token");
        try (ContextHolder.Scope ignored = context()) {
            client("payment-gateway").echo();
        }
        assertThat(Downstream.seen.get()).containsEntry("X-Trace-Id", "trace-9")
                .doesNotContainKey("Authorization").doesNotContainKey("X-User-Id");
    }

    @Test
    void headerSetByTheClientIsNotOverwritten() {
        incomingRequestWithToken("Bearer user-token");
        client("internal").echoWithOwnAuth();
        assertThat(Downstream.seen.get()).containsEntry("Authorization", "Bearer chosen-by-client");
    }

    @Test
    void nonBearerOrMissingIncomingAuthorizationIsNotForwarded() {
        incomingRequestWithToken("Basic dXNlcjpwYXNz");
        client("internal").echo();
        assertThat(Downstream.seen.get()).doesNotContainKey("Authorization");
        RequestContextHolder.resetRequestAttributes();
        client("internal").echo(); // không có request đang xử lý (ví dụ job nền)
        assertThat(Downstream.seen.get()).isEmpty();
    }

    // ---------------------------------------------------------------------- lỗi

    @Test
    void remoteErrorCodeIsKeptAcrossServices() {
        assertThatThrownBy(() -> client("internal").notFound()).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode().code()).isEqualTo("IDENTITY-4041");
            assertThat(e.getErrorCode().message()).isEqualTo("User not found");
            assertThat(e.getErrorCode().httpStatus()).isEqualTo(404);
            assertThat(e.getErrorCode().grpcStatus()).isEqualTo(5);
        });
    }

    @Test
    void nonJsonErrorFallsBackToCommonCodeWithoutLeakingTheBody() {
        assertThatThrownBy(() -> client("internal").plainError()).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
            assertThat(e.getErrorCode().message()).doesNotContain("secret-host");
        });
    }

    @Test
    void malformedRemoteCodeIsIgnored() {
        assertThatThrownBy(() -> client("internal").forgedCode()).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT));
    }

    @Test
    void defaultReadTimeoutStopsASlowService() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> client("internal").slow()).isInstanceOf(RetryableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1800));
    }

    // -------------------------------------------------------------------- quét

    @Test
    void feignClientsAreScannedWithoutEnableFeignClients() {
        assertThat(scanned).isNotNull();
    }
}
