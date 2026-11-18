package com.htv.identity.infrastructure.persistence;

import com.htv.identity.domain.model.authorization.PermissionEntity;
import com.htv.identity.domain.model.authorization.RoleEntity;
import com.htv.identity.domain.model.identity.UserEntity;
import com.htv.identity.domain.repository.IamReferenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaIamReferenceRepository implements IamReferenceRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean userExists(UUID userId) {
        return exists(UserEntity.class, userId);
    }

    @Override
    public boolean roleExists(UUID roleId) {
        return exists(RoleEntity.class, roleId);
    }

    @Override
    public boolean permissionExists(UUID permissionId) {
        return exists(PermissionEntity.class, permissionId);
    }

    private <T> boolean exists(Class<T> type, UUID id) {
        if (id == null) {
            return false;
        }
        return em.createQuery("select count(e) from " + type.getSimpleName() + " e where e.id = :id", Long.class)
                .setParameter("id", id).getSingleResult() > 0;
    }
}
