# security-starter

Bảo mật cho service servlet: xác thực JWT, kiểm quyền bằng `@RequirePermission`, quét danh mục quyền.
Không tự gọi Redis hay gRPC: chỉ dùng giao diện `PermissionProvider` (cài đặt ở `cached-starter` và `grpc-starter`).

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>security-starter</artifactId>
</dependency>
```

```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: https://...          # hoặc jwk-set-uri / public-key-location
  audiences: order-service         # nên đặt: chỉ nhận token cấp cho service này
```

```java
@GetMapping("/{id}")
@RequirePermission(value = "order:read", description = "Xem đơn hàng")
OrderDto get(@PathVariable UUID id) { ... }
```

`@RequirePermission` đặt trên method, hoặc trên class làm mặc định (method ghi đè class).

## Cung cấp

| Thành phần | Việc |
|---|---|
| `SecurityFilterChain` mặc định | Stateless, tắt CSRF, mọi đường dẫn cần JWT trừ `htv.security.public-paths`. 401/403 trả đúng dạng `BaseResponse` |
| `UserContextFilter` | Sau khi xác thực, đưa `sub` của token vào `ContextHolder` + MDC (userId cho log, gRPC, Feign) |
| `PermissionAuthorizationManager` | Lấy quyền qua `PermissionProvider`, so khớp (`*`, `<miền>:*`, mã cụ thể) |
| `RequirePermissionScanner` | Quét bean lúc khởi động, kiểm mã, gom danh mục |
| `PermissionCatalogSyncer` | Khi ứng dụng sẵn sàng, gửi danh mục lên identity qua `PermissionCatalogPublisher`. Chạy nền, thử lại 1s → 5 phút |

## Quy tắc quyết định

- Không có token hoặc token sai/hết hạn: **401** `COMMON-4010`, không nói lý do.
- Thiếu quyền, hoặc `sub` không phải UUID: **403** `COMMON-4030`.
- Không xác định được quyền (Redis/identity lỗi): ném `COMMON-5030` (fail-closed). Không bao giờ cho qua.
- Endpoint **không** có `@RequirePermission` chỉ cần đăng nhập, không cần quyền cụ thể.

## An toàn mặc định

| Tình huống | Hành vi |
|---|---|
| Chưa cấu hình JWT | Từ chối **mọi** đường dẫn trừ `public-paths`, kèm cảnh báo trong log |
| Dùng `@RequirePermission` nhưng không có `PermissionProvider` | **Không khởi động**, thông báo nêu starter cần thêm (`cached-starter`, `grpc-starter`) |
| Mã quyền sai định dạng (`Order Read`, `order:*`) | **Không khởi động**, thông báo nêu lớp và method |
| Cùng một mã nhưng hai mô tả khác nhau | **Không khởi động**. (Cùng một mã dùng ở nhiều method là bình thường; mô tả để trống không xung đột) |
| Không có người dùng mặc định với mật khẩu sinh ngẫu nhiên | Đã chặn (`UserDetailsService` rỗng) |

Định dạng mã: `<miền>:<hành động>`, chữ thường, số, `-` (ví dụ `order:read`). Ký tự đại diện chỉ dùng khi **cấp** quyền, không dùng khi khai báo.

## Cấu hình

| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `htv.security.enabled` | boolean | `true` | Tắt toàn bộ starter |
| `htv.security.public-paths` | list | `/actuator/health/**`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Đường dẫn (kiểu Ant) không cần đăng nhập |
| `spring.security.oauth2.resourceserver.jwt.*` | | | Cấu hình JWT chuẩn của Spring Boot |
| `htv.security.catalog.enabled` | boolean | `true` | Bật đồng bộ danh mục quyền (cần bean `PermissionCatalogPublisher` và `spring.application.name`) |
| `htv.security.catalog.fail-fast` | boolean | `false` | Đồng bộ ngay lúc khởi động; lỗi hoặc có mã bị từ chối thì không khởi động. Bật cho CI/UAT |
| `htv.security.catalog.service-version` | string | build-info, rồi `unknown` | Version gửi lên. Dùng để bản cũ không tắt quyền của bản mới |
| `htv.security.catalog.retry-initial` / `retry-max` | Duration | `1s` / `5m` | Khoảng chờ thử lại, nhân đôi mỗi lần |

## Ghi đè

| Muốn | Cách |
|---|---|
| Chuỗi bảo mật riêng | Khai báo bean `SecurityFilterChain`. Thêm `new UserContextFilter()` sau `BearerTokenAuthenticationFilter` để vẫn có userId trong log |
| Đổi cách tra quyền | Khai báo bean `PermissionProvider` |

## Ghi chú

- Chỉ quét bean Spring (controller, gRPC service, `@Service`). `@RequirePermission` ở nơi không phải bean không có tác dụng.
- Token không mang quyền: đổi role có hiệu lực ngay khi cache của người dùng bị xóa (xem `cached-starter`).
- Đồng bộ chỉ thử lại lỗi hệ thống (5xx). Lỗi do yêu cầu hoặc quyền (ví dụ chứng chỉ không khớp tên) thì dừng và ghi ERROR.
- Mỗi lần khởi động chỉ đăng ký một lần, không định kỳ.
- Cần spring-boot-maven-plugin `build-info` để version tự đúng. Không có thì version là `unknown`, và việc chống bản cũ không có tác dụng.
- Chưa kiểm: `audiences` (là tính năng của Spring Boot, test dùng `JwtDecoder` dựng tay).
