# feign-starter

Gọi REST sang service khác bằng OpenFeign: không cần `@EnableFeignClients`, ngữ cảnh tự đi theo, lỗi tự thành
`BusinessException` mang đúng mã lỗi của bên kia, timeout mặc định ngắn.

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>feign-starter</artifactId>
</dependency>
```

```java
@FeignClient(name = "identity", url = "${clients.identity.url}")
public interface IdentityClient {
    @GetMapping("/users/{id}")
    BaseResponse<UserDto> get(@PathVariable UUID id);
}
```

```yaml
htv.feign.trusted-clients: identity   # service nội bộ: được nhận token của người dùng
clients.identity.url: http://identity-service:8080
```

## Cung cấp

| Thành phần | Việc |
|---|---|
| Quét `@FeignClient` | Tự quét mọi gói dưới `com.htv`. Service tự khai báo `@EnableFeignClients` thì starter lùi lại (không đăng ký hai lần) |
| `ContextRequestInterceptor` | Mọi client: `X-Trace-Id`, `X-Channel`. Chỉ client trong `trusted-clients`: thêm `Authorization` (Bearer) của request đang xử lý và `X-User-Id`. Header client tự đặt không bị ghi đè |
| `BusinessErrorDecoder` | Lỗi HTTP thành `BusinessException`. Đọc `code`/`message` trong `BaseResponse` của bên kia (ví dụ `IDENTITY-4041`). Không phải JSON đó thì dùng mã chung theo HTTP status |
| Timeout mặc định | Kết nối 2s, đọc 5s (Spring Cloud mặc định 10s/60s) |
| Resilience4j | Có sẵn trên classpath để bật circuit breaker (xem dưới) |

Lỗi kết nối/hết giờ (`feign.RetryableException`) được `web-starter` trả **503** `COMMON-5030`, không lộ địa chỉ.

## Cấu hình

| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `htv.feign.enabled` | boolean | `true` | Tắt toàn bộ starter |
| `htv.feign.trusted-clients` | list | rỗng | Tên client (`@FeignClient(name=...)`) là service nội bộ |
| `htv.feign.connect-timeout` | Duration | `2s` | Timeout kết nối mặc định |
| `htv.feign.read-timeout` | Duration | `5s` | Timeout đọc mặc định |
| `htv.feign.max-error-body-bytes` | int | `8192` | Số byte tối đa đọc từ thân lỗi |
| `spring.cloud.openfeign.client.config.<tên>.*` | | | Chuẩn của Spring Cloud. Luôn thắng mặc định ở trên |
| `spring.cloud.openfeign.circuitbreaker.enabled` | boolean | `false` | Bật circuit breaker (Resilience4j) cho mọi client |

## Ghi đè

| Muốn | Cách |
|---|---|
| Dịch lỗi khác | Khai báo bean `ErrorDecoder` |
| Truyền header khác | Khai báo bean `ContextRequestInterceptor`, hoặc thêm bean `RequestInterceptor` |
| Quét gói khác | Tự khai báo `@EnableFeignClients(basePackages = ...)` |

## Ghi chú

- **Bảo mật:** chỉ thêm client vào `trusted-clients` khi đó là service của mình. Client lạ mà được tin sẽ nhận token của người dùng.
- Chỉ chuyển tiếp `Authorization` dạng `Bearer` của request servlet đang xử lý. Job nền (không có request) không có token để chuyển.
- Bật circuit breaker thì lỗi bị bọc bởi `NoFallbackAvailableException` khi client không có fallback. Hãy khai báo fallback cho client đó.
- Chưa kiểm: gọi qua service discovery / load balancer (chưa có registry trong dự án). Test gọi bằng `url` cố định.
