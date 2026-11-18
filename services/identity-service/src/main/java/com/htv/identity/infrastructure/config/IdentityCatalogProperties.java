package com.htv.identity.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Thuộc tính {@code htv.identity.catalog.*}. */
@ConfigurationProperties(prefix = "htv.identity.catalog")
public class IdentityCatalogProperties {

    /**
     * Tin {@code service_name} trong thân yêu cầu đăng ký khi không có chứng chỉ mTLS. CHỈ cho dev.
     * Bật cùng profile {@code prod} thì identity-service không khởi động.
     */
    private boolean trustRequestBody = false;

    public boolean isTrustRequestBody() {
        return trustRequestBody;
    }

    public void setTrustRequestBody(boolean trustRequestBody) {
        this.trustRequestBody = trustRequestBody;
    }

    /** Gọi lúc khởi động. */
    public void validate(Environment environment) {
        if (trustRequestBody && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "htv.identity.catalog.trust-request-body=true is forbidden with the 'prod' profile");
        }
    }
}
