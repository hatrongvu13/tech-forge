package com.htv.starter.cached;

import com.htv.shared.auth.PermissionProvider;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Khớp khi service CHƯA tự khai báo {@link PermissionProvider} riêng. Hai bean của chính các starter
 * ({@code grpcPermissionProvider}, {@code redisPermissionProvider}) không tính là "riêng".
 */
class NoCustomPermissionProviderCondition extends SpringBootCondition {

    private static final Set<String> STARTER_BEANS = Set.of("grpcPermissionProvider", "redisPermissionProvider");

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String[] names = context.getBeanFactory().getBeanNamesForType(PermissionProvider.class, true, false);
        String[] custom = Arrays.stream(names).filter(n -> !STARTER_BEANS.contains(n)).toArray(String[]::new);
        return custom.length == 0
                ? ConditionOutcome.match("no custom PermissionProvider")
                : ConditionOutcome.noMatch("custom PermissionProvider present: " + Arrays.toString(custom));
    }
}
