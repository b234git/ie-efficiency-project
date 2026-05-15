# IE-Eff Deployment Guide

Production deployment of IE-Eff (Spring Boot 4.0.3 / Java 17 / PostgreSQL 15). Target: internal LAN, 50–100 users.

Two supported paths:

1. **Docker / Docker Compose** — recommended for WSL Ubuntu and Linux hosts.
2. **Windows Server bare-metal** with NSSM — see legacy section at the bottom and `docs/SERVER_CONFIG.md` for sizing.

---

## A. Docker Deployment (WSL Ubuntu)

### A.1 What's in the repo

| File                       | Purpose                                                            |
|----------------------------|--------------------------------------------------------------------|
| `Dockerfile`               | Multi-stage build (Maven → JRE), runs as non-root `ieapp` user.    |
| `.dockerignore`            | Excludes build artifacts, secrets, IDE files.                      |
| `docker-compose.yml`       | Defines `db` (Postgres 15) + `app` + optional `proxy` (Nginx).     |
| `.env.example`             | Template for the `.env` file you create on the host.               |
| `deploy/nginx.conf`        | Optional TLS-terminating reverse proxy.                            |

### A.2 One-time host setup (WSL Ubuntu)

> Pick **one** of the two paths below. Path 1 is simpler if you already use Docker Desktop on Windows.

#### Path 1 — Docker Desktop with WSL2 integration
1. Install Docker Desktop on Windows.
2. Settings → Resources → WSL Integration → enable for your Ubuntu distro.
3. `wsl -d Ubuntu` → `docker version` should now work without `sudo`.

#### Path 2 — Native Docker Engine inside WSL Ubuntu (no Docker Desktop)
```bash
# In WSL Ubuntu
sudo apt update
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
# Log out / `exec sudo -i -u $USER` to pick up the group change.

# WSL2 doesn't run systemd by default. Either:
#   (a) Enable systemd:  edit /etc/wsl.conf, add [boot]\nsystemd=true,  then `wsl --shutdown` in PowerShell.
#       Then: sudo systemctl enable --now docker
#   (b) Or start daemon manually each WSL boot:  sudo service docker start
```

Verify:
```bash
docker run --rm hello-world
docker compose version
```

### A.3 Project bootstrap (one time)

```bash
# Inside WSL, in the repo directory
cp .env.example .env
$EDITOR .env       # fill in DB_PASSWORD, APP_DEFAULT_*_PASSWORD with strong values
```

Required values in `.env`:
- `DB_PASSWORD` — 24+ random chars
- `APP_DEFAULT_ADMIN_PASSWORD` — temp admin password (change after first login)
- `APP_DEFAULT_MANAGER_PASSWORD` — temp manager password (change after first login)

> **The repo path matters.** If your repo is on `/mnt/c/Users/...` (the Windows filesystem), Docker bind mounts will be slow. Clone it under `~/` inside WSL for best performance.

### A.4 Build and start

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f app          # follow until Flyway migration finishes
```

Open `http://localhost:8080` (or `http://<wsl-host-ip>:8080` from another LAN machine — see networking notes below).

### A.5 With TLS termination (optional)

The `proxy` service is gated by a Compose profile so it doesn't start by default.

1. Place TLS material at `deploy/certs/fullchain.pem` and `deploy/certs/privkey.pem`. For internal use, generate a self-signed cert:
   ```bash
   mkdir -p deploy/certs
   openssl req -x509 -newkey rsa:4096 -nodes -days 825 \
     -keyout deploy/certs/privkey.pem -out deploy/certs/fullchain.pem \
     -subj "/CN=ie-eff.local"
   ```
2. Start with the profile enabled:
   ```bash
   docker compose --profile proxy up -d --build
   ```
3. Open `https://localhost`.

### A.6 Day-2 operations

| Task                  | Command                                                                                   |
|-----------------------|-------------------------------------------------------------------------------------------|
| Tail app logs         | `docker compose logs -f app`                                                              |
| Restart app           | `docker compose restart app`                                                              |
| DB shell              | `docker compose exec db psql -U $DB_USERNAME $DB_NAME`                                    |
| One-shot DB backup    | `docker compose exec -T db pg_dump -U $DB_USERNAME -F c $DB_NAME > backups/ie-$(date +%F).dump` |
| Restore from dump     | `docker compose exec -T db pg_restore -U $DB_USERNAME -d $DB_NAME --clean < backups/ie-...dump` |
| Update to a new build | `git pull && docker compose up -d --build app`                                            |
| Tear down (keep data) | `docker compose down`                                                                     |
| Wipe everything       | `docker compose down -v`  ⚠️ deletes the Postgres volume                                  |

### A.7 Backups (scheduled)

Add a host-level cron (WSL) — daily at 03:00:
```bash
crontab -e
# add:
0 3 * * * cd /home/USER/IE-Eff && docker compose exec -T db pg_dump -U ie_app -F c shoe_eff_db > backups/ie-$(date +\%F).dump && find backups -name 'ie-*.dump' -mtime +60 -delete
```

> If WSL is shut down when cron fires, the job is skipped. For unattended servers, run on a real Linux host or use Windows Task Scheduler to keep WSL alive.

### A.8 Networking notes (WSL specifics)

- **From the Windows host**: `http://localhost:8080` works because WSL2 forwards localhost.
- **From another LAN machine**: you must port-forward Windows → WSL. PowerShell as Admin:
  ```powershell
  $wslIp = (wsl hostname -I).Trim().Split()[0]
  netsh interface portproxy add v4tov4 listenport=8080 listenaddress=0.0.0.0 connectport=8080 connectaddress=$wslIp
  netsh advfirewall firewall add rule name="IE-Eff 8080" dir=in action=allow protocol=TCP localport=8080
  ```
  Re-run after each WSL restart, since the WSL IP changes. For production use on real LAN traffic, run Docker on a real Linux server instead of WSL.
- **Management port 8081** is host-bound to `127.0.0.1` only — not reachable from LAN.

---

## What I (Claude) have set up vs. what you need to do

### ✅ Already in the repo

- Multi-stage `Dockerfile` (build cache–friendly, non-root, healthcheck against `/actuator/health`).
- `docker-compose.yml` with `db` + `app` + optional `proxy` profile, healthchecks, depends_on, named volumes.
- `.dockerignore` to keep the image small.
- `.env.example` with required-var documentation; Compose fails fast if any required var is unset.
- Nginx config for the optional `proxy` profile.
- App env wiring (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_DEFAULT_*_PASSWORD`, `MANAGEMENT_SERVER_ADDRESS=0.0.0.0` to make management port reachable inside the container while still host-localhost-bound).
- Postgres container tuned for ~100 connections (matches HikariCP pool of 50).
- Flyway runs migrations automatically — no manual schema step.
- Seed user passwords now ship empty by default; app refuses to start if they're missing in `.env` (`APP_DEFAULT_*_PASSWORD`).
- DB password no longer hardcoded; `${DB_PASSWORD}` is the only source.

### 🔧 Tasks you must do (cannot be automated from the repo)

| #  | Task                                                                                                  | Why I can't do it                                  |
|----|-------------------------------------------------------------------------------------------------------|----------------------------------------------------|
| 1  | Install Docker Engine (or Docker Desktop with WSL integration) on your machine.                       | Local environment.                                 |
| 2  | Create `.env` from `.env.example` and fill in **3 strong secrets**.                                   | Secrets must not live in the repo.                 |
| 3  | Enable systemd in WSL or `sudo service docker start` so Docker runs at WSL boot.                      | WSL-host config.                                   |
| 4  | Clone the repo into the WSL filesystem (`~/IE-Eff`), not under `/mnt/c/...`, for performance.         | Filesystem layout.                                 |
| 5  | Generate / obtain TLS certificate if you enable the proxy profile.                                    | Cert authority / internal CA decision.             |
| 6  | If exposing to LAN: configure Windows `netsh portproxy` + firewall rule.                              | Requires Admin shell + your LAN network.           |
| 7  | Set up the daily backup cron job (A.7) on a host that stays running.                                  | Host scheduler.                                    |
| 8  | **Finish and commit the in-flight REST refactor** (~30 modified files + untracked controllers) before tagging a release. | Mid-flight engineering work — not safe for me to commit on your behalf. |
| 9  | Stabilize the broken SSR tests flagged in `project_rest_refactor_slice2`.                             | Same as above.                                     |
| 10 | After first login, change the seed admin / manager passwords through the UI.                          | Operational rotation.                              |
| 11 | (Optional) Replace the `app.default-*-password` env-driven seeding with a one-shot CLI tool so passwords aren't injected to every restart. | Design change beyond this task. |

### A.9 Sanity checklist before flipping to "production"

- [ ] `.env` contains non-default values for `DB_PASSWORD`, `APP_DEFAULT_ADMIN_PASSWORD`, `APP_DEFAULT_MANAGER_PASSWORD`.
- [ ] `.env` is in `.gitignore` (it is).
- [ ] `docker compose ps` shows both `db` and `app` as `healthy`.
- [ ] `curl http://localhost:8080/login` returns 200.
- [ ] `curl http://127.0.0.1:8081/actuator/health` returns `{"status":"UP"}`.
- [ ] Logged in as `admin` / `manager` once, **changed both passwords**.
- [ ] First nightly backup file exists under `backups/`.
- [ ] If LAN-exposed: tested from a second machine.

---

## B. Windows Server bare-metal (NSSM) — legacy path

Use this if you cannot run Docker on the target machine. See **[docs/SERVER_CONFIG.md](docs/SERVER_CONFIG.md)** for hardware sizing, JVM flags, PostgreSQL tuning, reverse-proxy config, and the pgpass-based backup script.

High level:

1. Install Java 17 (Temurin), PostgreSQL 15+, NSSM.
2. Run `docs/db-bootstrap.sql` as `postgres` (creates `ie_app` role + `shoe_eff_db`).
3. `mvn package -DskipTests` → copy `target/management-0.0.1-SNAPSHOT.jar` to `C:\ie-eff\app.jar`.
4. Set system env vars: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_DEFAULT_ADMIN_PASSWORD`, `APP_DEFAULT_MANAGER_PASSWORD`, `SPRING_PROFILES_ACTIVE=prod`.
5. Install NSSM service:
   ```bat
   nssm install IEEff "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe"
   nssm set IEEff AppParameters "-Xmx8g -Xms2g -XX:+UseG1GC -jar C:\ie-eff\app.jar"
   nssm set IEEff AppDirectory  "C:\ie-eff"
   nssm set IEEff Start          SERVICE_AUTO_START
   nssm start IEEff
   ```
6. Open firewall port 8080 (or terminate TLS at IIS / Nginx in front and only expose 443).
7. Schedule the pgpass-based backup script — see `docs/SERVER_CONFIG.md`.

---

## Troubleshooting

| Symptom                                                | Likely cause                                                          | Fix                                                                                                                       |
|--------------------------------------------------------|-----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `app` exits with `IllegalStateException: Seed user passwords are not configured`. | `APP_DEFAULT_ADMIN_PASSWORD` / `APP_DEFAULT_MANAGER_PASSWORD` empty.   | Set both in `.env`, `docker compose up -d`.                                                                               |
| `app` exits with HikariCP "FATAL: password authentication failed". | Wrong `DB_PASSWORD`, or Postgres started before the env var changed.  | `docker compose down -v`, fix `.env`, re-up. (Note: `-v` wipes data — only do this on first bring-up.)                    |
| `Flyway migration failed: schema "public" has no usable tables`. | Pre-existing DB volume with partial state.                            | `docker compose down -v` and re-bring-up, or run `docs/db-bootstrap.sql` against the existing DB.                          |
| `413 Request Entity Too Large` on Excel upload.        | Proxy body-size limit < 100 MB.                                       | Set `client_max_body_size 100M` in Nginx (already in `deploy/nginx.conf`) or `maxAllowedContentLength` in IIS web.config. |
| `/actuator/health` not reachable from outside.         | Management port is host-bound to `127.0.0.1` by design.               | Use it from the same host only; do not expose to LAN.                                                                     |
| Slow first build on Windows path.                      | Source on `/mnt/c/...` causes Docker bind I/O slowness.               | Move repo into the WSL filesystem (`~/IE-Eff`).                                                                           |
