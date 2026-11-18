package com.htv.identity.infrastructure.persistence;

import com.htv.identity.domain.enums.UserStatus;
import com.htv.identity.domain.repository.PermissionQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaPermissionQueryRepository implements PermissionQueryRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<UserStatus> findUserStatus(UUID userId) {
        List<UserStatus> rows = em.createQuery("select u.status from UserEntity u where u.id = :id", UserStatus.class)
                .setParameter("id", userId).getResultList();
        return rows.stream().findFirst();
    }

    @Override
    public Set<String> findEffectivePermissionCodes(UUID userId, Instant now) {
        List<String> codes = em.createQuery("""
                select distinct p.code
                from UserRoleAssignmentEntity ur, RolePermissionEntity rp, PermissionEntity p
                where ur.userId = :userId
                  and ur.effectiveFrom <= :now
                  and (ur.effectiveTo is null or ur.effectiveTo > :now)
                  and rp.roleId = ur.roleId
                  and p.id = rp.permissionId
                  and p.active = true
                """, String.class).setParameter("userId", userId).setParameter("now", now).getResultList();
        return new HashSet<>(codes);
    }

    @Override
    public Set<UUID> findUserIdsByRoleId(UUID roleId) {
        List<UUID> ids = em.createQuery("select distinct ur.userId from UserRoleAssignmentEntity ur "
                + "where ur.roleId = :roleId", UUID.class).setParameter("roleId", roleId).getResultList();
        return new HashSet<>(ids);
    }

    @Override
    public Set<UUID> findUserIdsByPermissionIds(Collection<UUID> permissionIds) {
        if (permissionIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = em.createQuery("""
                select distinct ur.userId
                from UserRoleAssignmentEntity ur, RolePermissionEntity rp
                where rp.permissionId in :ids and ur.roleId = rp.roleId
                """, UUID.class).setParameter("ids", permissionIds).getResultList();
        return new HashSet<>(ids);
    }
}
