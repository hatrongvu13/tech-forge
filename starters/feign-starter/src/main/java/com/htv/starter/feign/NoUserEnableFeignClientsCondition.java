package com.htv.starter.feign;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Khớp khi chưa có lớp cấu hình nào của service mang {@link EnableFeignClients}. Auto-configuration được xử lý
 * sau cấu hình của service, nên lúc này mọi lớp của service đã được đăng ký.
 */
class NoUserEnableFeignClientsCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        if (beanFactory == null) {
            return ConditionOutcome.match("no bean factory");
        }
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(name);
            if (definition instanceof AnnotatedBeanDefinition annotated
                    && annotated.getMetadata().hasAnnotation(EnableFeignClients.class.getName())) {
                return ConditionOutcome.noMatch("@EnableFeignClients found on bean '" + name + "'");
            }
        }
        return ConditionOutcome.match("no @EnableFeignClients in the application");
    }
}
