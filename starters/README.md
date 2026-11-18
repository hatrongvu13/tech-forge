# Kho starter

Một starter = một công nghệ. Service ghép các starter cần dùng; không có "platform" lớn.

Thiết kế đầy đủ: `docs/thiet-ke-platform-starter.md`.

## Ba tầng

| Tầng | Chứa | Luật |
|---|---|---|
| 1. `shared/*`, `contracts/*` | Code thuần Java | Không biết tầng 2 và 3 |
| 2. Starter đơn (thư mục này) | Auto-configuration | Chỉ phụ thuộc tầng 1, Spring, thư viện ngoài. **Không** phụ thuộc starter đơn khác (trừ `core-starter`). **Không** phụ thuộc `*-service`, `gateway` |
| 3. Starter nhóm (`bundles/`) | Không có code | Chỉ liệt kê starter đơn |

Luật tầng 2 được Maven Enforcer kiểm trong `starters/pom.xml` (quy tắc `enforce-starter-rules`). Vi phạm thì build lỗi.

Luật tầng **trong** mỗi service (`interfaces -> application -> domain`, `domain` không import Spring, service không import mã của nhau) được ArchUnit kiểm: `shared-test/.../ArchitectureRules`. Service mới thêm một `ArchitectureTest` gọi `ArchitectureRules.check("com.htv.<tên>")` và thêm gói vào `SERVICE_PACKAGES`.

## Starter hiện có

| Starter | Trạng thái |
|---|---|
| `jpa-starter`, `core-starter`, `web-starter`, `grpc-starter`, `cached-starter`, `security-starter`, `feign-starter`, `tracing-starter` | Có README, có test |
| Starter nhóm (`bundles/`) | Có README, có test service rỗng. Xem `bundles/README.md` |

## Thêm một starter mới

1. Tạo `starters/<tên>-starter/` với `pom.xml` có `parent` là `com.htv:starters` (`relativePath` `../pom.xml`).
2. Thêm `<module>` vào `starters/pom.xml` và mục quản lý version vào `platform/bom/pom.xml`.
3. Viết lớp `...AutoConfiguration` và đăng ký trong `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
4. Làm đúng 7 quy tắc dưới đây.
5. Viết `README.md` theo mẫu bên dưới.

## 7 quy tắc cho mọi starter đơn

1. Có `README.md`: cung cấp gì, bảng cấu hình (khóa, kiểu, mặc định), cách ghi đè, ví dụ tối thiểu.
2. Thuộc tính khai báo bằng `@ConfigurationProperties` có metadata (IDE gợi ý được).
3. Mọi bean dùng `@ConditionalOnMissingBean`, để service ghi đè bằng bean cùng kiểu.
4. Tắt được bằng `htv.<tên>.enabled=false`.
5. Mặc định cấu hình đặt ở mức ưu tiên thấp nhất (`EnvironmentPostProcessor`); `application.yml` của service luôn thắng.
6. Đăng ký qua `AutoConfiguration.imports`.
7. Có test khởi động riêng bằng `ApplicationContextRunner`: bean có mặt, ghi đè được, tắt được. Mẫu: `jpa-starter/src/test/java/com/htv/starter/jpa/JpaAutoConfigurationTest.java`.

## Mẫu README

```markdown
# <tên>-starter
<một câu: làm gì>

## Dùng
<dependency> ... </dependency>

## Cung cấp
| Thành phần | Việc |

## Cấu hình
| Khóa | Kiểu | Mặc định | Ý nghĩa |
| `htv.<tên>.enabled` | boolean | `true` | tắt toàn bộ starter |

## Ghi đè
<bean nào ghi đè được và cách làm>

## Ghi chú
<giới hạn, rủi ro>
```
