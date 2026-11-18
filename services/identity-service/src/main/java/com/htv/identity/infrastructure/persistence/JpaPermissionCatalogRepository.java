package com.htv.identity.infrastructure.persistence;

import com.htv.identity.domain.model.authorization.PermissionEntity;
import com.htv.identity.domain.model.authorization.ServiceCatalogEntity;
import com.htv.identity.domain.repository.PermissionCatalogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaPermissionCatalogRepository implements PermissionCatalogRepository {

    /** Phiên bản khởi tạo của dòng mới: nhỏ hơn mọi version thật nên lần đăng ký đầu luôn được coi là mới nhất. */
    static final String INITIAL_VERSION = "0";

    @PersistenceContext
    private EntityManager em;

    @Override
    public ServiceCatalogEntity lockOrCreate(String serviceName, Instant now) {
        // INSERT ... ON CONFLICT DO NOTHING: hai instance đăng ký lần đầu cùng lúc không làm nhau lỗi.
        em.createNativeQuery("""
                INSERT INTO iam_service_catalog
                    (id, version, created_at, updated_at, service_name, service_version, registered_at)
                VALUES (gen_random_uuid(), 0, :now, :now, :name, :version, :now)
                ON CONFLICT (service_name) DO NOTHING
                """).setParameter("now", now).setParameter("name", serviceName)
                .setParameter("version", INITIAL_VERSION).executeUpdate();
        return em.createQuery("select c from ServiceCatalogEntity c where c.serviceName = :name",
                        ServiceCatalogEntity.class)
                .setParameter("name", serviceName).setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult();
    }

    @Override
    public Optional<PermissionEntity> findByCode(String code) {
        return em.createQuery("select p from PermissionEntity p where p.code = :code", PermissionEntity.class)
                .setParameter("code", code).getResultStream().findFirst();
    }

    @Override
    public List<PermissionEntity> findActiveByService(String serviceName) {
        return em.createQuery("select p from PermissionEntity p where p.serviceName = :s and p.active = true",
                PermissionEntity.class).setParameter("s", serviceName).getResultList();
    }

    @Override
    public PermissionEntity save(PermissionEntity permission) {
        em.persist(permission);
        em.flush(); // lỗi unique (hai service tranh cùng một mã) hiện ra ngay
        return permission;
    }
}
