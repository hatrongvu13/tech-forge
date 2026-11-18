# web-starter

REST cho service servlet (Spring MVC): ngữ cảnh request, xử lý lỗi thống nhất, access log, OpenAPI.
Không dùng cho gateway (WebFlux).

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>web-starter</artifactId>
</dependency>
```

Controller trả `BaseResponse` bằng `ApiResponses`:

```java
@GetMapping("/{id}")
BaseResponse<OrderDto> get(@PathVariable UUID id) {
    return ApiResponses.success(service.get(id));
}
```

Lỗi nghiệp vụ: ném `BusinessException(ErrorCode)`. Handler lo phần còn lại.

## Cung cấp

| Thành phần | Việc |
|---|---|
| `RequestContextFilter` | Dựng `RequestContext` (traceId, spanId, userId, channel, locale), đặt vào `ContextHolder` + MDC, dọn trong `finally`, trả header `X-Trace-Id` |
| `AccessLogFilter` | Mỗi request một dòng: `GET /path -> 200 12ms userId=.. traceId=..`. Không ghi query, body, header |
| `GlobalExceptionHandler` | Mọi lỗi thành `BaseResponse`. Thông điệp lấy từ `ErrorCode`, không lấy từ exception |
| `ApiResponses` | `success(data)`, `success()`, `error(code)`, `error(code, data)` |
| springdoc | OpenAPI + Swagger UI. **Tắt ở profile `prod`** |

Bảng lỗi:

| Nguồn | HTTP | Mã |
|---|---|---|
| `BusinessException` | theo `ErrorCode.httpStatus()` | mã của `ErrorCode` |
| Validation (`@Valid`, `@Validated`) | 400 | `COMMON-4000`, `data` là danh sách `{field, message}` |
| JSON hỏng, thiếu tham số | 400 | `COMMON-4000` |
| Sai method / sai media type | 405 / 415 | `COMMON-4050` / `COMMON-4150` |
| `AccessDeniedException` / `AuthenticationException` của Spring Security | 403 / 401 | `COMMON-4030` / `COMMON-4010` |
| Lỗi không lường trước | 500 | `COMMON-5000`, **không** lộ chi tiết |

## Cấu hình

| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `htv.web.enabled` | boolean | `true` | Tắt toàn bộ starter |
| `htv.web.trust-user-header` | boolean | `false` | Tin header `X-User-Id`. **Chỉ bật khi service nằm sau gateway đã xóa header của client và mạng chặn truy cập trực tiếp.** Dùng JWT thì giữ `false` |
| `htv.web.access-log.enabled` | boolean | `true` | Bật/tắt access log |
| `htv.web.access-log.exclude-paths` | list | `/actuator/**` | Đường dẫn (kiểu Ant) không ghi |
| `springdoc.*` | | tắt ở `prod` | Ghi `springdoc.swagger-ui.enabled=true` để bật lại |

## Thứ tự filter

`RequestContextFilter` (order `HIGHEST_PRECEDENCE + 10`) rồi `AccessLogFilter` (`+20`), cả hai trước Spring Security (`-100`). Nhờ vậy 401/403 cũng có traceId và có dòng log.

Hệ quả: lúc filter chạy, JWT chưa được xác thực nên `userId` chưa có. `security-starter` (M5) cập nhật bằng `MdcContextBinder.update(...)` sau khi xác thực.

## Ghi đè

| Muốn | Cách |
|---|---|
| Đổi xử lý lỗi | Khai báo bean `GlobalExceptionHandler` (hoặc lớp con) |
| Lấy userId từ nguồn khác | Khai báo bean `UserIdResolver` |
| Đổi filter | Khai báo bean tên `htvRequestContextFilter` hoặc `htvAccessLogFilter` |

## Ghi chú

- Trace id lấy theo thứ tự: MDC do Micrometer đặt (khi có `tracing-starter`), header `traceparent`, `X-Trace-Id`, tự sinh. Giá trị từ client chỉ nhận khi gồm `[A-Za-z0-9._-]`, tối đa 64 ký tự (chống chèn log).
- Chưa kiểm springdoc 3.0.3 chạy thật trên Boot 4.0.9: test hiện chỉ kiểm cấu hình. Sẽ kiểm bằng service rỗng ở M8.
- Ngữ cảnh không tự sang luồng tạo bằng `new Thread`. Dùng executor của Spring (đã có `ContextPropagatingTaskDecorator` từ `core-starter`).
