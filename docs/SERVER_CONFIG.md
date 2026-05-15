# IE-Eff Server Configuration Guide
> Target: 100+ internal users · Windows Server · 32 GB RAM · 1 TB SSD + 1 TB HDD

---

## Hardware Specification

| Component | Specification |
|-----------|--------------|
| CPU | 8 core (minimum) |
| RAM | 32 GB |
| Primary Disk | 1 TB SSD |
| Secondary Disk | 1 TB HDD |
| Network | LAN 1 Gbps |
| OS | Windows Server 2022 |

---

## Disk Layout

```
SSD 1 TB
├── C:\  (OS + Application)     ~200 GB
└── D:\  (PostgreSQL data dir)  ~800 GB

HDD 1 TB
└── E:\backup\                  ~1 TB  (daily pg_dump + log archive)
```

Move the PostgreSQL data directory to `D:\` during installation to keep OS I/O and DB I/O on separate partitions of the same SSD.

---

## Memory Allocation

```
32 GB RAM breakdown:
├── Windows Server OS             ~2–3 GB
├── JVM Heap (-Xmx)               ~8 GB
├── JVM Metaspace + overhead       ~1 GB
├── PostgreSQL shared_buffers      ~8 GB
├── PostgreSQL work_mem (x50 conn) ~2 GB
├── OS file cache + HikariCP       ~2 GB
└── Headroom / burst               ~8 GB
```

---

## JVM Flags

In NSSM **Arguments** field (or `start.bat`):

```bat
-Xmx8g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=16m -jar C:\ie-eff\app.jar --spring.profiles.active=prod
```

| Flag | Value | Reason |
|------|-------|--------|
| `-Xmx` | `8g` | Max heap — leaves room for OS + PostgreSQL |
| `-Xms` | `2g` | Pre-allocate to avoid resize at startup |
| `-XX:+UseG1GC` | — | Lower GC pause for large heaps |
| `-XX:MaxGCPauseMillis` | `200` | Target max pause under concurrent load |
| `-XX:G1HeapRegionSize` | `16m` | Optimal for 8 GB heap |

---

## application-prod.properties

```properties
# ── HikariCP Connection Pool ─────────────────────────────────────────────────
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=15
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000

# ── Tomcat Thread Pool ────────────────────────────────────────────────────────
server.tomcat.threads.max=200
server.tomcat.threads.min-spare=25
server.tomcat.accept-count=150
server.tomcat.max-connections=8192

# ── HTTP Compression ──────────────────────────────────────────────────────────
server.compression.enabled=true
server.compression.mime-types=text/html,text/css,application/javascript,application/json
server.compression.min-response-size=2048
```

---

## PostgreSQL Configuration

File location (default): `C:\Program Files\PostgreSQL\15\data\postgresql.conf`

```conf
# Memory
shared_buffers         = 8GB      # 25% of 32 GB RAM
effective_cache_size   = 20GB     # ~60% of RAM (planner hint, not allocated)
work_mem               = 40MB     # per sort/hash operation — 50 connections × 40 MB = 2 GB peak
maintenance_work_mem   = 1GB      # VACUUM, CREATE INDEX, etc.
wal_buffers            = 64MB

# Connections
max_connections        = 150      # HikariCP pool (50) + DBA + headroom

# Checkpointing
checkpoint_completion_target = 0.9

# SSD tuning
random_page_cost       = 1.1      # SSD — lower than default 4.0
effective_io_concurrency = 200    # SSD concurrent I/O

# Logging (optional but recommended)
log_min_duration_statement = 1000  # log queries slower than 1 s
```

After editing, restart PostgreSQL service:
```bat
net stop postgresql-x64-15
net start postgresql-x64-15
```

---

## Backup Configuration

### 1. Create a pgpass file (do NOT hard-code passwords in scripts)

Path on Windows: `%APPDATA%\postgresql\pgpass.conf` (for the user that runs the scheduled task).

Format — one line per entry:
```
localhost:5432:shoe_eff_db:ie_app:<your-db-password>
```

Then restrict ACLs so only the task-runner user can read it:
```powershell
icacls "$env:APPDATA\postgresql\pgpass.conf" /inheritance:r /grant:r "${env:USERNAME}:(R)"
```

`pg_dump` will pick it up automatically — no `PGPASSWORD` needed.

### 2. Backup script — `C:\ie-eff\backup.bat`

```bat
@echo off
set BACKUP_DIR=E:\backup
set DATE_STR=%DATE:~10,4%%DATE:~4,2%%DATE:~7,2%
"C:\Program Files\PostgreSQL\15\bin\pg_dump.exe" -U ie_app -h localhost -d shoe_eff_db -F c -f "%BACKUP_DIR%\shoe_eff_db_%DATE_STR%.dump"

rem Delete backups older than 60 days
forfiles /p E:\backup /s /m *.dump /d -60 /c "cmd /c del @path"
```

Schedule via Windows Task Scheduler: **daily at 03:00 AM**, running as the user whose `pgpass.conf` you configured.

---

## Reverse Proxy / TLS Termination

The Spring Boot app does **not** terminate TLS. Put a reverse proxy (IIS / Nginx / Caddy) in front, listening on 443.

**Required proxy settings:**

| Setting | Value | Why |
|---------|-------|-----|
| Forward `X-Forwarded-Proto: https` | yes | App already has `server.forward-headers-strategy=framework` |
| Forward `X-Forwarded-For` | yes | Correct client IP in logs / rate limiter |
| Max request body size | **≥ 100 MB** | Match `spring.servlet.multipart.max-request-size`; otherwise large Excel imports fail with 413 |
| Idle / read timeout | ≥ 120 s | Long-running imports / report generation |
| Backend target | `http://127.0.0.1:8080` | App binds to all interfaces by default; for defence-in-depth set `server.address=127.0.0.1` |

**Nginx example:**
```nginx
client_max_body_size 100M;
proxy_read_timeout 120s;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
proxy_set_header Host              $host;
proxy_pass http://127.0.0.1:8080;
```

**IIS (URL Rewrite + ARR)** — set `<requestLimits maxAllowedContentLength="104857600">` in `web.config`.

---

## Management Endpoints (localhost-only)

The app now exposes actuator on a **separate management port** bound to `127.0.0.1` only.

| Item | Value |
|------|-------|
| Port | 8081 |
| Bind address | 127.0.0.1 (LAN-unreachable) |
| Base path | `/actuator` |
| Exposed | `health`, `info`, `metrics` |

Reverse-proxy health probe should target `http://127.0.0.1:8081/actuator/health`. Do **not** publish this port through the firewall.

---

## Configuration Comparison

| Setting | Previous (8 GB) | This Guide (32 GB) |
|---------|-----------------|--------------------|
| JVM Heap | 2 GB | **8 GB** |
| PostgreSQL shared_buffers | ~2 GB | **8 GB** |
| HikariCP max pool | 20 | **50** |
| Tomcat max threads | default (200) | **200 (explicit)** |
| HTTP compression | off | **on** |
| Backup retention | 30 days | **60 days** |
| Backup disk | same as data | **separate HDD** |
