package com.htv.starter.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * Gốc của mọi entity có thể sửa: khóa chính UUID v7 và optimistic lock.
 *
 * <p>Cột SQL tương ứng:
 * <pre>
 * id      uuid   PRIMARY KEY,
 * version bigint NOT NULL DEFAULT 0
 * </pre>
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends IdentifiedEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;

}
