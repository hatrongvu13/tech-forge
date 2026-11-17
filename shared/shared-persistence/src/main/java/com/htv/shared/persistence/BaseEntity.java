package com.htv.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

/**
 * Gốc của mọi entity: khóa chính UUID v7 (tăng dần theo thời gian, index thân thiện) và
 * optimistic lock.
 *
 * <p>Cột SQL tương ứng:
 * <pre>
 * id      uuid   PRIMARY KEY,
 * version bigint NOT NULL DEFAULT 0
 * </pre>
 *
 * <p>equals/hashCode dựa trên {@code id}; entity chưa lưu (id == null) chỉ bằng chính nó.
 */
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public boolean isNew() {
        return id == null;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public final int hashCode() {
        // Hằng số theo lớp: giữ hashCode ổn định trước và sau khi persist (an toàn trong HashSet).
        return Hibernate.getClass(this).hashCode();
    }
}
