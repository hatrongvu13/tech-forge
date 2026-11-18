# Starter nhóm (bundles)

Mỗi nhóm chỉ gom starter đơn, không có code. Service khai báo **một** nhóm và lớp `@SpringBootApplication`.

| Nhóm | Gồm | Dùng cho |
|---|---|---|
| `service-starter` | core, web, security, tracing | Service REST không DB |
| `service-starter-jpa-cache-grpc` | `service-starter` + jpa, cache, grpc | `identity-service` |
| `service-starter-jpa-cache-grpc-feign` | `service-starter-jpa-cache-grpc` + feign | `order-service` |

Không dùng nhóm: `notification-service` (core + tracing + Kafka) và `gateway` (WebFlux).

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>service-starter-jpa-cache-grpc</artifactId>
</dependency>
```

```java
@SpringBootApplication
public class IdentityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
```

Service chỉ khai báo địa chỉ và bí mật (DB, Redis, JWT, kênh gRPC) theo README của từng starter.

## Đổi nhóm

| Muốn | Sửa |
|---|---|
| Thêm/bỏ công nghệ cho mọi service dùng một nhóm | pom của nhóm đó |
| Thêm cho mọi service REST | `service-starter/pom.xml` |
| Tổ hợp mới | Tạo nhóm mới khi có ít nhất một service dùng đúng tổ hợp đó |

## Kiểm

Mỗi nhóm có test khởi động một service rỗng chỉ với đúng nhóm đó:

- `service-starter`: `EmptyServiceIT` (health, 401 dạng `BaseResponse`, traceparent, OpenAPI).
- `service-starter-jpa-cache-grpc-feign`: `EmptyFullServiceIT` (PostgreSQL và Redis thật, đủ bean của mọi starter).
- `service-starter-jpa-cache-grpc`: kiểm qua `identity-service`.

Mỗi nhóm là một jar gần rỗng (chỉ có `META-INF/htv-bundle.txt`), nên service khai báo như dependency thường, không cần `<type>pom</type>`.
