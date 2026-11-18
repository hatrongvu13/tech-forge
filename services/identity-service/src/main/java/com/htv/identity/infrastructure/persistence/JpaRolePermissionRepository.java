package com.htv.identity.infrastructure.persistence;

import com.htv.identity.domain.model.authorization.RolePermissionEntity;
import com.htv.identity.domain.repository.RolePermissionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaRolePermissionRepository implements RolePermissionRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean exists(UUID roleId, UUID permissionId) {
        return em.createQuery("select count(rp) from RolePermissionEntity rp "
                        + "where rp.roleId = :r and rp.permissionId = :p", Long.class)
                .setParameter("r", roleId).setParameter("p", permissionId).getSingleResult() > 0;
    }

    @Override
    public RolePermissionEntity save(RolePermissionEntity rolePermission) {
        em.persist(rolePermission);
        em.flush(); // lỗi ràng buộc (unique, khóa ngoại) hiện ra ngay trong giao dịch này
        return rolePermission;
    }

    @Override
    public int delete(UUID roleId, UUID permissionId) {
        return em.createQuery("delete from RolePermissionEntity rp where rp.roleId = :r and rp.permissionId = :p")
                .setParameter("r", roleId).setParameter("p", permissionId).executeUpdate();
    }
}
