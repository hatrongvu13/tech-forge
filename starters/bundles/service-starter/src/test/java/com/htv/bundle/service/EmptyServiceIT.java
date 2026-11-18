package com.htv.bundle.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chứng minh "một dependency": service chỉ có {@code service-starter} và một lớp {@code @SpringBootApplication},
 * không tự khai báo filter, xử lý lỗi, bảo mật hay tracing. Chạy trên server HTTP thật.
 */
@SpringBootTest(classes = EmptyServiceIT.EmptyApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.application.name=empty-service", "management.server.port=0"})
class EmptyServiceIT {

    @SpringBootApplication
    static class EmptyApp {
    }

    /** Một endpoint nghiệp vụ bình thường của service, không có cấu hình gì thêm. */
    @RestController
    static class Hello {
        @GetMapping("/hello")
        String hello() {
            return "hi";
        }
    }

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        return getOn(port, path, headers);
    }

    private HttpResponse<String> getOn(int onPort, String path, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + onPort + path));
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void actuatorRunsOnTheManagementPortNotOnTheApiPort() throws Exception {
        assertThat(managementPort).isNotEqualTo(port);
        assertThat(get("/actuator/health").statusCode()).isIn(401, 404);
        assertThat(get("/actuator/prometheus").statusCode()).isIn(401, 404);
    }

    @Test
    void kubernetesProbesAreUpWithoutLoginAndWithoutDetails() throws Exception {
        for (String probe : new String[] {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            HttpResponse<String> r = getOn(managementPort, probe);
            assertThat(r.statusCode()).as(probe).isEqualTo(200);
            assertThat(r.body()).as(probe).contains("\"UP\"").doesNotContain("components").doesNotContain("details");
        }
    }

    @Test
    void prometheusIsScrapableOnTheManagementPort() throws Exception {
        HttpResponse<String> r = getOn(managementPort, "/actuator/prometheus");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("jvm_memory_used_bytes").contains("application=\"empty-service\"");
    }

    @Test
    void onlyWhitelistedEndpointsAreExposed() throws Exception {
        assertThat(getOn(managementPort, "/actuator/env").statusCode()).isEqualTo(404);
        assertThat(getOn(managementPort, "/actuator/beans").statusCode()).isEqualTo(404);
    }

    @Test
    void endpointsAreClosedByDefaultWithBaseResponseAndTraceId() throws Exception {
        HttpResponse<String> r = get("/hello");
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.body()).contains("\"code\":\"COMMON-4010\"");
        String traceId = r.headers().firstValue("X-Trace-Id").orElseThrow();
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(r.body()).contains("\"traceId\":\"" + traceId + "\"");
    }

    @Test
    void w3cTraceparentFromTheCallerIsContinued() throws Exception {
        // tracing-starter đang chạy: trace id lấy từ span (traceparent), không lấy từ X-Trace-Id do client tự đặt
        HttpResponse<String> r = get("/hello",
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "X-Trace-Id", "client-chosen");
        assertThat(r.headers().firstValue("X-Trace-Id")).contains("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void openApiIsServedOutsideProd() throws Exception {
        HttpResponse<String> r = get("/v3/api-docs");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"openapi\"");
    }
}
