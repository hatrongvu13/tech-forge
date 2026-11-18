package com.htv.starter.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

/**
 * Gốc chung có khóa chính UUID v7 (tăng dần theo thời gian, index thân thiện).
 * Dùng qua {@link BaseEntity} (bảng thường) hoặc {@link AppendOnlyEntity} (bảng chỉ thêm).
 *
 * <p>equals/hashCode dựa trên {@code id}; entity chưa lưu (id == null) chỉ bằng chính nó.
 */
@Getter
@MappedSuperclass
public abstract class IdentifiedEntity implements Serializable {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

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
        IdentifiedEntity other = (IdentifiedEntity) o;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public final int hashCode() {
        // Hằng số theo lớp: giữ hashCode ổn định trước và sau khi persist (an toàn trong HashSet).
        return Hibernate.getClass(this).hashCode();
    }
}
