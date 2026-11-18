package com.htv.identity.domain.repository;

import com.htv.identity.domain.model.authorization.UserRoleAssignmentEntity;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleAssignmentRepository {

    UserRoleAssignmentEntity save(UserRoleAssignmentEntity assignment);

    Optional<UserRoleAssignmentEntity> findById(UUID id);
}
