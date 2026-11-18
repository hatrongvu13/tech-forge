package com.htv.identity.domain.model.identity;

import com.htv.starter.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một phiên đăng nhập của người dùng trên một kênh/thiết bị. */
@Entity
@Table(name = "iam_user_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSessionEntity extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "channel", length = 50)
    private String channel;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static UserSessionEntity open(UUID userId, String channel, String ipAddress, String userAgent,
                                         Instant expiresAt) {
        if (userId == null || expiresAt == null) {
            throw new IllegalArgumentException("userId and expiresAt are required");
        }
        UserSessionEntity s = new UserSessionEntity();
        s.userId = userId;
        s.channel = channel;
        s.ipAddress = ipAddress;
        s.userAgent = userAgent;
        s.expiresAt = expiresAt;
        return s;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }
}
