package com.htv.identity.application.usecase;

import com.htv.identity.application.VersionComparator;
import com.htv.identity.domain.IdentityErrorCode;
import com.htv.identity.domain.event.PermissionCatalogChanged;
import com.htv.identity.domain.model.audit.AuditLogEntity;
import com.htv.identity.domain.model.authorization.PermissionEntity;
import com.htv.identity.domain.model.authorization.ServiceCatalogEntity;
import com.htv.identity.domain.repository.AuditLogRepository;
import com.htv.identity.domain.repository.PermissionCatalogRepository;
import com.htv.shared.auth.PermissionCodes;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.starter.core.ContextValues;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhận danh mục quyền do một service gửi lên lúc khởi động (thiết kế mục 3.5). Idempotent.
 *
 * <ul>
 *   <li>Upsert theo mã trong MỘT giao dịch, khóa dòng {@code iam_service_catalog} của service.
 *   <li>Mã đã thuộc service khác: từ chối riêng mã đó ({@code OWNED_BY_OTHER}), các mã khác vẫn xử lý.
 *   <li>Mã của service không còn trong danh sách: {@code active=false}, KHÔNG xóa (giữ {@code iam_role_permission}).
 *   <li>Không tắt quyền nếu version gửi lên CŨ HƠN version đã ghi (rolling deploy, rollback). Mã có trong
 *       danh sách vẫn được mở lại.
 *   <li>Quyền mới không tự gán cho role nào.
 * </ul>
 */
@Service
public class RegisterPermissionCatalogUseCase {

    public static final String REJECT_OWNED_BY_OTHER = "OWNED_BY_OTHER";
    public static final String REJECT_INVALID_FORMAT = "INVALID_FORMAT";
    static final String AUDIT_ACTION = "PERMISSION_CATALOG_REGISTERED";

    public record Definition(String code, String description) {
    }

    public record Command(String serviceName, String serviceVersion, String instanceId, List<Definition> permissions) {
    }

    public record Rejected(String code, String reason) {
    }

    public record Result(int created, int updated, int unchanged, int deactivated, List<Rejected> rejected) {
    }

    private final PermissionCatalogRepository catalog;
    private final AuditLogRepository audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RegisterPermissionCatalogUseCase(PermissionCatalogRepository catalog, AuditLogRepository audit,
                                            ApplicationEventPublisher events, Clock clock) {
        this.catalog = catalog;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Result execute(Command command) {
        String service = ContextValues.safe(command.serviceName());
        if (service == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "service_name is missing or invalid");
        }
        String version = command.serviceVersion() == null || command.serviceVersion().isBlank()
                ? "unknown" : command.serviceVersion().trim();

        ServiceCatalogEntity registry = catalog.lockOrCreate(service, clock.instant());
        boolean olderThanRecorded = VersionComparator.isOlder(version, registry.getServiceVersion());

        Map<String, String> requested = new LinkedHashMap<>();
        List<Rejected> rejected = new ArrayList<>();
        for (Definition d : command.permissions()) {
            if (!PermissionCodes.isValidDeclared(d.code())) {
                rejected.add(new Rejected(String.valueOf(d.code()), REJECT_INVALID_FORMAT));
            } else {
                requested.merge(d.code(), d.description() == null ? "" : d.description(),
                        (a, b) -> a.isEmpty() ? b : a);
            }
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        Set<UUID> stateChanged = new HashSet<>();
        for (Map.Entry<String, String> e : requested.entrySet()) {
            String code = e.getKey();
            String description = e.getValue();
            PermissionEntity existing = catalog.findByCode(code).orElse(null);
            if (existing == null) {
                catalog.save(PermissionEntity.declare(code, blankToNull(description), service));
                created++;
            } else if (existing.getServiceName() != null && !existing.getServiceName().equals(service)) {
                rejected.add(new Rejected(code, REJECT_OWNED_BY_OTHER));
            } else {
                boolean changed = false;
                if (existing.getServiceName() == null) {
                    existing.claimBy(service);
                    changed = true;
                }
                if (!existing.isActive()) {
                    existing.activate(description);
                    stateChanged.add(existing.getId());
                    changed = true;
                } else if (!description.isEmpty() && !description.equals(existing.getDescription())) {
                    existing.describe(description);
                    changed = true;
                }
                if (changed) {
                    updated++;
                } else {
                    unchanged++;
                }
            }
        }

        int deactivated = 0;
        if (!olderThanRecorded) {
            for (PermissionEntity p : catalog.findActiveByService(service)) {
                if (!requested.containsKey(p.getCode())) {
                    p.deactivate();
                    stateChanged.add(p.getId());
                    deactivated++;
                }
            }
            registry.update(version, command.instanceId(), clock.instant());
        }

        if (created + updated + deactivated > 0) {
            audit.save(AuditLogEntity.of(null, AUDIT_ACTION, "service", service, null));
        }
        if (!stateChanged.isEmpty()) {
            events.publishEvent(new PermissionCatalogChanged(stateChanged));
        }
        return new Result(created, updated, unchanged, deactivated, List.copyOf(rejected));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
