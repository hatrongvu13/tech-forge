-- Seed khởi tạo: role hệ thống SUPER_ADMIN giữ quyền '*' (mọi quyền).
-- Chạy lại an toàn (idempotent). Chưa tạo người dùng quản trị: việc đó thuộc phần đăng nhập.

INSERT INTO iam_role (id, version, created_at, updated_at, code, name, system_role, description)
VALUES (gen_random_uuid(), 0, now(), now(), 'SUPER_ADMIN', 'Super administrator', true,
        'Role hệ thống, giữ mọi quyền. Không được xóa.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam_permission (id, version, created_at, updated_at, code, description, service_name, active)
VALUES (gen_random_uuid(), 0, now(), now(), '*', 'Mọi quyền', NULL, true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam_role_permission (id, version, created_at, updated_at, role_id, permission_id)
SELECT gen_random_uuid(), 0, now(), now(), r.id, p.id
FROM iam_role r
         CROSS JOIN iam_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code = '*'
ON CONFLICT (role_id, permission_id) DO NOTHING;
