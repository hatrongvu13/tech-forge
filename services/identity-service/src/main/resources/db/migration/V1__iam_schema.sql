-- identity-service: lược đồ IAM.
-- Quy ước: bảng số ít, tiền tố iam_; khóa chính uuid; thời gian dùng timestamptz (UTC);
-- cột của BaseEntity (id, version) và AuditableEntity (created_at, updated_at, created_by, updated_by).
-- Mã tham chiếu giữa aggregate bằng uuid, có FOREIGN KEY ở đây (không dùng @ManyToOne trong entity).

-- ---------------------------------------------------------------- iam_user
CREATE TABLE iam_user (
    id          uuid         PRIMARY KEY,
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    username    varchar(100) NOT NULL,
    email       varchar(255),
    phone       varchar(32),
    full_name   varchar(255),
    status      varchar(20)  NOT NULL,
    CONSTRAINT ck_iam_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'DELETED'))
);
CREATE UNIQUE INDEX ux_iam_user_username ON iam_user (lower(username));
CREATE UNIQUE INDEX ux_iam_user_email    ON iam_user (lower(email)) WHERE email IS NOT NULL;

-- ------------------------------------------------------ iam_user_credential
CREATE TABLE iam_user_credential (
    id               uuid         PRIMARY KEY,
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    created_by       varchar(100),
    updated_by       varchar(100),
    user_id          uuid         NOT NULL REFERENCES iam_user (id),
    type             varchar(20)  NOT NULL,
    password_hash    varchar(255),
    password_version integer      NOT NULL DEFAULT 1,
    expired_at       timestamptz,
    CONSTRAINT ck_iam_credential_type CHECK (type IN ('LOCAL', 'LDAP', 'OIDC', 'SAML')),
    -- chỉ LOCAL có mật khẩu
    CONSTRAINT ck_iam_credential_password CHECK (
        (type = 'LOCAL' AND password_hash IS NOT NULL) OR (type <> 'LOCAL' AND password_hash IS NULL)),
    CONSTRAINT ux_iam_credential_user_type UNIQUE (user_id, type)
);

-- ---------------------------------------------------------- iam_user_session
CREATE TABLE iam_user_session (
    id          uuid         PRIMARY KEY,
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    user_id     uuid         NOT NULL REFERENCES iam_user (id),
    channel     varchar(50),
    ip_address  varchar(45),
    user_agent  varchar(500),
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz
);
CREATE INDEX ix_iam_user_session_user ON iam_user_session (user_id);

-- ------------------------------------------------------------ iam_user_token
-- Chỉ lưu băm của token, không lưu giá trị gốc.
CREATE TABLE iam_user_token (
    id          uuid         PRIMARY KEY,
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    session_id  uuid         NOT NULL REFERENCES iam_user_session (id),
    type        varchar(20)  NOT NULL,
    token_hash  varchar(128) NOT NULL,
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz,
    CONSTRAINT ck_iam_token_type CHECK (type IN ('REFRESH')),
    CONSTRAINT ux_iam_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_iam_user_token_session ON iam_user_token (session_id);

-- ------------------------------------------------------------------ iam_role
CREATE TABLE iam_role (
    id           uuid         PRIMARY KEY,
    version      bigint       NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    created_by   varchar(100),
    updated_by   varchar(100),
    code         varchar(100) NOT NULL,
    name         varchar(255) NOT NULL,
    system_role  boolean      NOT NULL DEFAULT false,
    description  varchar(500),
    CONSTRAINT ux_iam_role_code UNIQUE (code)
);

-- ------------------------------------------------------------ iam_permission
-- Danh mục quyền do các service tự đồng bộ (mốc M6). '*' là quyền đặc biệt "mọi quyền".
CREATE TABLE iam_permission (
    id            uuid         PRIMARY KEY,
    version       bigint       NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    created_by    varchar(100),
    updated_by    varchar(100),
    code          varchar(150) NOT NULL,
    description   varchar(500),
    service_name  varchar(100),
    active        boolean      NOT NULL DEFAULT true,
    CONSTRAINT ux_iam_permission_code UNIQUE (code),
    CONSTRAINT ck_iam_permission_code CHECK (code = '*' OR code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$')
);
CREATE INDEX ix_iam_permission_service ON iam_permission (service_name);

-- ------------------------------------------------------- iam_role_permission
CREATE TABLE iam_role_permission (
    id             uuid         PRIMARY KEY,
    version        bigint       NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    created_by     varchar(100),
    updated_by     varchar(100),
    role_id        uuid         NOT NULL REFERENCES iam_role (id),
    permission_id  uuid         NOT NULL REFERENCES iam_permission (id),
    CONSTRAINT ux_iam_role_permission UNIQUE (role_id, permission_id)
);
CREATE INDEX ix_iam_role_permission_permission ON iam_role_permission (permission_id);

-- ------------------------------------------------------------- iam_user_role
CREATE TABLE iam_user_role (
    id              uuid         PRIMARY KEY,
    version         bigint       NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    created_by      varchar(100),
    updated_by      varchar(100),
    user_id         uuid         NOT NULL REFERENCES iam_user (id),
    role_id         uuid         NOT NULL REFERENCES iam_role (id),
    effective_from  timestamptz  NOT NULL,
    effective_to    timestamptz,
    assigned_by     uuid         REFERENCES iam_user (id),
    CONSTRAINT ck_iam_user_role_period CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ux_iam_user_role UNIQUE (user_id, role_id, effective_from)
);
CREATE INDEX ix_iam_user_role_role ON iam_user_role (role_id);

-- --------------------------------------------------------- iam_service_catalog
CREATE TABLE iam_service_catalog (
    id               uuid         PRIMARY KEY,
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    created_by       varchar(100),
    updated_by       varchar(100),
    service_name     varchar(100) NOT NULL,
    service_version  varchar(100) NOT NULL,
    instance_id      varchar(200),
    registered_at    timestamptz  NOT NULL,
    CONSTRAINT ux_iam_service_catalog_name UNIQUE (service_name)
);

-- ------------------------------------------------------------- iam_audit_log
-- Chỉ thêm. Không có FOREIGN KEY tới iam_user để nhật ký tồn tại độc lập với người dùng.
CREATE TABLE iam_audit_log (
    id           uuid         PRIMARY KEY,
    created_at   timestamptz  NOT NULL,
    user_id      uuid,
    action       varchar(100) NOT NULL,
    entity_type  varchar(100),
    entity_id    varchar(100),
    ip_address   varchar(45)
);
CREATE INDEX ix_iam_audit_log_user   ON iam_audit_log (user_id, created_at);
CREATE INDEX ix_iam_audit_log_entity ON iam_audit_log (entity_type, entity_id);
