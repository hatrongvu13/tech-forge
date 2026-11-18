package com.htv.identity.domain.model.authorization;

import com.htv.starter.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Gán role cho người dùng, có thể giới hạn thời gian hiệu lực ({@code effectiveTo} rỗng là vô thời hạn). */
@Entity
@Table(name = "iam_user_role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRoleAssignmentEntity extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "assigned_by", updatable = false)
    private UUID assignedBy;

    public static UserRoleAssignmentEntity assign(UUID userId, UUID roleId, Instant from, Instant to, UUID assignedBy) {
        if (userId == null || roleId == null || from == null) {
            throw new IllegalArgumentException("userId, roleId and effectiveFrom are required");
        }
        if (to != null && !to.isAfter(from)) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
        UserRoleAssignmentEntity a = new UserRoleAssignmentEntity();
        a.userId = userId;
        a.roleId = roleId;
        a.effectiveFrom = from;
        a.effectiveTo = to;
        a.assignedBy = assignedBy;
        return a;
    }

    public boolean isEffective(Instant now) {
        return !now.isBefore(effectiveFrom) && (effectiveTo == null || now.isBefore(effectiveTo));
    }

    public void endAt(Instant at) {
        if (!at.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("end must be after effectiveFrom");
        }
        this.effectiveTo = at;
    }
}
