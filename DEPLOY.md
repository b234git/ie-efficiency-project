# Hướng dẫn Triển khai IE-Eff (Nội bộ)

Ứng dụng quản lý hiệu suất sản xuất — Spring Boot 4.0.3 / Java 17 / SQL Server.
Dùng cho mạng nội bộ (LAN), không yêu cầu kết nối internet.

---

## Yêu cầu phần cứng

| Thành phần | Tối thiểu | Khuyến nghị |
|------------|-----------|-------------|
| CPU | 2 core | 4 core |
| RAM | 4 GB | **8 GB** (quan trọng nhất) |
| Disk | 50 GB HDD | 100 GB SSD |
| Network | LAN 100 Mbps | LAN 1 Gbps |

> JVM heap cần ~2–4 GB, SQL Server cần ~2 GB riêng → tổng cần ít nhất 6 GB RAM.

## Yêu cầu phần mềm

| Phần mềm | Phiên bản | Ghi chú |
|----------|-----------|---------|
| OS | Windows Server 2022 | Hoặc Ubuntu 22.04 LTS |
| Java | **17 LTS** | Eclipse Temurin: adoptium.net |
| Maven | 3.8+ | Chỉ cần trên máy build |
| SQL Server | 2019+ | Express (free) đủ dùng, giới hạn 10 GB DB |
| NSSM | bất kỳ | Chạy app như Windows Service |

---

## Hướng dẫn triển khai từng bước

### Bước 1 — Cài Java 17

```
1. Tải Eclipse Temurin 17 tại: https://adoptium.net/
2. Cài đặt, tick "Set JAVA_HOME" và "Add to PATH"
3. Kiểm tra: java -version  →  phải hiện "17.x.x"
```

### Bước 2 — Cài và cấu hình SQL Server

```
1. Tải SQL Server Express tại: https://www.microsoft.com/sql-server/
2. Cài đặt với chế độ "Basic"
3. Mở SQL Server Configuration Manager:
   - Vào SQL Server Network Configuration → Protocols for MSSQLSERVER
   - Enable "TCP/IP"
   - Nhấp đúp TCP/IP → Tab IP Addresses → IPAll → TCP Port = 1433
   - Restart service SQL Server
4. Cài thêm SSMS (SQL Server Management Studio) để quản lý
```

### Bước 3 — Tạo database và user

Mở SSMS, kết nối localhost, chạy SQL sau:

```sql
CREATE DATABASE ShoeEffDB;
GO

CREATE LOGIN ie_app WITH PASSWORD = 'DoiMatKhauNay@2024!';
GO

USE ShoeEffDB;
CREATE USER ie_app FOR LOGIN ie_app;
ALTER ROLE db_owner ADD MEMBER ie_app;
GO
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
└── start.bat        ← xem bên dưới (tùy chọn)
```

Tạo `C:\ie-eff\start.bat` (dùng để test thủ công):

```bat
@echo off
set DB_URL=jdbc:sqlserver://localhost:1433;databaseName=ShoeEffDB;encrypt=true;trustServerCertificate=true
set DB_USERNAME=ie_app
set DB_PASSWORD=DoiMatKhauNay@2024!
set PORT=8080

java -Xmx2g -Xms512m -jar C:\ie-eff\app.jar --spring.profiles.active=prod
```

### Bước 6 — Kiểm tra thủ công trước

```
1. Chạy start.bat
2. Đợi khoảng 30–60 giây cho app khởi động
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
   DB_URL=jdbc:sqlserver://localhost:1433;databaseName=ShoeEffDB;encrypt=true;trustServerCertificate=true
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

```
# Chạy với quyền Admin, thay 192.168.1.0/24 bằng subnet LAN của bạn
netsh advfirewall firewall add rule ^
  name="IE-Eff App" ^
  dir=in ^
  action=allow ^
  protocol=TCP ^
  localport=8080 ^
  remoteip=192.168.1.0/24
```

Người dùng trong LAN truy cập: `http://<IP-máy-server>:8080`

### Bước 9 — Cài backup tự động SQL Server

Mở SSMS → SQL Server Agent → Jobs → New Job, hoặc chạy SQL sau:

```sql
-- Tạo stored procedure backup
USE msdb;
GO

EXEC sp_add_job @job_name = N'Daily Backup IE-Eff';
EXEC sp_add_jobstep
    @job_name = N'Daily Backup IE-Eff',
    @step_name = N'Backup',
    @command = N'BACKUP DATABASE [ShoeEffDB]
                 TO DISK = N''C:\backup\ShoeEffDB_'' + CONVERT(VARCHAR,GETDATE(),112) + ''.bak''
                 WITH COMPRESSION, STATS = 10;';
EXEC sp_add_schedule
    @schedule_name = N'Daily 3AM',
    @freq_type = 4,
    @freq_interval = 1,
    @active_start_time = 030000;  -- 03:00:00
EXEC sp_attach_schedule @job_name = N'Daily Backup IE-Eff', @schedule_name = N'Daily 3AM';
EXEC sp_add_jobserver @job_name = N'Daily Backup IE-Eff';
GO
```

Tạo thư mục `C:\backup\` trước khi chạy.

---

## Checklist trước khi go-live

- [ ] `java -version` hiện Java 17
- [ ] SQL Server chạy, port 1433 mở
- [ ] Database `ShoeEffDB` và user `ie_app` tạo thành công
- [ ] `mvn package -DskipTests` build không lỗi
- [ ] Test thủ công: `http://localhost:8080` hiện trang login
- [ ] Đăng nhập được bằng admin, **ĐÃ ĐỔI MẬT KHẨU**
- [ ] Upload file Excel thử không lỗi
- [ ] NSSM service `IE-Eff` đang chạy (`nssm status IE-Eff`)
- [ ] Sau khi restart máy → service tự bật lại
- [ ] Firewall mở đúng subnet LAN
- [ ] Người dùng khác trong LAN truy cập được `http://<server-IP>:8080`
- [ ] Backup `C:\backup\` có file .bak sau 3:00 AM
- [ ] Log `C:\ie-eff\logs\app.log` được ghi

---

## Cập nhật phiên bản mới

```
1. Build JAR mới: mvn package -DskipTests
2. Dừng service: nssm stop IE-Eff
3. Thay file: copy target\management-0.0.1-SNAPSHOT.jar C:\ie-eff\app.jar
4. Khởi động lại: nssm start IE-Eff
5. Kiểm tra log: tail -f C:\ie-eff\logs\app.log
```

---

## Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân | Giải pháp |
|-------------|-------------|-----------|
| App không khởi động | Java không tìm thấy | Kiểm tra PATH, `java -version` |
| `Connection refused 1433` | SQL Server chưa bật TCP | Bật TCP/IP trong SQL Config Manager, restart |
| `Login failed for user` | Sai credentials | Kiểm tra biến môi trường DB_USERNAME/DB_PASSWORD |
| Trang trắng hoặc 500 | Flyway migration lỗi | Xem `logs\app.log`, kiểm tra schema DB |
| Service dừng sau vài giờ | OutOfMemoryError | Tăng `-Xmx` lên `3g`, kiểm tra RAM |
| Upload Excel báo lỗi | File quá lớn | Giới hạn 100MB (prod), kiểm tra file .xlsx |
