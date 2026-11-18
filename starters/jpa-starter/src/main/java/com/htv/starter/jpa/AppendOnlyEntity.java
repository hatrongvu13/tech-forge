package com.htv.starter.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Gốc cho bảng chỉ thêm (nhật ký kiểm toán): chỉ có id và thời điểm tạo. Không {@code version},
 * không {@code updated_*}, không setter. Dòng đã ghi không được sửa.
 *
 * <p>Cột SQL tương ứng:
 * <pre>
 * id         uuid        PRIMARY KEY,
 * created_at timestamptz NOT NULL
 * </pre>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AppendOnlyEntity extends IdentifiedEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Instant getCreatedAt() {
        return createdAt;
    }
}
