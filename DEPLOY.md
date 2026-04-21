# Hướng dẫn Triển khai IE-Eff (Nội bộ)

Ứng dụng quản lý hiệu suất sản xuất — Spring Boot 4.0.3 / Java 17 / **PostgreSQL 15+**.
Dùng cho mạng nội bộ (LAN), không yêu cầu kết nối internet.

---

## Cài đặt nhanh (All-in-one package)

Thư mục `deploy/` chứa script tự động hóa toàn bộ quá trình cài đặt.

### Chuẩn bị trước

| # | Việc cần làm | Link tải |
|---|-------------|----------|
| 1 | Cài **Java 17** (Eclipse Temurin), tick "Set JAVA_HOME" | https://adoptium.net/ |
| 2 | Cài **PostgreSQL 15+**, ghi nhớ mật khẩu `postgres` | https://www.postgresql.org/download/windows/ |
| 3 | Tải **NSSM**, giải nén `win64\` vào `deploy\nssm\` | https://nssm.cc/download |
| 4 | Build JAR: `mvn package -DskipTests` rồi copy `target\management-0.0.1-SNAPSHOT.jar` vào `deploy\` và đổi tên thành `app.jar` | — |

### Cấu trúc thư mục deploy/

```
deploy/
├── app.jar          ← bạn copy vào (sau khi build)
├── config.bat       ← SỬA FILE NÀY TRƯỚC
├── install.bat      ← chạy để cài
├── uninstall.bat    ← chạy để gỡ cài đặt
└── nssm\win64\
    └── nssm.exe     ← bạn copy vào (từ nssm.cc)
```

### Các bước chạy

```
1. Mở config.bat → điền mật khẩu postgres và DB_PASSWORD
2. Chuột phải install.bat → "Run as administrator"
3. Đợi ~60 giây → script tự kiểm tra, cài service, mở firewall, và verify
4. Truy cập: http://localhost:8080
5. Đăng nhập admin/admin → ĐỔI MẬT KHẨU NGAY
```

> Script `install.bat` tự động: tạo database, cài Windows Service (tự khởi động cùng máy), mở firewall port 8080, đặt lịch backup 3:00 AM.

---

---

## Yêu cầu phần cứng

| Thành phần | Tối thiểu | Khuyến nghị |
|------------|-----------|-------------|
| CPU | 2 core | 4 core |
| RAM | 4 GB | **8 GB** (JVM ~2–4 GB + PostgreSQL ~1–2 GB) |
| Disk | 50 GB HDD | 100 GB SSD |
| Network | LAN 100 Mbps | LAN 1 Gbps |

## Yêu cầu phần mềm

| Phần mềm | Phiên bản | Ghi chú |
|----------|-----------|---------|
| OS | Windows Server 2022 | Hoặc Ubuntu 22.04 LTS |
| Java | **17 LTS** | Eclipse Temurin: adoptium.net |
| Maven | 3.8+ | Chỉ cần trên máy build |
| PostgreSQL | **15+** | postgresql.org/download |
| NSSM | bất kỳ | Chạy app như Windows Service (Windows only) |

---

## Hướng dẫn triển khai từng bước

### Bước 1 — Cài Java 17

```
1. Tải Eclipse Temurin 17 tại: https://adoptium.net/
2. Cài đặt, tick "Set JAVA_HOME" và "Add to PATH"
3. Kiểm tra: java -version  →  phải hiện "17.x.x"
```

### Bước 2 — Cài PostgreSQL

```
1. Tải PostgreSQL 15+ tại: https://www.postgresql.org/download/
2. Cài đặt, ghi nhớ mật khẩu user "postgres" đặt trong lúc cài
3. Mặc định PostgreSQL lắng nghe trên port 5432
4. (Tùy chọn) Cài pgAdmin để quản lý DB bằng giao diện đồ họa
```

### Bước 3 — Tạo database và user

Mở Command Prompt hoặc psql, chạy với user postgres:

```bash
psql -U postgres
```

Trong psql, chạy SQL sau:

```sql
CREATE DATABASE shoe_eff_db;

CREATE USER ie_app WITH PASSWORD 'DoiMatKhauNay@2024!';

GRANT ALL PRIVILEGES ON DATABASE shoe_eff_db TO ie_app;

-- Cấp quyền trên schema public (PostgreSQL 15+)
\c shoe_eff_db
GRANT ALL ON SCHEMA public TO ie_app;
```

> **Lưu ý:** Đổi `DoiMatKhauNay@2024!` thành mật khẩu mạnh của bạn.

### Bước 4 — Build JAR

Trên máy dev (có Maven):

```bash
cd C:\path\to\IE-Eff
mvn package -DskipTests
```

Output: `target/management-0.0.1-SNAPSHOT.jar`

Chép file JAR này sang máy server (ví dụ: `C:\ie-eff\app.jar`).

### Bước 5 — Tạo thư mục và cấu hình trên server

```
Tạo cấu trúc thư mục:
C:\ie-eff\
├── app.jar          ← file JAR vừa build
├── logs\            ← sẽ tự tạo khi chạy
└── start.bat        ← xem bên dưới (dùng để test thủ công)
```

Tạo `C:\ie-eff\start.bat`:

```bat
@echo off
set DB_URL=jdbc:postgresql://localhost:5432/shoe_eff_db
set DB_USERNAME=ie_app
set DB_PASSWORD=DoiMatKhauNay@2024!
set PORT=8080

java -Xmx2g -Xms512m -jar C:\ie-eff\app.jar --spring.profiles.active=prod
```

### Bước 6 — Kiểm tra thủ công trước

```
1. Chạy start.bat
2. Đợi khoảng 30–60 giây cho app khởi động
   (Flyway sẽ tự động tạo schema từ V1__init_schema.sql)
3. Mở trình duyệt: http://localhost:8080
4. Phải thấy trang đăng nhập → OK
5. Đăng nhập admin/admin, ĐỔI MẬT KHẨU NGAY
6. Nhấn Ctrl+C để dừng
```

### Bước 7 — Cài làm Windows Service bằng NSSM

```
1. Tải NSSM tại: https://nssm.cc/download
2. Giải nén vào C:\tools\nssm\
3. Mở Command Prompt với quyền Admin:
   C:\tools\nssm\win64\nssm.exe install IE-Eff
4. Điền thông tin trong cửa sổ NSSM:

   [Application tab]
   Path:        C:\Program Files\Eclipse Adoptium\jdk-17...\bin\java.exe
   Arguments:   -Xmx2g -Xms512m -jar C:\ie-eff\app.jar --spring.profiles.active=prod
   Startup dir: C:\ie-eff

   [Environment tab — thêm từng dòng]
   DB_URL=jdbc:postgresql://localhost:5432/shoe_eff_db
   DB_USERNAME=ie_app
   DB_PASSWORD=DoiMatKhauNay@2024!
   PORT=8080

   [I/O tab]
   Output (stdout): C:\ie-eff\logs\nssm-stdout.log
   Error  (stderr): C:\ie-eff\logs\nssm-stderr.log

5. Nhấn "Install service"
6. Khởi động service:
   nssm start IE-Eff
7. Cấu hình tự khởi động:
   sc config IE-Eff start=auto
```

### Bước 8 — Mở firewall nội bộ

```bat
rem Chạy với quyền Admin, thay 192.168.1.0/24 bằng subnet LAN của bạn
netsh advfirewall firewall add rule ^
  name="IE-Eff App" ^
  dir=in ^
  action=allow ^
  protocol=TCP ^
  localport=8080 ^
  remoteip=192.168.1.0/24
```

Người dùng trong LAN truy cập: `http://<IP-máy-server>:8080`

### Bước 9 — Cài backup tự động (pg_dump)

Tạo file `C:\ie-eff\backup.bat`:

```bat
@echo off
set PGPASSWORD=DoiMatKhauNay@2024!
set BACKUP_DIR=C:\backup
set DATE_STR=%DATE:~10,4%%DATE:~4,2%%DATE:~7,2%
"C:\Program Files\PostgreSQL\15\bin\pg_dump.exe" -U ie_app -h localhost -d shoe_eff_db -F c -f "%BACKUP_DIR%\shoe_eff_db_%DATE_STR%.dump"
```

Đặt lịch chạy 3:00 AM hàng ngày qua Windows Task Scheduler:

```
1. Mở Task Scheduler → Create Basic Task
2. Name: "IE-Eff Daily Backup"
3. Trigger: Daily, 3:00 AM
4. Action: Start a program → C:\ie-eff\backup.bat
5. Tạo thư mục C:\backup\ trước
```

> Xóa backup cũ hơn 30 ngày để tiết kiệm ổ đĩa:
> `forfiles /p C:\backup /s /m *.dump /d -30 /c "cmd /c del @path"`

---

## Checklist trước khi go-live

- [ ] `java -version` hiện Java 17
- [ ] PostgreSQL chạy, port 5432 mở
- [ ] Database `shoe_eff_db` và user `ie_app` tạo thành công
- [ ] `mvn package -DskipTests` build không lỗi
- [ ] Test thủ công: `http://localhost:8080` hiện trang login
- [ ] Flyway migration hoàn thành (xem log: `Flyway ... Successfully applied`)
- [ ] Đăng nhập được bằng admin, **ĐÃ ĐỔI MẬT KHẨU**
- [ ] Upload file Excel thử không lỗi (giới hạn 100MB trên prod)
- [ ] NSSM service `IE-Eff` đang chạy (`nssm status IE-Eff`)
- [ ] Sau khi restart máy → service tự bật lại
- [ ] Firewall mở đúng subnet LAN
- [ ] Người dùng khác trong LAN truy cập được `http://<server-IP>:8080`
- [ ] Backup `C:\backup\` có file .dump sau 3:00 AM
- [ ] Log `C:\ie-eff\logs\app.log` được ghi

---

## Cập nhật phiên bản mới

```
1. Build JAR mới: mvn package -DskipTests
2. Dừng service: nssm stop IE-Eff
3. Thay file: copy target\management-0.0.1-SNAPSHOT.jar C:\ie-eff\app.jar
4. Khởi động lại: nssm start IE-Eff
5. Kiểm tra log: type C:\ie-eff\logs\nssm-stdout.log
```

> Flyway tự động chạy các migration mới khi app khởi động.

---

## Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân | Giải pháp |
|-------------|-------------|-----------|
| App không khởi động | Java không tìm thấy | Kiểm tra PATH, `java -version` |
| `Connection refused 5432` | PostgreSQL chưa chạy | Kiểm tra service PostgreSQL, `pg_isready` |
| `password authentication failed` | Sai credentials | Kiểm tra biến môi trường DB_USERNAME/DB_PASSWORD |
| `role "ie_app" does not exist` | Chưa tạo user | Chạy lại SQL ở Bước 3 |
| Trang trắng hoặc 500 | Flyway migration lỗi | Xem log app, kiểm tra bảng `flyway_schema_history` |
| Service dừng sau vài giờ | OutOfMemoryError | Tăng `-Xmx` lên `3g`, kiểm tra RAM |
| Upload Excel báo lỗi | File quá lớn | Giới hạn 100MB (prod), kiểm tra file .xlsx |
