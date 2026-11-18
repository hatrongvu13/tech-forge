# jpa-starter

JPA + Flyway + PostgreSQL, lớp gốc entity và JPA auditing. Thay cho `shared-persistence` cũ.

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>jpa-starter</artifactId>
</dependency>
```

Entity kế thừa `AuditableEntity`:

```java
@Entity
@Table(name = "orders")
public class Order extends AuditableEntity { /* chỉ khai báo cột nghiệp vụ */ }
```

## Cung cấp

| Thành phần | Việc |
|---|---|
| `BaseEntity` | `id` UUID v7, `@Version`. `equals/hashCode` theo `id` |
| `AuditableEntity` | thêm `created_at`, `updated_at`, `created_by`, `updated_by` |
| `JpaAutoConfiguration` | bật JPA auditing; `created_by/updated_by` lấy từ `ContextHolder` (userId) |
| Spring Data JPA, Flyway, PostgreSQL | dependency đi kèm |

Cột SQL: `BaseEntity` cần `id uuid PRIMARY KEY`, `version bigint NOT NULL`. `AuditableEntity` cần thêm `created_at timestamptz NOT NULL`, `updated_at timestamptz NOT NULL`, `created_by varchar(100)`, `updated_by varchar(100)`.

## Cấu hình

| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `htv.jpa.enabled` | boolean | `true` | `false` thì tắt auto-configuration (không bật auditing) |

URL DB, user, mật khẩu: dùng khóa chuẩn của Spring (`spring.datasource.*`, `spring.flyway.*`).

## Ghi đè

Khai báo bean `AuditorAware<?>` của riêng service, bean mặc định sẽ lùi lại.

## Ghi chú

- `created_by` rỗng nếu request không có userId trong `ContextHolder`.
- Sắp có: `AppendOnlyEntity` (cho nhật ký kiểm toán) ở M2.
