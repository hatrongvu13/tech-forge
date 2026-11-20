package com.htv.starter.cached;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.htv.shared.auth.PermissionCacheKeys;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Logic đọc/ghi cache quyền, Redis được giả lập (không cần container). */
class PermissionCacheLogicTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final String KEY = PermissionCacheKeys.userKey(USER);

    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> sets = mock(SetOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    PermissionCacheLogicTest() {
        when(redis.opsForSet()).thenReturn(sets);
    }

    private static PermissionProvider counting(AtomicInteger calls, Set<String> result) {
        return id -> {
            calls.incrementAndGet();
            return result;
        };
    }

    // ------------------------------------------------------------ RedisPermissionProvider

    @Test
    void hitReturnsCachedPermissionsWithoutAskingIdentity() {
        when(sets.members(KEY)).thenReturn(Set.of("order:read", "billing:*"));
        AtomicInteger calls = new AtomicInteger();
        assertThat(new RedisPermissionProvider(redis, counting(calls, Set.of("x:y"))).permissionsOf(USER))
                .containsExactlyInAnyOrder("order:read", "billing:*");
        assertThat(calls).hasValue(0);
    }

    @Test
    void noneMarkerMeansNoPermissionsAndIsNotAMiss() {
        when(sets.members(KEY)).thenReturn(Set.of(PermissionCacheKeys.NONE));
        AtomicInteger calls = new AtomicInteger();
        assertThat(new RedisPermissionProvider(redis, counting(calls, Set.of("x:y"))).permissionsOf(USER)).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void missAsksIdentity() {
        when(sets.members(KEY)).thenReturn(Set.of());
        AtomicInteger calls = new AtomicInteger();
        assertThat(new RedisPermissionProvider(redis, counting(calls, Set.of("from:identity"))).permissionsOf(USER))
                .containsExactly("from:identity");
        assertThat(calls).hasValue(1);

        when(sets.members(KEY)).thenReturn(null);
        new RedisPermissionProvider(redis, counting(calls, Set.of())).permissionsOf(USER);
        assertThat(calls).hasValue(2);
    }

    @Test
    void missWithoutIdentityFailsClosed() {
        when(sets.members(KEY)).thenReturn(Set.of());
        assertThatThrownBy(() -> new RedisPermissionProvider(redis, null).permissionsOf(USER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void redisErrorFailsClosedAndDoesNotFallBackToIdentity() {
        when(sets.members(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> new RedisPermissionProvider(redis, counting(calls, Set.of("x:y"))).permissionsOf(USER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
        assertThat(calls).hasValue(0);
    }

    // ------------------------------------------------------------ PermissionCacheStore

    @Test
    @SuppressWarnings("unchecked")
    void putSendsTtlAndPermissionsInOneScript() {
        new PermissionCacheStore(redis, Duration.ofMinutes(10)).put(USER, Set.of("a:b"));
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), args.capture());
        assertThat(args.getValue()).containsExactly("600", "a:b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void putOfEmptySetStoresNoneMarkerAndTtlIsAtLeastOneSecond() {
        new PermissionCacheStore(redis, Duration.ofMillis(10)).put(USER, Set.of());
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), args.capture());
        assertThat(args.getValue()).containsExactly("1", PermissionCacheKeys.NONE);
    }

    @Test
    void evictDeletesTheUserKey() {
        new PermissionCacheStore(redis, Duration.ofMinutes(1)).evict(USER);
        verify(redis).delete(KEY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void evictAllDeletesInBatchesOf500() {
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < 1100; i++) {
            users.add(UUID.randomUUID());
        }
        List<Integer> batchSizes = new ArrayList<>();
        when(redis.delete(anyList())).thenAnswer(inv -> {
            batchSizes.add(((Collection<String>) inv.getArgument(0)).size());
            return 0L;
        });
        new PermissionCacheStore(redis, Duration.ofMinutes(1)).evictAll(users);
        assertThat(batchSizes).containsExactly(500, 500, 100);

        new PermissionCacheStore(redis, Duration.ofMinutes(1)).evictAll(List.of());
        verify(redis, times(3)).delete(anyList());
    }
}
