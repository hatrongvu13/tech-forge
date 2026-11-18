package com.htv.identity.domain.model.identity;

import com.htv.identity.domain.enums.UserStatus;
import com.htv.starter.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Người dùng. Duy nhất theo {@code lower(username)} và {@code lower(email)} (index trong SQL).
 */
@Entity
@Table(name = "iam_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends AuditableEntity {

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    public static UserEntity register(String username, String email, String phone, String fullName) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        UserEntity u = new UserEntity();
        u.username = username.trim();
        u.email = blankToNull(email);
        u.phone = blankToNull(phone);
        u.fullName = blankToNull(fullName);
        u.status = UserStatus.ACTIVE;
        return u;
    }

    public void updateProfile(String email, String phone, String fullName) {
        this.email = blankToNull(email);
        this.phone = blankToNull(phone);
        this.fullName = blankToNull(fullName);
    }

    public void changeStatus(UserStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
