package com.htv.bundle.full;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.shared.auth.PermissionProvider;
import com.htv.starter.cached.PermissionCacheStore;
import com.htv.starter.cached.RedisPermissionProvider;
import com.htv.starter.feign.ContextRequestInterceptor;
import com.htv.starter.grpc.GrpcChannelFactory;
import com.htv.starter.web.GlobalExceptionHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Chứng minh "một dependency" cho nhóm đầy đủ: service chỉ có {@code service-starter-jpa-cache-grpc-feign} và
 * {@code @SpringBootApplication}. Kết nối PostgreSQL và Redis thật; chỉ khai báo địa chỉ, không khai báo cấu hình.
 */
@SpringBootTest(classes = EmptyFullServiceIT.EmptyApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.application.name=empty-full-service", "htv.grpc.server.port=0", "management.server.port=0"})
class EmptyFullServiceIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            REDIS.stop();
            POSTGRES.stop();
        }));
    }

    @DynamicPropertySource
    static void addresses(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @SpringBootApplication
    static class EmptyApp {
    }

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Autowired
    ApplicationContext ctx;

    @Test
    void everyStarterIsWiredWithoutAnyConfigurationClass() {
        assertThat(ctx.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);   // web
        assertThat(ctx.getBeansOfType(SecurityFilterChain.class)).hasSize(1);      // security
        assertThat(ctx.getBeansOfType(AuditorAware.class)).hasSize(1);             // jpa
        assertThat(ctx.getBeansOfType(PermissionCacheStore.class)).hasSize(1);     // cache
        assertThat(ctx.getBean(PermissionProvider.class)).isInstanceOf(RedisPermissionProvider.class);
        assertThat(ctx.getBeansOfType(GrpcChannelFactory.class)).hasSize(1);       // grpc
        assertThat(ctx.getBeansOfType(ContextRequestInterceptor.class)).hasSize(1); // feign
    }

    @Test
    void readinessIsUpWithDatabaseAndRedisConnected() throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + "/actuator/health/readiness")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"UP\"");
    }
}
