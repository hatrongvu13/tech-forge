package com.htv.starter.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    static class Foo extends AuditableEntity {
    }

    static class Log extends AppendOnlyEntity {
    }

    private static Foo withId(UUID id) throws Exception {
        Foo f = new Foo();
        Field field = IdentifiedEntity.class.getDeclaredField("id");
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

    @Test
    void appendOnlyEntityHasNoAuditUpdateFields() throws Exception {
        Log log = new Log();
        assertThat(log.isNew()).isTrue();
        assertThat(log.getCreatedAt()).isNull();
        assertThat(log).isEqualTo(log).isNotEqualTo(new Log());
        assertThat(Log.class.getSuperclass().getSuperclass()).isEqualTo(IdentifiedEntity.class);
    }
}
