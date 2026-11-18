package com.htv.identity.infrastructure.persistence;

import com.htv.identity.domain.model.audit.AuditLogEntity;
import com.htv.identity.domain.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
class JpaAuditLogRepository implements AuditLogRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public AuditLogEntity save(AuditLogEntity entry) {
        em.persist(entry);
        return entry;
    }
}
