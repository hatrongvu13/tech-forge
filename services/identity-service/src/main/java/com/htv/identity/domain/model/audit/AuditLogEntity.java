package com.htv.identity.domain.model.audit;

import com.htv.starter.jpa.AppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Nhật ký kiểm toán. Chỉ thêm, không sửa. Không có khóa ngoại tới {@code iam_user}: nhật ký phải
 * còn nguyên cả khi người dùng bị xóa.
 */
@Entity
@Table(name = "iam_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntity extends AppendOnlyEntity {

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "entity_type", updatable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", updatable = false, length = 100)
    private String entityId;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    public static AuditLogEntity of(UUID userId, String action, String entityType, String entityId, String ipAddress) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        AuditLogEntity l = new AuditLogEntity();
        l.userId = userId;
        l.action = action;
        l.entityType = entityType;
        l.entityId = entityId;
        l.ipAddress = ipAddress;
        return l;
    }
}
