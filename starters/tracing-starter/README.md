# tracing-starter

Vận hành cho mọi service: actuator ở cổng quản trị riêng, probe Kubernetes, metric Prometheus, trace OpenTelemetry,
tắt êm khi pod dừng.

## Dùng

```xml
<dependency>
    <groupId>com.htv</groupId>
    <artifactId>tracing-starter</artifactId>
</dependency>
```

Đã có sẵn trong mọi starter nhóm. Không cần khai báo gì thêm.

## Cung cấp

| Thành phần | Việc |
|---|---|
| Spring Boot Actuator | `health`, `info`, `prometheus` ở **cổng 8081**, tách khỏi cổng API |
| Probe Kubernetes | `/actuator/health/liveness`, `/actuator/health/readiness` |
| Micrometer + Prometheus | `/actuator/prometheus`, mọi metric có nhãn `application=<spring.application.name>` |
| OpenTelemetry | Trace theo W3C `traceparent`; traceId vào MDC và vào `BaseResponse` |
| Tắt êm | `server.shutdown=graceful`, chờ tối đa 30s |

Mọi giá trị trên là mặc định ở mức ưu tiên thấp nhất. `application.yml` của service luôn thắng.

## Cổng

| Cổng | Dùng cho | Đưa ra ngoài? |
|---|---|---|
| `8080` (`server.port`) | API | Có (qua gateway) |
| `8081` (`htv.tracing.management-port`) | Probe, Prometheus | **Không** |
| `9090` (`htv.grpc.server.port`) | gRPC nội bộ | Không |

`security-starter` cho qua mọi request trên cổng quản trị mà không cần JWT. Trên cổng API, `/actuator/*` không có (bị chặn 401 trước khi tới 404).

## Kubernetes

```yaml
ports:
  - { name: http,    containerPort: 8080 }
  - { name: mgmt,    containerPort: 8081 }
  - { name: grpc,    containerPort: 9090 }
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: mgmt }
  periodSeconds: 10
  failureThreshold: 3
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: mgmt }
  periodSeconds: 5
  failureThreshold: 3
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: mgmt }
  periodSeconds: 5
  failureThreshold: 30          # cho tối đa 150s để khởi động (Flyway, kết nối DB)
terminationGracePeriodSeconds: 40  # lớn hơn 30s tắt êm
```

Prometheus scrape cổng `mgmt`, đường dẫn `/actuator/prometheus`. Service Kubernetes công khai chỉ mở cổng `http`.

## Cấu hình

| Khóa | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `htv.tracing.enabled` | boolean | `true` | Tắt các mặc định dưới đây (dependency vẫn còn) |
| `htv.tracing.management-port` | int | `8081` | Cổng quản trị |
| `management.endpoints.web.exposure.include` | | `health,info,prometheus` | Endpoint được mở |
| `management.endpoint.health.show-details` / `show-components` | | `never` | Không lộ tên DB, phiên bản, lỗi kết nối |
| `management.tracing.sampling.probability` | double | `1.0`, `0.1` ở `prod` | Tỉ lệ lấy mẫu trace |
| `management.opentelemetry.tracing.export.otlp.endpoint` | URL | (trống) | Nơi gửi trace (Collector). Đặt theo môi trường |
| `server.shutdown` / `spring.lifecycle.timeout-per-shutdown-phase` | | `graceful` / `30s` | Tắt êm |

## Ghi chú

- **Bảo mật:** cổng 8081 không đòi đăng nhập. Không được đưa nó ra Ingress hay Service công khai. Nên chặn bằng NetworkPolicy, chỉ cho Prometheus và kubelet.
- **Chạy nhiều service trên một máy dev:** mỗi service cần cổng quản trị khác nhau. Đặt `MANAGEMENT_SERVER_PORT` (hoặc `htv.tracing.management-port`) riêng cho từng service.
- Readiness tự chuyển `OUT_OF_SERVICE` khi tắt, nên Kubernetes ngừng gửi request mới trước khi pod dừng.
- Readiness hiện chưa gồm Redis/DB. Muốn pod không nhận request khi DB chết thì thêm `management.endpoint.health.group.readiness.include=readinessState,db,redis`. Cân nhắc: DB chết thì mọi pod cùng ra khỏi vòng.
- Chưa kiểm: gửi trace thật tới OpenTelemetry Collector (chưa có Collector trong dự án).
