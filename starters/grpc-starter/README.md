# grpc-starter

gRPC cho service: chạy server cùng vòng đời Spring, tạo kênh client, interceptor ngữ cảnh / log / lỗi,
tra quyền và đăng ký danh mục quyền với identity-service.

Spring Boot 4.0.x chưa có starter gRPC dựng sẵn (có từ 4.1), nên starter này tự quản vòng đời server.

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>grpc-starter</artifactId>
</dependency>
```

**Server:** khai báo bean `BindableService` (lớp con `...ImplBase` đánh dấu `@Component`). Server tự chạy ở cổng `htv.grpc.server.port`.

**Client:**

```java
var stub = OrderServiceGrpc.newBlockingStub(channelFactory.channel("order")); // htv.grpc.client.channels.order.address
var result = GrpcErrors.translate(() -> stub.getOrder(request));                // lỗi gRPC -> BusinessException
```

## Cung cấp

| Thành phần | Việc |
|---|---|
| `PeerIdentityInterceptor` + `GrpcPeers.serviceName()` | Đọc CN của chứng chỉ client (mTLS) để biết service nào đang gọi |
| `GrpcServerLifecycle` | Chạy server, health service, tắt êm (báo NOT_SERVING, chờ lời gọi đang chạy) |
| `ContextServerInterceptor` / `ContextClientInterceptor` | Chuyển `traceId`, `userId`, `channel` qua metadata. Server đặt vào `ContextHolder` + MDC mỗi callback |
| `GrpcAccessLogInterceptor` | Mỗi lời gọi một dòng: `Service/Method -> OK 12ms userId=.. traceId=..`. Không ghi nội dung |
| `GrpcExceptionInterceptor` | Exception thành status chuẩn. Lỗi lạ trả `INTERNAL "Internal error"`, không lộ chi tiết |
| `DeadlineClientInterceptor` | Deadline mặc định cho lời gọi chưa đặt deadline |
| `GrpcErrors` | `translate(...)` và `toBusinessException(...)`: lỗi gRPC thành `BusinessException` mang đúng mã lỗi của service kia |
| `GrpcPermissionProvider` | Tra quyền từ identity (`PermissionQueryService`). Lỗi thì `COMMON-5030` (fail-closed) |
| `GrpcPermissionCatalogPublisher` | Gửi danh mục quyền (`PermissionCatalogService.Register`) |

Thứ tự interceptor phía server (ngoài vào trong): danh tính service gọi → ngữ cảnh → access log → xử lý lỗi → bean `ServerInterceptor` của service (theo `@Order`) → handler.

Mã lỗi đi xuyên service: server ghi trailer `x-error-code`, `GrpcErrors` đọc lại.

## Cấu hình

| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `htv.grpc.enabled` | boolean | `true` | Tắt toàn bộ starter |
| `htv.grpc.server.port` | int | `9090` | Cổng gRPC (`0` = ngẫu nhiên, cho test) |
| `htv.grpc.server.max-inbound-message-size` | DataSize | `4MB` | Kích thước message tối đa nhận vào |
| `htv.grpc.server.shutdown-timeout` | Duration | `30s` | Thời gian chờ lời gọi đang chạy khi tắt |
| `htv.grpc.server.reflection` | boolean | `false` | Bật reflection (grpcurl). Tắt vì lộ danh sách API |
| `htv.grpc.server.trust-user-metadata` | boolean | theo mTLS | Tin `x-user-id`. Để trống: chỉ tin khi `tls.client-auth=REQUIRE` |
| `htv.grpc.server.tls.cert-chain` / `private-key` / `trust-certs` | đường dẫn PEM | rỗng | TLS. Rỗng toàn bộ = không mã hóa (chỉ dev) |
| `htv.grpc.server.tls.client-auth` | `NONE`/`OPTIONAL`/`REQUIRE` | `NONE` | `REQUIRE` = mTLS (cần `trust-certs`) |
| `htv.grpc.server.access-log.enabled` | boolean | `true` | Bật/tắt access log |
| `htv.grpc.server.access-log.exclude-methods` | list | `grpc.health.v1.Health/*` | Phương thức (kiểu Ant) không ghi |
| `htv.grpc.client.default-deadline` | Duration | `5s` | Deadline mặc định (`0` = không đặt) |
| `htv.grpc.client.channels.<tên>.address` | string | | Địa chỉ, ví dụ `dns:///identity-service:9090` |
| `htv.grpc.client.channels.<tên>.load-balancing-policy` | string | `round_robin` | Hợp với headless service của Kubernetes |
| `htv.grpc.client.channels.<tên>.tls.*` | | rỗng | TLS/mTLS phía client (`cert-chain`, `private-key` để trình chứng chỉ; `trust-certs` để tin CA) |

**Kênh `identity`:** đặt `htv.grpc.client.channels.identity.address` thì starter tự tạo bean `grpcPermissionProvider` và `PermissionCatalogPublisher`.

## Ghi đè

| Muốn | Cách |
|---|---|
| Thêm interceptor server / client | Khai báo bean `ServerInterceptor` / `ClientInterceptor` (chạy sau interceptor có sẵn) |
| Thay cách tra quyền | Khai báo bean tên `grpcPermissionProvider`, hoặc bean `PermissionProvider` khác |
| Thay publisher danh mục quyền | Khai báo bean `PermissionCatalogPublisher` (identity-service làm vậy để ghi thẳng DB) |

## Ghi chú

- **Niềm tin:** `x-user-id` trong metadata chỉ đáng tin khi server xác thực được service gọi bằng mTLS. Không có mTLS thì userId bị bỏ qua (traceId vẫn dùng).
- **mTLS đã kiểm thật** (`MtlsTest`, chứng chỉ do openssl cấp): đọc đúng CN; client không có chứng chỉ, hoặc chứng chỉ từ CA khác (dù trùng CN), bị từ chối ở bước bắt tay.
- `GrpcPeers.serviceName()` chỉ có giá trị khi server bật mTLS. Không có mTLS thì rỗng.
- `GrpcExceptionInterceptor` chỉ bắt exception ném đồng bộ trong handler. Handler tự tạo luồng phải báo lỗi qua `responseObserver.onError`.
- Tên service/method trong access log là thông tin hợp đồng, không phải dữ liệu nhạy cảm.
