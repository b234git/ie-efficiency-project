# IE-Eff — Deployment on Windows Server 2012

How to deploy IE-Eff (Spring Boot 4.0.3 / Java 17 / PostgreSQL 15) on an existing
**Windows Server 2012** machine, using the automated installer in the `deploy/` folder.

Target: internal LAN tool, 50–100 users.

---

## 0. Read this first

### ⚠️ Server 2012 is end-of-life

Windows Server 2012 / 2012 R2 reached **end of support on 10 October 2023** — no more
security patches. Therefore:

- **Keep this deployment LAN-only.** Do not expose the app port to the public internet.
- If remote access is needed, use a VPN (e.g. Tailscale), never a raw port-forward.
- Plan an OS upgrade to Windows Server 2022 when possible.

### Confirm the OS version

This is verified for **Server 2012 R2**. Run in PowerShell on the server:

```powershell
(Get-CimInstance Win32_OperatingSystem).Caption
```

### Why no Docker

Docker cannot run on Server 2012 (it needs Server 2016+). This guide installs Java and
PostgreSQL natively and runs the app as a Windows service — that is the only viable path
on this OS.

---

## 1. How the deployment works

Everything is driven by three files in the `deploy/` folder of this repo:

| File          | What it does                                                              |
|---------------|---------------------------------------------------------------------------|
| `config.bat`  | The settings you edit — DB passwords, install folder, port, **RAM**.      |
| `init_db.sql` | Creates all 14 database tables. Run automatically by `install.bat`.       |
| `install.bat` | Does everything: creates the DB, loads the schema, installs the Windows service, opens the firewall, schedules backups, and starts the app. |

`deploy/` also bundles NSSM (the service wrapper) and has `uninstall.bat` to remove
everything.

The runtime result:

```
   LAN browsers ──http://<server-ip>:8080──► IE-Eff service (NSSM)  ──► PostgreSQL 15
                                              port 8080                  shoe_eff_db
```

---

## 2. Prerequisites — install these two things manually

The installer checks for Java and PostgreSQL but does not install them. Download the
installers on a normal PC and copy them to the server — Server 2012's built-in browser
often cannot reach modern HTTPS download sites.

### 2.1 Java 17

Install **Eclipse Temurin JDK 17** (Windows x64 `.msi`):
https://adoptium.net/temurin/releases/?version=17

During setup, enable **"Set JAVA_HOME variable"** and **"Add to PATH"**. Verify in a new
PowerShell window:

```powershell
java -version
```

### 2.2 PostgreSQL 15

Install **PostgreSQL 15** (Windows x86-64):
https://www.enterprisedb.com/downloads/postgres-postgresql-downloads

> Use version **15** — it is the tested version and installs cleanly on Server 2012 R2.
> Newer PostgreSQL releases may refuse to install on this OS.

During setup:
- Set a password for the `postgres` superuser — **write it down**, you need it in §4.
- Keep the default port **5432**.
- Locale: choose **`C`** or **Default locale**.

---

## 3. Build the application JAR

Build on your **development machine** (it already has the JDK and internet for Maven),
then copy the result to the server. In the repo root:

```powershell
.\mvnw.cmd clean package -DskipTests
```

`-DskipTests` is intentional — the repo has known-broken SSR tests that would block the
build but do not affect the runtime app.

This produces `target\management-0.0.1-SNAPSHOT.jar`. Copy it into the `deploy\` folder
and **rename it to `app.jar`**:

```
deploy\app.jar
```

Then copy the **whole `deploy\` folder** to the server (e.g. to the Desktop).

---

## 4. Configure — edit `config.bat`

Open `deploy\config.bat` on the server in Notepad and set the values. This is the only
file you edit.

```bat
:: Mat khau user "postgres" — nhap o §2.2
set POSTGRES_PASSWORD=postgres

:: Database ung dung
set DB_NAME=shoe_eff_db
set DB_USER=ie_app
set DB_PASSWORD=IeApp@Secure2024!

:: Thu muc cai dat
set INSTALL_DIR=C:\ie-eff

:: Port ung dung
set APP_PORT=8080

:: RAM cho ung dung (Java heap)
set APP_XMX=2g
set APP_XMS=512m

:: Mat khau tai khoan khoi tao
set APP_ADMIN_PASSWORD=Admin@123
set APP_MANAGER_PASSWORD=Manager@123
```

What to change:

| Setting               | Set it to                                                             |
|-----------------------|-----------------------------------------------------------------------|
| `POSTGRES_PASSWORD`   | The `postgres` superuser password from §2.2.                          |
| `DB_PASSWORD`         | A strong password for the app's database account.                     |
| `APP_XMX`             | **Maximum RAM for the app** — see §4.1.                               |
| `APP_ADMIN_PASSWORD` / `APP_MANAGER_PASSWORD` | Temporary login passwords — change them after first login (§6). |
| `APP_PORT`            | Leave `8080` unless that port is taken.                               |

> Do **not** use the `!` character in any password — `.bat` files cannot handle it.

### 4.1 Choosing how much RAM (`APP_XMX`)

`APP_XMX` is the maximum heap memory the application may use. Pick it from the server's
total RAM — a good rule is about a quarter of it:

| Server RAM | Set `APP_XMX` |
|-----------:|:-------------:|
| 8 GB       | `2g`          |
| 16 GB      | `4g`          |
| 32 GB      | `8g`          |

Leave `APP_XMS` (the startup heap) at `512m`.

Check the server's RAM with:

```powershell
[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
```

To change RAM **later**, edit `APP_XMX` in `config.bat` and re-run `install.bat` (§5) —
it is safe to run again.

---

## 5. Run the installer

1. Make sure `deploy\app.jar` exists (§3).
2. **Right-click `install.bat` → "Run as administrator".**

It runs six steps and prints progress:

1. Checks Java 17.
2. Finds PostgreSQL.
3. Creates the database + user, **then creates all 14 tables from `init_db.sql`**.
4. Creates the install folder and copies `app.jar`.
5. Installs the `IE-Eff` Windows service (auto-start) and opens the firewall port.
6. Schedules a daily database backup at 03:00.

It then starts the service and waits ~45 seconds.

---

## 6. Verify and first login

The installer tries an automatic check at the end. On Server 2012 the `curl` command may
be missing, so it can show *"App chua phan hoi"* even when the app is fine — verify
manually instead:

- On the server: open `http://localhost:8080`
- From another LAN PC: open `http://<server-ip>:8080`
  (find the IP with `ipconfig`)

Log in with the seed accounts using the passwords you set in `config.bat`:

- `admin` / `APP_ADMIN_PASSWORD`
- `manager` / `APP_MANAGER_PASSWORD`

**Change both passwords immediately** through the UI.

---

## 7. Day-2 operations

Run these in an Administrator PowerShell. The service name is `IE-Eff`.

| Task            | Command                                                          |
|-----------------|------------------------------------------------------------------|
| Start           | `Start-Service IE-Eff`                                           |
| Stop            | `Stop-Service IE-Eff`                                            |
| Restart         | `Restart-Service IE-Eff`                                         |
| Status          | `Get-Service IE-Eff`                                             |
| View logs       | `Get-Content C:\ie-eff\logs\app.log -Tail 40 -Wait`              |
| Change RAM      | Edit `APP_XMX` in `config.bat`, re-run `install.bat`             |

### Deploying a new version

1. Build a new `app.jar` (§3) and copy it into `deploy\`.
2. Re-run `install.bat` as administrator — it stops the service, swaps the JAR, and
   restarts. The database and its data are kept.

### Backups

`install.bat` already scheduled a daily `pg_dump` at 03:00 to `C:\ie-eff\backup\`
(kept 30 days). To restore a backup:

```powershell
& "C:\Program Files\PostgreSQL\15\bin\pg_restore.exe" -U ie_app -h localhost -d shoe_eff_db --clean "C:\ie-eff\backup\db_YYYYMMDD.dump"
```

### Uninstall

Right-click `uninstall.bat` → "Run as administrator". It removes the service, firewall
rule and backup task. The database is left intact (it tells you how to drop it).

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `[LOI] Khong the ket noi PostgreSQL` | Wrong `POSTGRES_PASSWORD` in `config.bat` | Correct it; confirm with `psql -U postgres -h localhost` |
| `[LOI] Khong the tao bang du lieu` | Schema load failed | Read the PostgreSQL error printed above the message; usually a permissions or connection issue |
| `[LOI] Khong tim thay app.jar` | JAR not built/copied | Do §3 — build and copy to `deploy\app.jar` |
| `[LOI] Java chua cai dat` | Java 17 not installed or not on PATH | Do §2.1, open a new shell |
| Service starts then stops | Check `C:\ie-eff\logs\app.log` | Usually a wrong `DB_PASSWORD` or a missing env var |
| `Schema-validation: missing table` in the log | `init_db.sql` did not run | Re-run `install.bat`; the schema step must report "schema da san sang" |
| App seeds fail to start, log mentions seed passwords | `APP_ADMIN_PASSWORD` / `APP_MANAGER_PASSWORD` empty in `config.bat` | Set both, re-run `install.bat` |
| App reachable on the server but not from other PCs | Firewall | Confirm the rule "IE-Eff App" exists for the port; check clients use the server IP |

---

## 9. Go-live checklist

- [ ] OS confirmed; deployment kept LAN-only.
- [ ] Java 17 and PostgreSQL 15 installed.
- [ ] `app.jar` built and placed in `deploy\`.
- [ ] `config.bat` filled in — passwords set, `APP_XMX` sized to the server's RAM.
- [ ] `install.bat` ran clean; schema step reported "schema da san sang".
- [ ] App reachable from a second LAN machine.
- [ ] Logged in as `admin` and `manager`; **both passwords changed**.
- [ ] A backup file exists under `C:\ie-eff\backup\` after the first 03:00 run.
