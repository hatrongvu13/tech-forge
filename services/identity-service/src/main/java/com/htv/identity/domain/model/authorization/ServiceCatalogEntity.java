package com.htv.identity.domain.model.authorization;

import com.htv.starter.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Mỗi service một dòng: version đã đăng ký danh mục quyền gần nhất. Dòng này cũng là khóa giao dịch
 * khi nhiều instance đăng ký cùng lúc (xem thiết kế mục 3.5).
 */
@Entity
@Table(name = "iam_service_catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceCatalogEntity extends AuditableEntity {

    @Column(name = "service_name", nullable = false, updatable = false, length = 100)
    private String serviceName;

    @Column(name = "service_version", nullable = false, length = 100)
    private String serviceVersion;

    @Column(name = "instance_id", length = 200)
    private String instanceId;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    public static ServiceCatalogEntity register(String serviceName, String serviceVersion, String instanceId,
                                                Instant at) {
        if (serviceName == null || serviceName.isBlank() || serviceVersion == null || serviceVersion.isBlank()
                || at == null) {
            throw new IllegalArgumentException("serviceName, serviceVersion and registeredAt are required");
        }
        ServiceCatalogEntity c = new ServiceCatalogEntity();
        c.serviceName = serviceName;
        c.serviceVersion = serviceVersion;
        c.instanceId = instanceId;
        c.registeredAt = at;
        return c;
    }

    public void update(String serviceVersion, String instanceId, Instant at) {
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
        this.registeredAt = at;
    }
}
