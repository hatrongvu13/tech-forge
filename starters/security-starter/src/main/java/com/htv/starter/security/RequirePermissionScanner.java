package com.htv.starter.security;

import com.htv.shared.auth.PermissionCatalog.PermissionDef;
import com.htv.shared.auth.PermissionCodes;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.auth.RequirePermission;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Quét mọi bean tìm {@code @RequirePermission} sau khi context khởi tạo xong.
 *
 * <ul>
 *   <li>Mã sai định dạng, hoặc cùng một mã nhưng mô tả khác nhau: lỗi của dev, service <b>không khởi động</b>.
 *   <li>Có dùng {@code @RequirePermission} mà không có {@link PermissionProvider}: không khởi động, thông báo
 *       nêu starter cần thêm.
 *   <li>Cùng một mã dùng ở nhiều method là bình thường; kết quả được gộp theo mã.
 * </ul>
 *
 * <p>Chỉ quét bean Spring (controller, gRPC service, {@code @Service}). Annotation đặt ở nơi không phải bean
 * không có tác dụng. Kết quả ({@link #declared()}) được dùng để đồng bộ danh mục quyền (M6).
 */
public class RequirePermissionScanner implements SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;
    private final ObjectProvider<PermissionProvider> provider;
    private volatile List<PermissionDef> declared = List.of();

    public RequirePermissionScanner(ConfigurableListableBeanFactory beanFactory,
                                    ObjectProvider<PermissionProvider> provider) {
        this.beanFactory = beanFactory;
        this.provider = provider;
    }

    /** Các quyền đã khai báo trong code, đã gộp theo mã, giữ thứ tự gặp. */
    public List<PermissionDef> declared() {
        return declared;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, String> byCode = new LinkedHashMap<>();
        Map<String, String> where = new LinkedHashMap<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            if (beanFactory.getBeanDefinition(name).isAbstract()) {
                continue;
            }
            Class<?> type = beanFactory.getType(name, false);
            if (type == null) {
                continue;
            }
            Class<?> userClass = ClassUtils.getUserClass(type);
            if (userClass.getName().startsWith("org.springframework.") || userClass.getName().startsWith("java.")) {
                continue;
            }
            RequirePermission onClass = AnnotatedElementUtils.findMergedAnnotation(userClass, RequirePermission.class);
            if (onClass != null) {
                register(byCode, where, onClass, userClass.getName());
            }
            for (Method m : ReflectionUtils.getUniqueDeclaredMethods(userClass)) {
                RequirePermission onMethod = AnnotatedElementUtils.findMergedAnnotation(m, RequirePermission.class);
                if (onMethod != null) {
                    register(byCode, where, onMethod, userClass.getName() + "#" + m.getName());
                }
            }
        }
        List<PermissionDef> result = new ArrayList<>();
        byCode.forEach((code, description) -> result.add(new PermissionDef(code, description)));
        this.declared = List.copyOf(result);

        if (!declared.isEmpty() && provider.getIfAvailable() == null) {
            throw new IllegalStateException("@RequirePermission is used (" + declared.size()
                    + " permission code(s), e.g. '" + declared.get(0).code() + "') but there is no PermissionProvider bean. "
                    + "Add cached-starter (Redis) and grpc-starter (identity lookup), or declare your own PermissionProvider.");
        }
    }

    private static void register(Map<String, String> byCode, Map<String, String> where, RequirePermission a,
                                 String location) {
        String code = a.value();
        if (!PermissionCodes.isValidDeclared(code)) {
            throw new IllegalStateException("Invalid @RequirePermission code '" + code + "' on " + location
                    + ". Expected <domain>:<action>, lowercase letters, digits and '-' (e.g. order:read).");
        }
        String description = a.description() == null ? "" : a.description();
        String existing = byCode.get(code);
        if (existing == null) {
            byCode.put(code, description);
            where.put(code, location);
        } else if (existing.isEmpty()) {
            byCode.put(code, description);
        } else if (!description.isEmpty() && !existing.equals(description)) {
            throw new IllegalStateException("@RequirePermission code '" + code + "' has conflicting descriptions: '"
                    + existing + "' (" + where.get(code) + ") vs '" + description + "' (" + location + ")");
        }
    }
}
