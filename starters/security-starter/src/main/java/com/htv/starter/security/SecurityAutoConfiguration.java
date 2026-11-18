package com.htv.starter.security;

import com.htv.shared.auth.PermissionCatalogPublisher;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.auth.RequirePermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Bảo mật cho service servlet. Tắt bằng {@code htv.security.enabled=false}.
 *
 * <ul>
 *   <li>Có {@link JwtDecoder} (cấu hình {@code spring.security.oauth2.resourceserver.jwt.*}): mọi đường dẫn
 *       cần JWT hợp lệ, trừ {@code htv.security.public-paths}.
 *   <li>Không có {@link JwtDecoder}: từ chối tất cả trừ đường dẫn công khai, kèm cảnh báo (an toàn mặc định).
 *   <li>{@code @RequirePermission} được kiểm bằng {@link PermissionProvider}.
 * </ul>
 */
@Slf4j
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, HttpSecurity.class})
@ConditionalOnProperty(prefix = "htv.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfiguration {

    /**
     * Chuỗi lọc mặc định. Service tự khai báo {@link SecurityFilterChain} thì chuỗi này lùi lại.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain htvSecurityFilterChain(HttpSecurity http, SecurityProperties properties,
                                               ObjectProvider<JwtDecoder> jwtDecoder) throws Exception {
        boolean jwt = jwtDecoder.getIfAvailable() != null;
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint();
        ApiAccessDeniedHandler deniedHandler = new ApiAccessDeniedHandler();
        String[] publicPaths = properties.getPublicPaths().toArray(String[]::new);

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(a -> {
                    if (publicPaths.length > 0) {
                        a.requestMatchers(publicPaths).permitAll();
                    }
                    if (jwt) {
                        a.anyRequest().authenticated();
                    } else {
                        a.anyRequest().denyAll();
                    }
                });
        if (jwt) {
            http.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())
                            .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                    .addFilterAfter(new UserContextFilter(), BearerTokenAuthenticationFilter.class);
        } else {
            log.warn("No JwtDecoder bean: all requests except htv.security.public-paths are denied. "
                    + "Set spring.security.oauth2.resourceserver.jwt.issuer-uri, jwk-set-uri or public-key-location.");
        }
        return http.build();
    }

    /**
     * Không tạo người dùng mặc định với mật khẩu sinh ngẫu nhiên (Spring Boot hay in ra log).
     */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    UserDetailsService htvNoUsers() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    @ConditionalOnMissingBean
    PermissionAuthorizationManager permissionAuthorizationManager(ObjectProvider<PermissionProvider> provider) {
        return new PermissionAuthorizationManager(provider);
    }

    @Bean
    @ConditionalOnMissingBean
    RequirePermissionScanner requirePermissionScanner(ConfigurableListableBeanFactory beanFactory,
                                                      ObjectProvider<PermissionProvider> provider) {
        return new RequirePermissionScanner(beanFactory, provider);
    }

    /**
     * Gắn bộ kiểm quyền vào mọi method có {@link RequirePermission}.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor requirePermissionAdvisor(ObjectProvider<PermissionAuthorizationManager> manager) {
        AuthorizationManagerBeforeMethodInterceptor interceptor = new AuthorizationManagerBeforeMethodInterceptor(
                new RequirePermissionPointcut(),
                (authentication, invocation) -> manager.getObject().authorize(authentication, invocation));
        interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder());
        return interceptor;
    }

    /**
     * Bật hạ tầng method security (đăng ký auto-proxy). Service vẫn dùng được {@code @PreAuthorize}.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
