package com.htv.identity.infrastructure.persistence;

import com.htv.identity.domain.model.authorization.UserRoleAssignmentEntity;
import com.htv.identity.domain.repository.UserRoleAssignmentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserRoleAssignmentRepository implements UserRoleAssignmentRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public UserRoleAssignmentEntity save(UserRoleAssignmentEntity assignment) {
        em.persist(assignment);
        em.flush();
        return assignment;
    }

    @Override
    public Optional<UserRoleAssignmentEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(UserRoleAssignmentEntity.class, id));
    }
}
