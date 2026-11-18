package com.htv.identity.domain.repository;

import com.htv.identity.domain.model.audit.AuditLogEntity;

public interface AuditLogRepository {

    AuditLogEntity save(AuditLogEntity entry);
}
