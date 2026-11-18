package com.htv.identity.application.usecase;

import com.htv.identity.domain.IdentityErrorCode;
import com.htv.identity.domain.event.UserRolesChanged;
import com.htv.identity.domain.model.authorization.UserRoleAssignmentEntity;
import com.htv.identity.domain.repository.IamReferenceRepository;
import com.htv.identity.domain.repository.UserRoleAssignmentRepository;
import com.htv.shared.common.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gán role cho người dùng hoặc kết thúc một lần gán. Sau COMMIT, cache quyền của người dùng bị xóa. */
@Service
public class ChangeUserRolesUseCase {

    private final UserRoleAssignmentRepository assignments;
    private final IamReferenceRepository references;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ChangeUserRolesUseCase(UserRoleAssignmentRepository assignments, IamReferenceRepository references,
                                  ApplicationEventPublisher events, Clock clock) {
        this.assignments = assignments;
        this.references = references;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @param from       thời điểm bắt đầu hiệu lực; null = ngay bây giờ
     * @param to         thời điểm hết hiệu lực; null = vô thời hạn
     * @param assignedBy người thực hiện (có thể null)
     * @return id của lần gán
     */
    @Transactional
    public UUID assign(UUID userId, UUID roleId, Instant from, Instant to, UUID assignedBy) {
        if (!references.userExists(userId)) {
            throw new BusinessException(IdentityErrorCode.USER_NOT_FOUND);
        }
        if (!references.roleExists(roleId)) {
            throw new BusinessException(IdentityErrorCode.ROLE_NOT_FOUND);
        }
        Instant start = from != null ? from : clock.instant();
        UserRoleAssignmentEntity entity;
        try {
            entity = UserRoleAssignmentEntity.assign(userId, roleId, start, to, assignedBy);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(IdentityErrorCode.INVALID_ASSIGNMENT_PERIOD, e.getMessage(), e);
        }
        UUID id = assignments.save(entity).getId();
        events.publishEvent(new UserRolesChanged(userId));
        return id;
    }

    /** Kết thúc hiệu lực của lần gán tại thời điểm {@code at} (null = ngay bây giờ). */
    @Transactional
    public void end(UUID assignmentId, Instant at) {
        UserRoleAssignmentEntity a = assignments.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ASSIGNMENT_NOT_FOUND));
        try {
            a.endAt(at != null ? at : clock.instant());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(IdentityErrorCode.INVALID_ASSIGNMENT_PERIOD, e.getMessage(), e);
        }
        events.publishEvent(new UserRolesChanged(a.getUserId()));
    }
}
