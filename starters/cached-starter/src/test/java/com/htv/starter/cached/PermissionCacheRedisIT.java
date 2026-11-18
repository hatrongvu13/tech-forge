package com.htv.starter.cached;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.shared.auth.PermissionCacheKeys;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** Chạy trên Redis thật (Docker). */
class PermissionCacheRedisIT {

    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);
    static LettuceConnectionFactory factory;
    static StringRedisTemplate template;

    @BeforeAll
    static void start() {
        redis.start();
        factory = connect(redis.getHost(), redis.getMappedPort(6379));
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void stop() {
        factory.destroy();
        redis.stop();
    }

    private static LettuceConnectionFactory connect(String host, int port) {
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2)).build();
        LettuceConnectionFactory f = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), client);
        f.afterPropertiesSet();
        return f;
    }

    private final PermissionCacheStore store = new PermissionCacheStore(template, Duration.ofMinutes(5));

    private static RedisPermissionProvider provider(AtomicInteger delegateCalls, Set<String> delegateResult) {
        return new RedisPermissionProvider(template, id -> {
            delegateCalls.incrementAndGet();
            return delegateResult;
        });
    }

    @Test
    void hitReturnsCachedSetWithoutCallingIdentity() {
        UUID user = UUID.randomUUID();
        store.put(user, Set.of("order:read", "billing:*"));
        AtomicInteger calls = new AtomicInteger();
        assertThat(provider(calls, Set.of("SHOULD-NOT-BE-USED")).permissionsOf(user))
                .containsExactlyInAnyOrder("order:read", "billing:*");
        assertThat(calls).hasValue(0);
    }

    @Test
    void emptyPermissionsAreCachedAsNoneMarkerAndReadBackAsEmpty() {
        UUID user = UUID.randomUUID();
        store.put(user, Set.of());
        assertThat(template.opsForSet().members(PermissionCacheKeys.userKey(user)))
                .containsExactly(PermissionCacheKeys.NONE);
        AtomicInteger calls = new AtomicInteger();
        assertThat(provider(calls, Set.of("x:y")).permissionsOf(user)).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void missAsksIdentity() {
        AtomicInteger calls = new AtomicInteger();
        assertThat(provider(calls, Set.of("from:identity")).permissionsOf(UUID.randomUUID()))
                .containsExactly("from:identity");
        assertThat(calls).hasValue(1);
    }

    @Test
    void putReplacesPreviousMembersAndSetsTtl() {
        UUID user = UUID.randomUUID();
        store.put(user, Set.of("a:one", "a:two"));
        store.put(user, Set.of("b:only"));
        String key = PermissionCacheKeys.userKey(user);
        assertThat(template.opsForSet().members(key)).containsExactly("b:only");
        Long ttl = template.getExpire(key);
        assertThat(ttl).isBetween(250L, 300L);
    }

    @Test
    void evictRemovesTheKeyAndTheNextReadMisses() {
        UUID user = UUID.randomUUID();
        store.put(user, Set.of("a:b"));
        store.evict(user);
        assertThat(template.hasKey(PermissionCacheKeys.userKey(user))).isFalse();
        AtomicInteger calls = new AtomicInteger();
        provider(calls, Set.of()).permissionsOf(user);
        assertThat(calls).hasValue(1);
    }

    @Test
    void evictAllHandlesMoreKeysThanOneBatch() {
        Set<UUID> users = new HashSet<>();
        for (int i = 0; i < 1100; i++) {
            UUID u = UUID.randomUUID();
            users.add(u);
            store.put(u, Set.of("a:b"));
        }
        store.evictAll(users);
        assertThat(users).noneMatch(u -> Boolean.TRUE.equals(template.hasKey(PermissionCacheKeys.userKey(u))));
        store.evictAll(List.of()); // không lỗi khi rỗng
    }

    @Test
    void failsClosedWhenRedisIsDown() {
        LettuceConnectionFactory dead = connect("127.0.0.1", 1);
        try {
            StringRedisTemplate broken = new StringRedisTemplate(dead);
            broken.afterPropertiesSet();
            AtomicInteger calls = new AtomicInteger();
            RedisPermissionProvider p = new RedisPermissionProvider(broken, id -> {
                calls.incrementAndGet();
                return Set.of("must:not-be-used");
            });
            assertThatThrownBy(() -> p.permissionsOf(UUID.randomUUID())).isInstanceOfSatisfying(
                    BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
            assertThat(calls).hasValue(0); // Redis lỗi thì không hỏi identity: đúng thiết kế fail-closed
        } finally {
            dead.destroy();
        }
    }

    @Test
    void missWithoutDelegateFailsClosed() {
        RedisPermissionProvider p = new RedisPermissionProvider(template, null);
        assertThatThrownBy(() -> p.permissionsOf(UUID.randomUUID())).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
    }
}
