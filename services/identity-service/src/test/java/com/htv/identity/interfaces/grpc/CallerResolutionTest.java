package com.htv.identity.interfaces.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase;
import com.htv.identity.infrastructure.config.IdentityCatalogProperties;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Ai được đăng ký danh mục của service nào: tên lấy từ chứng chỉ, không tin thân yêu cầu. */
class CallerResolutionTest {

    private static PermissionCatalogGrpcService service(boolean trustBody) {
        IdentityCatalogProperties p = new IdentityCatalogProperties();
        p.setTrustRequestBody(trustBody);
        return new PermissionCatalogGrpcService(mock(RegisterPermissionCatalogUseCase.class), p);
    }

    @Test
    void certificateNameIsUsedAndMustMatchTheClaimedName() {
        PermissionCatalogGrpcService s = service(false);
        assertThat(s.resolveCaller("order-service", Optional.of("order-service"))).isEqualTo("order-service");
        assertThat(s.resolveCaller("", Optional.of("order-service"))).isEqualTo("order-service");
        assertThatThrownBy(() -> s.resolveCaller("billing-service", Optional.of("order-service")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void certificateWinsEvenWhenTheBodyIsTrusted() {
        assertThatThrownBy(() -> service(true).resolveCaller("billing-service", Optional.of("order-service")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void withoutCertificateTheRequestIsRefusedUnlessBodyIsTrusted() {
        assertThatThrownBy(() -> service(false).resolveCaller("order-service", Optional.empty()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
        assertThat(service(true).resolveCaller("order-service", Optional.empty())).isEqualTo("order-service");
        assertThatThrownBy(() -> service(true).resolveCaller("", Optional.empty()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
    }

    @Test
    void trustingTheBodyIsForbiddenWithTheProdProfile() {
        IdentityCatalogProperties p = new IdentityCatalogProperties();
        p.setTrustRequestBody(true);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> p.validate(prod)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
        p.validate(new MockEnvironment().withProperty("spring.profiles.active", "dev")); // dev: được
        IdentityCatalogProperties off = new IdentityCatalogProperties();
        off.validate(prod); // mặc định tắt: prod vẫn khởi động
    }
}
