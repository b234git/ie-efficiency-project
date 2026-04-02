# Kế Hoạch Migrate SQL Server sang PostgreSQL

## Tổng Quan

| Thông tin | Chi tiết |
|-----------|----------|
| **Dự án** | IE-Eff (Shoe Factory Efficiency Management) |
| **Database hiện tại** | Microsoft SQL Server |
| **Database đích** | PostgreSQL |
| **ORM** | Spring Data JPA / Hibernate |
| **Migration tool** | Flyway |
| **Số entity** | 8 |
| **Số repository** | 7 |
| **Native SQL** | Không có (thuần JPQL) - giúp việc migrate dễ hơn |

---

## Tiến Độ Tổng Thể

- [ x ] **Phase 1**: Chuẩn bị & Setup PostgreSQL
- [ x ] **Phase 2**: Cập nhật Dependencies (Maven)
- [ ] **Phase 3**: Cập nhật Configuration
- [ ] **Phase 4**: Migrate Schema (Flyway)
- [ ] **Phase 5**: Cập nhật Entity classes
- [ ] **Phase 6**: Kiểm tra Repository & Service
- [ ] **Phase 7**: Migrate dữ liệu
- [ ] **Phase 8**: Testing
- [ ] **Phase 9**: Deploy & Chuyển đổi Production

---

## Phase 1: Chuẩn Bị & Setup PostgreSQL

- [ ] Cài đặt PostgreSQL (phiên bản 15+ khuyến nghị)
- [ ] Tạo database mới: `shoe_eff_db`
- [ ] Tạo user/role cho ứng dụng
- [ ] Kiểm tra kết nối từ máy dev tới PostgreSQL
- [ ] Backup toàn bộ dữ liệu SQL Server hiện tại

```sql
-- Chạy trên PostgreSQL
CREATE DATABASE shoe_eff_db;
CREATE USER shoe_app WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE shoe_eff_db TO shoe_app;
```

---

## Phase 2: Cập Nhật Dependencies (Maven)

File: `pom.xml`

- [ ] Xóa dependency `mssql-jdbc`
- [ ] Xóa dependency `flyway-sqlserver`
- [ ] Thêm dependency `postgresql` driver
- [ ] Thêm dependency `flyway-database-postgresql`

### Cần xóa:
```xml
<!-- XÓA -->
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-sqlserver</artifactId>
</dependency>
```

### Cần thêm:
```xml
<!-- THÊM -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

---

## Phase 3: Cập Nhật Configuration

### 3.1 application.properties (Dev)

File: `src/main/resources/application.properties`

- [ ] Cập nhật `spring.datasource.url`
- [ ] Cập nhật `spring.datasource.driver-class-name`
- [ ] Cập nhật Hibernate dialect (nếu có)

```properties
# TỪ (SQL Server):
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=ShoeEffDB;encrypt=true;trustServerCertificate=true
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# SANG (PostgreSQL):
spring.datasource.url=jdbc:postgresql://localhost:5432/shoe_eff_db
spring.datasource.driver-class-name=org.postgresql.Driver
```

### 3.2 application-prod.properties (Production)

File: `src/main/resources/application-prod.properties`

- [ ] Cập nhật connection URL format cho PostgreSQL
- [ ] Giữ nguyên HikariCP settings (tương thích)

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:shoe_eff_db}
spring.datasource.driver-class-name=org.postgresql.Driver
```

### 3.3 application-test.properties

- [ ] Kiểm tra - H2 test config có thể giữ nguyên (không ảnh hưởng)

---

## Phase 4: Migrate Schema (Flyway)

File: `src/main/resources/db/migration/V1__init_schema.sql`

Cần viết lại file migration cho PostgreSQL syntax.

### Bảng ánh xạ kiểu dữ liệu:

| SQL Server | PostgreSQL | Ghi chú |
|------------|------------|---------|
| `BIGINT IDENTITY(1,1)` | `BIGSERIAL` | Auto-increment |
| `DATETIME2(6)` | `TIMESTAMP(6)` | Microsecond precision |
| `NVARCHAR(MAX)` | `TEXT` | Unbounded text |
| `NVARCHAR(n)` | `VARCHAR(n)` | Unicode mặc định trong PG |
| `FLOAT(53)` | `DOUBLE PRECISION` | 64-bit float |
| `BIT` | `BOOLEAN` | True/False |
| `GETDATE()` | `NOW()` | Current timestamp |

### Checklist từng bảng:

- [ ] Bảng `users`
  - [ ] Chuyển `BIGINT IDENTITY(1,1)` → `BIGSERIAL`
  - [ ] Chuyển `NVARCHAR` → `VARCHAR`
- [ ] Bảng `daily_production`
  - [ ] Chuyển `BIGINT IDENTITY(1,1)` → `BIGSERIAL`
  - [ ] Chuyển `DATETIME2(6)` → `TIMESTAMP(6)`
  - [ ] Chuyển `FLOAT(53)` → `DOUBLE PRECISION`
- [ ] Bảng `daily_production_detail`
  - [ ] Chuyển `BIGINT IDENTITY(1,1)` → `BIGSERIAL`
  - [ ] Chuyển `FLOAT(53)` → `DOUBLE PRECISION`
- [ ] Bảng `master_db`
  - [ ] Chuyển `BIGINT IDENTITY(1,1)` → `BIGSERIAL`
  - [ ] Chuyển `FLOAT(53)` → `DOUBLE PRECISION`
  - [ ] Chuyển `NVARCHAR` → `VARCHAR`
- [ ] Bảng `split_entry`
  - [ ] Chuyển `BIGINT IDENTITY(1,1)` → `BIGSERIAL`
  - [ ] Chuyển `FLOAT(53)` → `DOUBLE PRECISION`
  - [ ] Chuyển `NVARCHAR` → `VARCHAR`
- [ ] Bảng `split_entry_detail`
  - [ ] Chuyển `BIGINT IDENTITY(1,1)` → `BIGSERIAL`
  - [ ] Chuyển `FLOAT(53)` → `DOUBLE PRECISION`
- [ ] Bảng `notification`
  - [ ] Chuyển `BIGINT IDENTITY(1,1)` → `BIGSERIAL`
  - [ ] Chuyển `NVARCHAR(MAX)` → `TEXT`
  - [ ] Chuyển `BIT` → `BOOLEAN`
  - [ ] Chuyển `GETDATE()` → `NOW()`
- [ ] Bảng `system_log`
  - [ ] Chuyển `BIGINT IDENTITY(1,1)` → `BIGSERIAL`
  - [ ] Chuyển `NVARCHAR(MAX)` → `TEXT`
  - [ ] Chuyển `DATETIME2(6)` → `TIMESTAMP(6)`
- [ ] Kiểm tra lại tất cả FOREIGN KEY constraints
- [ ] Kiểm tra lại tất cả INDEX definitions
- [ ] Kiểm tra lại UNIQUE constraints

> **Lưu ý**: Tạo file migration mới `V2__migrate_to_postgresql.sql` hoặc viết lại `V1` nếu đây là fresh install. Nếu viết lại V1, cần xóa bảng `flyway_schema_history` trên database mới.

---

## Phase 5: Cập Nhật Entity Classes

### Thay đổi `GenerationType`

Hiện tại tất cả entity dùng `GenerationType.IDENTITY` (SQL Server IDENTITY columns).
PostgreSQL dùng `BIGSERIAL` tương thích với `GenerationType.IDENTITY`, nên **không cần đổi**.

- [ ] Kiểm tra `User.java` — `@GeneratedValue(strategy = GenerationType.IDENTITY)` → **Giữ nguyên**
- [ ] Kiểm tra `DailyProduction.java` → **Giữ nguyên**
- [ ] Kiểm tra `DailyProductionDetail.java` → **Giữ nguyên**
- [ ] Kiểm tra `MasterDb.java` → **Giữ nguyên**
- [ ] Kiểm tra `SplitEntry.java` → **Giữ nguyên**
- [ ] Kiểm tra `SplitEntryDetail.java` → **Giữ nguyên**
- [ ] Kiểm tra `Notification.java` → **Giữ nguyên**
- [ ] Kiểm tra `SystemLog.java` → **Giữ nguyên**

### Thay đổi `columnDefinition`

- [ ] `Notification.java` — đổi `columnDefinition = "NVARCHAR(MAX)"` → `columnDefinition = "TEXT"`
- [ ] `SystemLog.java` — đổi `columnDefinition = "NVARCHAR(MAX)"` → `columnDefinition = "TEXT"`

---

## Phase 6: Kiểm Tra Repository & Service

Tất cả repository hiện dùng JPQL (không có native SQL), nên phần lớn tương thích.

### Repository checklist:

- [ ] `UserRepository.java` — Kiểm tra query compatibility
- [ ] `DailyProductionRepository.java` — Kiểm tra LEFT JOIN FETCH queries
- [ ] `DailyProductionDetailRepository.java` — Basic CRUD, không cần thay đổi
- [ ] `MasterDbRepository.java` — Kiểm tra month-aware queries
- [ ] `SplitEntryRepository.java` — Kiểm tra date/section/line queries
- [ ] `NotificationRepository.java` — Kiểm tra role-based queries
- [ ] `SystemLogRepository.java` — Kiểm tra timestamp queries

### Service checklist:

- [ ] `DataRetentionService.java` — Kiểm tra delete queries hoạt động đúng
- [ ] `ProductionService.java` — Kiểm tra batch operations
- [ ] `EfficiencyCalculatorService.java` — Kiểm tra calculations
- [ ] Các service khác — Kiểm tra tổng quát

---

## Phase 7: Migrate Dữ Liệu

> **Chỉ cần nếu có dữ liệu production cần giữ lại**

- [ ] Export dữ liệu từ SQL Server (CSV hoặc SQL dump)
- [ ] Chuyển đổi format dữ liệu nếu cần (datetime, boolean)
- [ ] Import vào PostgreSQL
- [ ] Verify số lượng records khớp
- [ ] Verify dữ liệu chính xác (spot check)

### Công cụ gợi ý:
- **pgLoader** — tự động migrate từ SQL Server sang PostgreSQL
- **DBeaver** — export/import thủ công
- **Spring Batch** — viết job migrate nếu cần transform dữ liệu

### Thứ tự import (theo foreign key dependencies):

1. [ ] `users`
2. [ ] `master_db`
3. [ ] `daily_production`
4. [ ] `daily_production_detail`
5. [ ] `split_entry`
6. [ ] `split_entry_detail`
7. [ ] `notification`
8. [ ] `system_log`

- [ ] Reset sequences sau khi import (`SELECT setval('table_id_seq', (SELECT MAX(id) FROM table))`)

---

## Phase 8: Testing

### Unit Tests:

- [ ] Chạy toàn bộ test suite hiện có (H2) — phải pass hết
- [ ] Thêm integration test profile dùng PostgreSQL (Testcontainers hoặc local PG)

### Manual Testing:

- [ ] Đăng nhập / đăng xuất
- [ ] CRUD DailyProduction (tạo, sửa, xóa)
- [ ] CRUD DailyProductionDetail
- [ ] CRUD MasterDb
- [ ] Split Entry workflow
- [ ] Export Excel
- [ ] Import Excel
- [ ] Notification system
- [ ] System logging
- [ ] Data retention scheduler
- [ ] Report generation
- [ ] Pagination hoạt động đúng
- [ ] Search/filter hoạt động đúng

### Performance Testing:

- [ ] So sánh response time trước/sau migrate
- [ ] Kiểm tra batch insert performance
- [ ] Kiểm tra query performance với data lớn

---

## Phase 9: Deploy & Chuyển Đổi Production

- [ ] Setup PostgreSQL trên server production
- [ ] Cấu hình firewall/network cho PostgreSQL port (5432)
- [ ] Migrate dữ liệu production
- [ ] Deploy ứng dụng với config mới
- [ ] Smoke test trên production
- [ ] Monitor logs/errors trong 24-48h đầu
- [ ] Giữ SQL Server backup ít nhất 30 ngày để rollback nếu cần
- [ ] Xác nhận hoàn tất, tắt SQL Server instance

---

## Lưu Ý Quan Trọng

### Điểm thuận lợi:
- **Không có native SQL** — tất cả query đều là JPQL, giảm thiểu rủi ro khi migrate
- **Không có stored procedures** — không cần chuyển đổi logic phức tạp
- **Flyway đã có sẵn** — chỉ cần viết lại migration script
- **`GenerationType.IDENTITY`** — tương thích với cả SQL Server và PostgreSQL

### Điểm cần chú ý:
- **`NVARCHAR(MAX)`** → `TEXT` — cần sửa trong entity và migration
- **`BIT`** → `BOOLEAN` — Hibernate tự xử lý, nhưng migration script cần đúng
- **`GETDATE()`** → `NOW()` — chỉ ảnh hưởng migration script
- **Case sensitivity** — PostgreSQL mặc định lowercase cho tên bảng/cột (cần kiểm tra nếu có đặt tên mixed-case)
- **Sequence reset** — sau khi import dữ liệu cũ cần reset auto-increment sequences

---

## Thời Gian Ước Tính Theo Phase

| Phase | Độ phức tạp | Ghi chú |
|-------|-------------|---------|
| Phase 1 | Thấp | Setup cơ bản |
| Phase 2 | Thấp | Thay đổi 4 dòng trong pom.xml |
| Phase 3 | Thấp | Thay đổi vài dòng config |
| Phase 4 | Trung bình | Viết lại schema migration |
| Phase 5 | Thấp | Chỉ sửa 2 entity (columnDefinition) |
| Phase 6 | Thấp | Chủ yếu verify, JPQL tương thích |
| Phase 7 | Trung bình-Cao | Phụ thuộc lượng dữ liệu |
| Phase 8 | Trung bình | Test toàn diện |
| Phase 9 | Trung bình | Cần cẩn thận với production |
