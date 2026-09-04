# Family Expense Manager

Hệ thống quản lý chi tiêu gia đình, dùng thực tế hàng ngày, xây dựng theo kiến trúc microservices thật (không phải demo tối giản).

**Stack:** Spring Boot 3.3.5 + Doma 2 (không dùng JPA/Hibernate) · React (Vite) · Oracle SQL · Docker Compose · Kafka · Redis · Eureka · Spring Cloud Gateway · springdoc-openapi (Swagger UI).

> File này chỉ mô tả **cấu trúc project và lộ trình gen code**. Chưa có code nghiệp vụ nào được tạo — các thư mục source (`.java`, `.jsx`) hiện đang rỗng (giữ chỗ bằng `.gitkeep`), chỉ có sẵn `pom.xml` / `application.yml` / `package.json` / `docker-compose.yml` để định hình cấu trúc.

## Kiến trúc

```
                        ┌───────────────┐
                        │   frontend     │  React + Vite
                        │  (port 5173)   │
                        └───────┬────────┘
                                │
                        ┌───────▼────────┐
                        │  api-gateway    │  Spring Cloud Gateway
                        │  (port 8080)    │  routes + JWT pre-filter
                        └───────┬────────┘
              ┌─────────────────┼─────────────────┐
              │                 │                 │
      ┌───────▼──────┐  ┌───────▼───────┐  ┌──────▼─────────────┐
      │ auth-service  │  │expense-service │  │notification-service│
      │ (port 8081)   │  │ (port 8082)    │  │ (port 8083)        │
      │ schema        │  │ schema         │  │ schema             │
      │ FEM_AUTH      │  │ FEM_EXPENSE    │  │ FEM_NOTIFY         │
      └───────┬───────┘  └──┬─────┬──────┘  └──────────▲─────────┘
              │             │     │                     │
              │        Redis│     │Kafka                │
              │        cache│     │(expense-events)──────┘
              │             │     │
      ┌───────▼─────────────▼─────▼──────┐
      │           Oracle DB               │
      └────────────────────────────────────┘

      Tất cả service đăng ký với eureka-server (port 8761)
```

Xem thêm mô tả chi tiết từng thành phần trong `docs/` (chưa tạo — có thể copy nội dung phần dưới vào đó nếu muốn tách riêng).

## Cấu trúc thư mục

```
family-expense-manager/
├── backend/
│   ├── pom.xml                      # Maven reactor cha (Spring Boot 3.3.5, Spring Cloud 2023.0.3, Doma 2.61.0, ojdbc11, Java 21)
│   ├── common/                      # JwtUtil, DTOs dùng chung, exception base, OpenApiConfig — dùng chung cho mọi service
│   ├── eureka-server/               # Service registry
│   ├── api-gateway/                 # Gateway + JWT pre-filter
│   ├── auth-service/                # Đăng ký/đăng nhập/JWT — schema FEM_AUTH
│   ├── expense-service/             # Ví/danh mục/giao dịch/ngân sách — schema FEM_EXPENSE
│   └── notification-service/        # Xử lý thông báo qua Kafka — schema FEM_NOTIFY
├── frontend/                        # React + Vite SPA
└── infra/
    ├── docker-compose.yml           # 9 container: oracle-db, kafka, redis, eureka-server, api-gateway, 3 service, frontend
    ├── oracle/init/                 # Script tạo schema/user Oracle (chạy khi container Oracle khởi tạo lần đầu)
    └── .env.example                 # copy thành .env trước khi docker compose up
```

Mỗi service backend đi theo layout chuẩn của Doma:
```
<service>/src/main/java/.../domain/entity/*.java      # @Entity
<service>/src/main/java/.../dao/*.java                 # @Dao interface
<service>/src/main/resources/META-INF/com/family/expensemanager/<service>/dao/<DaoName>/<method>.sql
```

## Mô hình dữ liệu (tóm tắt)

**FEM_AUTH** — `FAMILIES(id, name, created_at)` · `USERS(id, family_id, email, password_hash, display_name, role, active)` · `REFRESH_TOKENS(id, user_id, token_hash, expires_at, revoked)`

**FEM_EXPENSE** — `WALLETS(id, family_id, name, currency, initial_balance)` · `CATEGORIES(id, family_id, name, type, icon, color)` · `TRANSACTIONS(id, wallet_id, category_id, family_id, user_id, type, amount, occurred_at, note)` · `BUDGETS(id, family_id, category_id, period_month, limit_amount)`

**FEM_NOTIFY** — `NOTIFICATIONS(id, family_id, user_id, type, title, message, payload_json, is_read)`

## Hợp đồng Kafka

Topic `expense-events`, key = `familyId`, phân biệt bằng field `eventType`.

- `EXPENSE_CREATED` — publish sau mỗi giao dịch được commit.
- `BUDGET_EXCEEDED` — chỉ publish khi tổng chi trong tháng của 1 category **vượt ngưỡng lần đầu** (không lặp lại ở các giao dịch vượt ngân sách tiếp theo trong cùng tháng).

`notification-service` chỉ xử lý `BUDGET_EXCEEDED` ở giai đoạn này.

## Redis

Cache 2 endpoint tổng hợp tốn chi phí: `GET /api/expenses/summary` và `GET /api/expenses/reports/category`, key `expense:summary:{familyId}:{yearMonth}` / `expense:report:category:{familyId}:{yearMonth}`, TTL 10 phút, evict chính xác khi có giao dịch mới trong tháng đó.

## API Docs (Swagger)

`auth-service`, `expense-service`, `notification-service` dùng springdoc-openapi (`spring-boot-starter-web` → webmvc-ui). Mỗi service tự phục vụ:
- Swagger UI: `http://localhost:<port>/swagger-ui.html`
- OpenAPI JSON: `http://localhost:<port>/v3/api-docs`

`OpenApiConfig` (trong `common`) set title = `spring.application.name` và đăng ký scheme `bearerAuth` (JWT) cho nút Authorize — mỗi service cần `@Import(OpenApiConfig.class)` trên class `@SpringBootApplication` vì `common` nằm ngoài package gốc của từng service nên không được component-scan tự động.

`api-gateway` có sẵn dependency `springdoc-openapi-starter-webflux-ui` để gộp cả 3 Swagger UI vào một trang tại `:8080/swagger-ui.html`; route và cấu hình `springdoc.swagger-ui.urls` tương ứng đang comment sẵn trong `api-gateway/application.yml`, bỏ comment cùng lúc với 3 route chính ở Ngày 5.

Khi viết `SecurityConfig` cho từng service (Ngày 3-4, 6-8, 9-10), nhớ `permitAll()` cho `/v3/api-docs/**` và `/swagger-ui/**` (`/swagger-ui.html`), nếu không Spring Security sẽ chặn luôn Swagger UI.

## Bảo mật

JWT được xác thực **độc lập ở từng service** (qua `common`), không chỉ tin tưởng header do gateway set — gateway cũng xác thực để fail nhanh nhưng vẫn forward nguyên `Authorization` header xuống service. Access token 15 phút, refresh token 7 ngày. Chỉ `api-gateway`, `eureka-server`, `frontend` mở port ra ngoài; 3 service còn lại chỉ nằm trong mạng nội bộ Docker.

## Phạm vi KHÔNG làm ở giai đoạn này

CI/CD, Kubernetes, observability (Prometheus/Grafana), test suite đầy đủ, i18n, luồng mời thành viên qua invite-code, xác minh email/quên mật khẩu, rate-limiting, service mesh, admin UI riêng, tách compose dev/prod.

---

## Lộ trình gen code hàng ngày

Mỗi ngày làm đúng 1 mục, có bước verify cụ thể trước khi qua ngày tiếp theo. Thư mục/file liên quan đã được scaffold sẵn (rỗng), chỉ cần điền code vào.

### Ngày 1 — Nền tảng
- [ ] Kiểm tra `backend/pom.xml` (reactor cha) build được: `mvn -f backend/pom.xml validate`
- [ ] Viết `EurekaServerApplication.java` trong `backend/eureka-server/src/main/java/com/family/expensemanager/eureka/` với `@EnableEurekaServer`
- **Verify:** `mvn -f backend/eureka-server spring-boot:run` rồi mở `http://localhost:8761` — thấy dashboard registry rỗng.

### Ngày 2 — Thư viện dùng chung (`common`)
- [ ] `JwtUtil` (sign/verify/parse claims), `JwtAuthenticationFilter`, `ApiResponse`/`ErrorResponse` DTO, exception base — tất cả trong `backend/common/src/main/java/com/family/expensemanager/common/`
- [ ] `OpenApiConfig` đã có sẵn trong `common/.../config/` — mỗi service nhớ `@Import(OpenApiConfig.class)` khi viết class `@SpringBootApplication`
- **Verify:** `mvn -f backend/common install` chạy thành công, jar được cài vào local repo để các module khác dùng.

### Ngày 3-4 — Auth service
- [ ] `@Entity` cho `FAMILIES`/`USERS`/`REFRESH_TOKENS` trong `auth-service/.../domain/entity/`
- [ ] `@Dao` tương ứng trong `auth-service/.../dao/` + file `.sql` trong `auth-service/src/main/resources/META-INF/.../dao/`
- [ ] `AuthController` (`POST /register`, `/login`, `/refresh`), `AuthService`, cấu hình Spring Security dùng `JwtAuthenticationFilter` từ `common`
- [ ] Chạy script tạo schema Oracle trong `infra/oracle/init/` (tạo user `FEM_AUTH` + bảng)
- **Verify:** `curl -X POST localhost:8081/register` → `curl -X POST localhost:8081/login` → nhận JWT, decode kiểm tra claims (`sub`, `familyId`, `role`).

### Ngày 5 — API Gateway
- [ ] Bỏ comment 3 route trong `api-gateway/src/main/resources/application.yml`
- [ ] Viết `JwtGatewayFilter` trong `api-gateway/.../gateway/filter/` (dùng `common`), bỏ qua các route public (`/api/auth/register`, `/login`, `/refresh`)
- [ ] Bỏ comment 3 route `*-docs` và `springdoc.swagger-ui.urls` trong cùng file để gộp Swagger UI của 3 service vào `:8080/swagger-ui.html`
- **Verify:** gọi qua `:8080/api/auth/login` hoạt động; gọi 1 route cần bảo vệ không có token → 401; có token hợp lệ → forward thành công; mở `:8080/swagger-ui.html` thấy đủ 3 nhóm API.

### Ngày 6-8 — Expense service
- [ ] `@Entity`/`@Dao` cho `WALLETS`/`CATEGORIES`/`TRANSACTIONS`/`BUDGETS` + file `.sql`
- [ ] CRUD đầy đủ qua `TransactionController`/`TransactionService`, logic kiểm tra vượt ngân sách (so sánh tổng chi trước/sau giao dịch)
- [ ] Script tạo schema `FEM_EXPENSE` trong `infra/oracle/init/`
- **Verify:** CRUD qua curl trực tiếp `:8082` và qua gateway `:8080/api/expenses/...`.

### Ngày 9-10 — Luồng Kafka
- [ ] `expense-service`: publish `EXPENSE_CREATED`/`BUDGET_EXCEEDED` vào topic `expense-events` (dùng class trong `expense-service/.../messaging/`) — publish sau khi transaction DB đã commit
- [ ] `notification-service`: `@Entity`/`@Dao` cho `NOTIFICATIONS`, `@KafkaListener` trong `notification-service/.../messaging/`, `GET /notifications`
- [ ] Script tạo schema `FEM_NOTIFY`
- **Verify:** tạo giao dịch vượt ngân sách → kiểm tra `notification-service` lưu được bản ghi → `curl :8080/api/notifications` trả về đúng thông báo.

### Ngày 11 — Redis cache
- [ ] `@Cacheable` trên endpoint summary/report trong `expense-service`, `@CacheEvict` khi tạo/sửa/xoá giao dịch
- **Verify:** gọi lại endpoint summary 2 lần liên tiếp, lần 2 nhanh hơn rõ rệt (cache hit); tạo giao dịch mới → gọi lại → số liệu cập nhật đúng (cache đã evict).

### Ngày 12 — Docker Compose
- [ ] Viết `Dockerfile` cho từng service Java (`backend/<service>/Dockerfile`, build multi-stage: `mvn package` → copy jar → `java -jar`) và cho `frontend` (build Vite → serve bằng nginx)
- [ ] `cp infra/.env.example infra/.env` rồi điền giá trị thật
- **Verify:** `docker compose -f infra/docker-compose.yml --env-file infra/.env up --build`, chạy lại toàn bộ curl check của ngày 3-10 nhưng chỉ gọi qua gateway (`:8080`) để xác nhận service discovery hoạt động đúng trong container, không phải chỉ chạy local.

### Ngày 13+ — Frontend
- [ ] `src/main.jsx`, `src/App.jsx`, router (`react-router-dom`)
- [ ] `src/api/client.js` — 1 axios instance duy nhất, base URL = gateway, interceptor gắn `Authorization`, xử lý 401 refresh
- [ ] Từng trang trong `src/pages/`: `Login`, `Register`, `Dashboard`, `Transactions`, `Categories`, `Budgets`, `Notifications`, `Wallets` — làm lần lượt, mỗi trang xong thì thử thao tác thật trên UI trước khi qua trang tiếp theo
- **Verify:** đăng ký gia đình mới → đăng nhập → thêm ví/danh mục/giao dịch → thấy dashboard cập nhật → tạo giao dịch vượt ngân sách → thấy thông báo xuất hiện.

---

Sau khi hoàn thành lộ trình trên, hệ thống chạy được đầy đủ vòng đời: đăng ký gia đình → ghi nhận thu/chi → theo dõi ngân sách → nhận cảnh báo khi vượt ngân sách → xem báo cáo tổng hợp.
