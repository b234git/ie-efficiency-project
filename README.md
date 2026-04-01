# IE-Eff — Shoe Factory Efficiency Management

Hệ thống quản lý hiệu suất sản xuất giày (Industrial Efficiency). Theo dõi năng suất, tính toán KPI (PPH, Efficiency), và quản lý dữ liệu master cho từng chuyền sản xuất.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4.0.3, Java 17 |
| Frontend | Thymeleaf, HTML/CSS/JS |
| Database | SQL Server (SQL Server 2019+) |
| Security | Spring Security (BCrypt, Role-based) |
| ORM | Spring Data JPA / Hibernate 6 |
| Build | Maven |
| DB Migration | Flyway |
| Excel | Apache POI 5.2.5 |

## Prerequisites

- Java 17+
- Maven 3.8+
- SQL Server 2019+ (hoặc SQL Server Express)

## Getting Started (Dev)

### 1. Clone repo
```bash
git clone <repo-url>
cd IE-Eff
```

### 2. Tạo database
```sql
CREATE DATABASE ShoeEffDB;
```

### 3. Cấu hình kết nối DB

Mặc định dev dùng `sa / 1` trên `localhost:1433`. Nếu khác, set env vars:
```bash
# Windows
set DB_USERNAME=sa
set DB_PASSWORD=your_password
```
Hoặc chỉnh trực tiếp trong `src/main/resources/application.properties`.

### 4. Chạy app
```bash
mvn spring-boot:run
```

App chạy tại: `http://localhost:8080`

Flyway sẽ tự động tạo schema khi khởi động lần đầu trên database mới.

## Default Credentials (Dev Only)

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin` | ADMIN |
| `manager` | `manager` | MANAGER |

> Các tài khoản này chỉ được tạo tự động ở profile `dev`. **Không dùng trong production.**

## Environment Variables

| Variable | Required | Default (dev) | Description |
|----------|----------|---------------|-------------|
| `DB_URL` | Prod only | `jdbc:sqlserver://localhost:1433;databaseName=ShoeEffDB;encrypt=true;trustServerCertificate=true` | JDBC connection string |
| `DB_USERNAME` | Prod only | `sa` | Database username |
| `DB_PASSWORD` | Prod only | `1` | Database password |
| `PORT` | No | `8080` | Server port |

Xem thêm `.env.example` để biết cú pháp đầy đủ.

## Chạy Production

```bash
mvn package -DskipTests
java -jar target/management-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_URL="jdbc:sqlserver://your-server:1433;databaseName=ShoeEffDB;encrypt=true;trustServerCertificate=true" \
  --DB_USERNAME=your_user \
  --DB_PASSWORD=your_password
```

## Project Structure

```
src/main/java/thienloc/manage/
├── controller/     # Spring MVC controllers (11)
├── service/        # Business logic (15)
├── entity/         # JPA entities (8)
├── repository/     # Spring Data JPA repositories (8)
├── dto/            # Data transfer objects (7)
├── security/       # Spring Security config
├── exception/      # Exception classes & global handler
├── scheduler/      # Scheduled tasks (data retention)
└── config/         # App configuration

src/main/resources/
├── templates/      # Thymeleaf HTML templates (18)
├── static/         # CSS, JS assets
├── db/migration/   # Flyway SQL migration scripts
└── application*.properties
```

## Sections / Chuyền sản xuất

Hệ thống hỗ trợ 8 section:
`SEW`, `BUFFING 1ST`, `BUFFING 2ND`, `STOCKFIT UV`, `STOCKFIT 1ST`, `STOCKFIT 2ND`, `ASSEMBLY BIG`, `ASSEMBLY SMALL`

## Chạy Tests

```bash
mvn test
```

Tests dùng H2 in-memory database, không cần SQL Server.
