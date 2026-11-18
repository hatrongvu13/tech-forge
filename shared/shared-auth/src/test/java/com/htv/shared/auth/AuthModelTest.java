package com.htv.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.shared.auth.PermissionCatalog.PermissionDef;
import com.htv.shared.auth.PermissionCatalogPublisher.PublishResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthModelTest {

    @Test
    void declaredCodeFormat() {
        assertThat(PermissionCodes.isValidDeclared("order:read")).isTrue();
        assertThat(PermissionCodes.isValidDeclared("order-item:read-all")).isTrue();
        for (String bad : new String[] {null, "", "order", "Order:read", "order:", ":read", "order:*", "*", "order:read:x", "1order:read"}) {
            assertThat(PermissionCodes.isValidDeclared(bad)).as(String.valueOf(bad)).isFalse();
        }
    }

    @Test
    void grantFormatAllowsWildcards() {
        assertThat(PermissionCodes.isValidGrant("*")).isTrue();
        assertThat(PermissionCodes.isValidGrant("order:*")).isTrue();
        assertThat(PermissionCodes.isValidGrant("order:read")).isTrue();
        assertThat(PermissionCodes.isValidGrant("*:read")).isFalse();
        assertThat(PermissionCodes.isValidGrant(null)).isFalse();
    }

    @Test
    void coversMatching() {
        assertThat(PermissionCodes.covers("*", "order:read")).isTrue();
        assertThat(PermissionCodes.covers("order:*", "order:read")).isTrue();
        assertThat(PermissionCodes.covers("order:read", "order:read")).isTrue();
        assertThat(PermissionCodes.covers("order:*", "orders:read")).isFalse();
        assertThat(PermissionCodes.covers("order:*", "billing:read")).isFalse();
        assertThat(PermissionCodes.covers("order:read", "order:write")).isFalse();
        assertThat(PermissionCodes.covers(null, "order:read")).isFalse();
        assertThat(PermissionCodes.covers("order:read", null)).isFalse();
    }

    @Test
    void anyCovers() {
        assertThat(PermissionCodes.anyCovers(Set.of("a:x", "order:*"), "order:read")).isTrue();
        assertThat(PermissionCodes.anyCovers(Set.of("a:x"), "order:read")).isFalse();
        assertThat(PermissionCodes.anyCovers(Set.of(), "order:read")).isFalse();
        assertThat(PermissionCodes.anyCovers(null, "order:read")).isFalse();
        assertThat(PermissionCodes.anyCovers(Set.of(PermissionCacheKeys.NONE), "order:read")).isFalse();
    }

    @Test
    void catalogRejectsBadInput() {
        assertThatThrownBy(() -> new PermissionDef("Bad", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PermissionCatalog(" ", "1", "i", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PermissionCatalog("order", null, "i", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PermissionCatalog("order", "1", "i",
                List.of(new PermissionDef("order:read", ""), new PermissionDef("order:read", "dup"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("order:read");
    }

    @Test
    void catalogIsImmutableAndNullSafe() {
        PermissionCatalog c = new PermissionCatalog("order", "1.0", "i-1", null);
        assertThat(c.permissions()).isEmpty();
        assertThatThrownBy(() -> c.permissions().add(new PermissionDef("order:read", null)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new PermissionDef("order:read", null).description()).isEmpty();
    }

    @Test
    void publishResultDefaultsRejected() {
        assertThat(new PublishResult(1, 0, 0, null).rejected()).isEmpty();
    }

    @Test
    void cacheKey() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertThat(PermissionCacheKeys.userKey(id)).isEqualTo("iam:perm:user:00000000-0000-0000-0000-000000000001");
        assertThat(PermissionCodes.isValidGrant(PermissionCacheKeys.NONE)).isFalse();
    }
}
