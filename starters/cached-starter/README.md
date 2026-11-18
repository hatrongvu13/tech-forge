# cached-starter

Redis (Lettuce) và cache quyền của người dùng: phía đọc cho mọi service, phía ghi/xóa cho identity-service.

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>cached-starter</artifactId>
</dependency>
```

```yaml
spring.data.redis:
  host: redis
  port: 6379
# mật khẩu: đặt biến môi trường SPRING_DATA_REDIS_PASSWORD
```

## Cung cấp

| Thành phần | Ai dùng | Việc |
|---|---|---|
| `RedisPermissionProvider` (bean `redisPermissionProvider`, `@Primary`) | Mọi service | Đọc `iam:perm:user:{userId}`. Có thì trả ngay. Thiếu (miss) thì hỏi bean `grpcPermissionProvider` (nếu có; identity tự ghi cache) |
| `PermissionCacheStore` | identity-service | `put` (ghi nguyên tử bằng Lua: xóa, thêm, đặt TTL), `evict`, `evictAll` (theo lô 500 khóa) |

Định dạng khóa và giá trị nằm ở `shared-auth` (`PermissionCacheKeys`): SET Redis các mã quyền. Người dùng không có quyền nào lưu một phần tử `__none__` để khỏi hỏi identity lặp lại.

## Quy tắc

- **Fail-closed:** Redis lỗi, hoặc miss mà không có nơi để hỏi, thì ném `COMMON-5030`. Redis lỗi **không** chuyển sang hỏi identity (đúng thiết kế đã duyệt).
- Đổi quyền không cần sửa token: identity xóa khóa sau khi giao dịch commit. Nếu xóa lỗi, khóa tự hết hạn theo TTL.

## Cấu hình

| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `htv.cache.enabled` | boolean | `true` | Tắt toàn bộ starter |
| `htv.cache.permissions.enabled` | boolean | `true` | Tắt riêng phần quyền |
| `htv.cache.permissions.ttl` | Duration | `10m` | Thời gian sống của khóa. Cũng là độ trễ tối đa khi việc xóa cache thất bại |

## Ghi đè

Service khai báo bean `PermissionProvider` của riêng mình thì `redisPermissionProvider` tự lùi lại. Bean `grpcPermissionProvider` của `grpc-starter` **không** tính là "riêng" (nó là nơi để hỏi khi miss). Identity-service làm vậy vì nó là nguồn quyền.

## Ghi chú

- Mỗi request kiểm quyền tốn một lần đọc Redis. Nếu thành điểm nghẽn: bọc `PermissionProvider` bằng cache trong bộ nhớ (L1) mà không phải đổi mã nghiệp vụ.
- Có một khoảng đua nhỏ: nếu một lần tính quyền cũ đang chạy đúng lúc quyền đổi và cache bị xóa, bản cũ có thể được ghi lại. TTL giới hạn độ trễ đó.
