package com.htv.identity.domain.model.identity;

import com.htv.identity.domain.enums.CredentialType;
import com.htv.starter.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Thông tin xác thực của người dùng. Mỗi người dùng có tối đa một dòng cho mỗi {@link CredentialType}.
 * Chỉ {@code LOCAL} có mật khẩu; LDAP/OIDC/SAML để {@code passwordHash} rỗng.
 */
@Entity
@Table(name = "iam_user_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCredentialEntity extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private CredentialType type;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "password_version", nullable = false)
    private int passwordVersion;

    @Column(name = "expired_at")
    private Instant expiredAt;

    public static UserCredentialEntity local(UUID userId, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank for LOCAL credential");
        }
        UserCredentialEntity c = new UserCredentialEntity();
        c.userId = requireUser(userId);
        c.type = CredentialType.LOCAL;
        c.passwordHash = passwordHash;
        c.passwordVersion = 1;
        return c;
    }

    public static UserCredentialEntity external(UUID userId, CredentialType type) {
        if (type == null || type == CredentialType.LOCAL) {
            throw new IllegalArgumentException("external credential must not be LOCAL");
        }
        UserCredentialEntity c = new UserCredentialEntity();
        c.userId = requireUser(userId);
        c.type = type;
        c.passwordVersion = 1;
        return c;
    }

    /** Đổi mật khẩu, tăng phiên bản (để vô hiệu hóa token cũ theo phiên bản mật khẩu). */
    public void changePassword(String newPasswordHash, Instant expiredAt) {
        if (type != CredentialType.LOCAL) {
            throw new IllegalStateException("Only LOCAL credential has a password");
        }
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        this.passwordHash = newPasswordHash;
        this.passwordVersion++;
        this.expiredAt = expiredAt;
    }

    private static UUID requireUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return userId;
    }
}
