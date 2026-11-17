package com.htv.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    static class Foo extends AuditableEntity {
    }

    private static Foo withId(UUID id) throws Exception {
        Foo f = new Foo();
        Field field = BaseEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(f, id);
        return f;
    }

    @Test
    void newEntitiesAreOnlyEqualToThemselves() {
        Foo a = new Foo();
        assertThat(a.isNew()).isTrue();
        assertThat(a).isEqualTo(a).isNotEqualTo(new Foo());
    }

    @Test
    void entitiesWithSameIdAreEqual() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(withId(id)).isEqualTo(withId(id)).hasSameHashCodeAs(withId(id));
        assertThat(withId(id)).isNotEqualTo(withId(UUID.randomUUID()));
    }

    @Test
    void auditFieldsStartEmpty() {
        Foo f = new Foo();
        assertThat(f.getVersion()).isZero();
        assertThat(f.getCreatedAt()).isNull();
        assertThat(f.getUpdatedAt()).isNull();
        assertThat(f.getCreatedBy()).isNull();
        assertThat(f.getUpdatedBy()).isNull();
    }
}
