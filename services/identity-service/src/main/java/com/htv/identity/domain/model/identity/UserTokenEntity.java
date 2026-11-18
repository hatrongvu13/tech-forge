package com.htv.identity.domain.model.identity;

import com.htv.identity.domain.enums.TokenType;
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
 * Token làm mới của một phiên. **Chỉ lưu băm** ({@code token_hash}), không bao giờ lưu giá trị gốc.
 * (Luồng phát hành/xoay vòng token thuộc phần đăng nhập, chưa làm ở giai đoạn này.)
 */
@Entity
@Table(name = "iam_user_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTokenEntity extends AuditableEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 20)
    private TokenType type;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static UserTokenEntity issue(UUID sessionId, TokenType type, String tokenHash, Instant expiresAt) {
        if (sessionId == null || type == null || expiresAt == null || tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("sessionId, type, tokenHash and expiresAt are required");
        }
        UserTokenEntity t = new UserTokenEntity();
        t.sessionId = sessionId;
        t.type = type;
        t.tokenHash = tokenHash;
        t.expiresAt = expiresAt;
        return t;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }
}
