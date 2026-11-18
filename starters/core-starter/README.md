# core-starter

Nền của mọi service: MDC theo ngữ cảnh, giữ ngữ cảnh ở luồng bất đồng bộ, mặc định logging.

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>core-starter</artifactId>
</dependency>
```

Thường không khai báo trực tiếp: đã có sẵn trong `web-starter` và các starter nhóm.

## Cung cấp

| Thành phần | Việc |
|---|---|
| `MdcContextBinder` | Đặt `RequestContext` vào `ContextHolder` và MDC (`traceId`, `spanId`, `userId`, `channel`), khôi phục khi đóng. Mọi cổng vào (filter, interceptor, listener) dùng lớp này |
| `ContextPropagatingTaskDecorator` | Chép ngữ cảnh sang luồng `@Async`/executor và dọn sau khi chạy |
| `CoreEnvironmentPostProcessor` | Profile `prod`: log JSON (`logging.structured.format.console=ecs`). Dev giữ log chữ |

## Cấu hình

| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `htv.core.enabled` | boolean | `true` | Tắt toàn bộ starter |
| `logging.structured.format.console` | string | `ecs` ở profile `prod` | Chuẩn của Spring Boot. Ghi trong `application.yml` thì thắng mặc định |

## Dùng `MdcContextBinder`

```java
try (MdcContextBinder.Scope scope = MdcContextBinder.open(context)) {
    // xử lý; ContextHolder và MDC có dữ liệu
}   // đóng: khôi phục trạng thái trước đó
```

Có userId sau khi xác thực: `MdcContextBinder.update(contextMoi)` trong cùng phạm vi.

## Ghi đè

Khai báo bean `TaskDecorator` của riêng service, bản mặc định sẽ lùi lại.

## Ghi chú

- Executor tự tạo bằng `new ThreadPoolExecutor(...)` **không** tự gắn decorator. Dùng `TaskExecutorBuilder` hoặc gọi `decorator.decorate(task)` thủ công.
