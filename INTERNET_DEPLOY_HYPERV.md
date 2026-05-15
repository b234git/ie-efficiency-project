# Public-Internet Deployment via Hyper-V Linux VM on Windows Server 2012

End-to-end guide to put IE-Eff on the public internet from a Windows Server 2012 host, by running Ubuntu Server inside Hyper-V and exposing it through your router with a domain and Let's Encrypt TLS.

> **Read this once end-to-end before starting.** Parts 1 and 2 (Hyper-V setup, networking) happen from the Windows host. Parts 3–6 happen inside the Ubuntu VM, where you clone this repo and follow the rest of the file from disk.

---

## Architecture

```
                 Internet
                    │
                    ▼  443 / 80
            ┌───────────────┐
            │  Your router  │  ← port-forward 80,443 → VM LAN IP
            └───────┬───────┘
                    │  LAN (e.g. 192.168.1.50)
                    ▼
   ┌────────────────────────────────────────┐
   │ Windows Server 2012 (host, EOL OS)     │
   │                                        │
   │  ┌──────────────────────────────────┐  │
   │  │ Hyper-V Virtual Switch (External)│  │
   │  └──────────────┬───────────────────┘  │
   │                 │                      │
   │  ┌──────────────▼───────────────────┐  │
   │  │ Ubuntu Server 22.04 LTS (Gen 1)  │  │
   │  │   ┌─────────────────────────┐    │  │
   │  │   │ Docker Engine            │    │  │
   │  │   │  ├─ caddy   (TLS)        │    │  │
   │  │   │  ├─ ie-eff-app           │    │  │
   │  │   │  └─ ie-eff-db (Postgres) │    │  │
   │  │   └─────────────────────────┘    │  │
   │  └──────────────────────────────────┘  │
   └────────────────────────────────────────┘
```

The Windows Server 2012 host is intentionally **not** in the public traffic path. Only the Ubuntu VM is reachable, and only on ports 80/443.

---

## Prerequisites checklist

Before you start, make sure you have:

- [ ] Windows Server 2012 (or 2012 R2) with Hyper-V role available
- [ ] At least 16 GB free RAM and 100 GB free disk on the host (VM will use 8 GB / 100 GB)
- [ ] A public static IP on your router/firewall (or a Dynamic DNS service)
- [ ] A registered domain (Namecheap, Cloudflare Registrar, GoDaddy, etc.) and DNS-management access for it
- [ ] Admin access to your router (to add port-forwarding rules)
- [ ] Ubuntu Server 22.04 LTS ISO downloaded — https://releases.ubuntu.com/22.04/
- [ ] Git access to this repo (HTTPS or SSH)

> Domain examples in this guide use `ie-eff.example.com`. Replace with your actual domain everywhere it appears.

---

# PART 1 — On the Windows Server 2012 host

## 1.1 Install / enable the Hyper-V role

If Hyper-V is already installed (Server Manager → "Hyper-V" appears as a role), skip to 1.2.

Otherwise:

1. Open **Server Manager** → **Manage** → **Add Roles and Features**.
2. Wizard: Role-based → next → select the local server → check **Hyper-V** → next through defaults → **Install**.
3. **Reboot** when prompted.

Verify: from the Start menu, open **Hyper-V Manager**. You should see the server name in the left pane.

## 1.2 Create the virtual switch (External)

The VM needs its own IP on your LAN so the router can forward traffic to it.

1. Hyper-V Manager → right pane → **Virtual Switch Manager**.
2. Select **External** → **Create Virtual Switch**.
3. Name: `External-LAN`.
4. Connection type: **External network**, pick your physical NIC.
5. Leave **Allow management operating system to share this network adapter** checked (so the Windows host still has its LAN IP).
6. **OK** — the host network will blip for ~5 seconds while the switch is created.

## 1.3 Create the Ubuntu VM

1. Hyper-V Manager → **Action → New → Virtual Machine**.
2. Name: `ie-eff-ubuntu`. Optionally change the storage location to a disk with ≥ 100 GB free.
3. **Generation: 1** (more reliable for Ubuntu on Server 2012 Hyper-V than Gen 2).
4. **Memory: 8192 MB** (8 GB). **Uncheck "Use Dynamic Memory"** — Docker/JVM behave better with a fixed allocation.
5. **Networking: External-LAN** (the switch you just made).
6. **Hard disk: Create a virtual hard disk**, 100 GB, dynamically expanding.
7. **Installation options: Install an operating system from a bootable CD/DVD-ROM**, point to the Ubuntu Server 22.04 ISO.
8. Finish.

After creation:

- Right-click the VM → **Settings**.
- **Processor**: set "Number of virtual processors" to **4**.
- Click **OK**.

## 1.4 Install Ubuntu Server 22.04 LTS

1. Right-click the VM → **Connect** → **Start**.
2. The Ubuntu installer boots. Follow prompts:
   - Language: English
   - Keyboard: your layout
   - Type of install: **Ubuntu Server** (not minimized)
   - **Network**: leave DHCP for now — you'll convert to static in step 2.1
   - Proxy: blank
   - Mirror: default
   - Storage: **Use entire disk**, no LVM unless you want it
   - Profile: create a user named `deploy` with a strong password. Server name `ie-eff-ubuntu`.
   - **SSH setup: install OpenSSH server** ← important
   - Featured snaps: skip all
3. Installation completes (~10 min) → **Reboot**.

After reboot, log in as `deploy` on the Hyper-V console. Run:

```bash
ip -4 addr show eth0 | awk '/inet / {print $2}'
```

Note the IP — e.g. `192.168.1.123/24`. You'll use this in the next steps.

## 1.5 Confirm SSH from another machine

From your workstation:

```bash
ssh deploy@192.168.1.123
```

If this works, you can close the Hyper-V Connect window — everything from here is over SSH.

---

# PART 2 — Network exposure (router + DNS)

## 2.1 Pin the VM's IP (DHCP reservation OR static)

You need the VM's IP to stop changing, or your port-forwards will break.

### Option A — DHCP reservation (recommended, easier)

In your router's admin UI, find DHCP / LAN settings → add a reservation that binds the VM's MAC address to a fixed IP (use the one from step 1.5). Reboot the VM, confirm same IP.

### Option B — Static IP inside Ubuntu

Edit `/etc/netplan/00-installer-config.yaml`:

```yaml
network:
  version: 2
  ethernets:
    eth0:
      addresses: [192.168.1.50/24]
      routes:
        - to: default
          via: 192.168.1.1
      nameservers:
        addresses: [1.1.1.1, 8.8.8.8]
```

Apply: `sudo netplan apply`. Verify with `ip -4 addr`.

## 2.2 Port forward 80 and 443 from router → VM

In your router's admin UI:

| External port | Protocol | Internal IP        | Internal port |
|--------------:|----------|--------------------|--------------:|
| 80            | TCP      | 192.168.1.50 (VM)  | 80            |
| 443           | TCP      | 192.168.1.50 (VM)  | 443           |

**Do not forward 22 (SSH) or 5432 (Postgres).** SSH is for LAN access only; Postgres is internal to Docker.

If your router uses CGNAT (common with home ISPs), port-forwarding won't work. You'll need a business internet plan with a static public IP, or use a tunneling service like Cloudflare Tunnel.

## 2.3 Confirm your public IP and that ports are reachable

```bash
curl -4 ifconfig.me        # your public IP
```

From outside the LAN (e.g. phone on cellular), check `https://www.yougetsignal.com/tools/open-ports/` — query port 80 and 443 against your public IP. They should report **open** (or "no service" — meaning the port forward works but nothing is listening yet, which is correct at this stage).

## 2.4 Configure your domain's DNS

In your domain registrar's DNS panel, add:

| Type | Name      | Value (your public IP) | TTL  |
|------|-----------|------------------------|------|
| A    | `ie-eff`  | `203.0.113.45`         | 300  |

Wait for propagation (1–10 minutes), then verify:

```bash
dig ie-eff.example.com +short
# should print your public IP
```

If you use Cloudflare for DNS, **leave the proxy OFF (grey cloud) for now**. Caddy needs direct port-80 access to obtain the Let's Encrypt cert. You can turn the proxy on after the cert is issued.

---

# PART 3 — Inside the Ubuntu VM

From here, every command runs as `deploy` inside the Ubuntu VM (SSH in from your workstation).

## 3.1 Update system + create swap (optional but helpful)

```bash
sudo apt update && sudo apt -y upgrade
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 3.2 Harden SSH (key-only, no root login)

On your **workstation**, if you don't already have an SSH key:

```bash
ssh-keygen -t ed25519 -C "deploy@ie-eff"
# accept defaults, set a passphrase
ssh-copy-id deploy@192.168.1.50
```

Then on the **VM**:

```bash
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PubkeyAuthentication.*/PubkeyAuthentication yes/' /etc/ssh/sshd_config
sudo systemctl restart ssh
```

**Open a second SSH session to verify key auth works before closing the first one.** If key auth fails you can still recover via the Hyper-V Connect console.

## 3.3 Firewall — deny everything except SSH, HTTP, HTTPS

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status verbose
```

## 3.4 Install fail2ban

```bash
sudo apt -y install fail2ban
sudo systemctl enable --now fail2ban
sudo fail2ban-client status sshd
```

## 3.5 Install Docker Engine

```bash
sudo apt -y install ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker $USER
exec sudo -i -u $USER       # reload group; or log out + back in
docker run --rm hello-world
```

Native Ubuntu has systemd, so Docker auto-starts at boot. Verify:

```bash
sudo systemctl is-enabled docker     # → enabled
```

## 3.6 Clone the repo

```bash
cd ~
git clone <repo-url> IE-Eff
cd IE-Eff
```

> If the repo is private, use SSH (`git@github.com:...`) and either add the VM's SSH key as a deploy key or use a personal access token.

## 3.7 Create `.env` with strong secrets

```bash
cp .env.example .env

# Generate three strong random values — copy each into .env
openssl rand -base64 32     # → DB_PASSWORD
openssl rand -base64 24     # → APP_DEFAULT_ADMIN_PASSWORD
openssl rand -base64 24     # → APP_DEFAULT_MANAGER_PASSWORD

nano .env
```

Set:

```
DB_NAME=shoe_eff_db
DB_USERNAME=ie_app
DB_PASSWORD=<the openssl value>
APP_DEFAULT_ADMIN_PASSWORD=<the openssl value>
APP_DEFAULT_MANAGER_PASSWORD=<the openssl value>
APP_PORT=8080
MANAGEMENT_PORT=8081
TZ=Asia/Ho_Chi_Minh
```

Save and exit. Verify the file isn't tracked:

```bash
git status --short .env
# should show nothing — .env must be gitignored
```

## 3.8 Create the Caddyfile (auto Let's Encrypt)

Caddy obtains and renews TLS certs from Let's Encrypt automatically. Two lines and you're done.

```bash
cat > Caddyfile <<'EOF'
ie-eff.example.com {
    encode zstd gzip

    # Match Spring's spring.servlet.multipart.max-request-size (100 MB).
    request_body {
        max_size 100MB
    }

    reverse_proxy app:8080 {
        header_up X-Forwarded-Proto {scheme}
        header_up X-Forwarded-For   {remote}
        header_up X-Real-IP         {remote}
        transport http {
            read_timeout 120s
        }
    }

    # Basic security headers
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains"
        X-Content-Type-Options    "nosniff"
        X-Frame-Options           "DENY"
        Referrer-Policy           "strict-origin-when-cross-origin"
    }
}
EOF
```

**Replace `ie-eff.example.com` with your actual domain** before continuing.

## 3.9 Create the Compose override for the Caddy proxy

The repo's existing `docker-compose.yml` includes an optional Nginx proxy with a self-signed cert (good for LAN). For internet exposure we override it with Caddy:

```bash
cat > docker-compose.override.yml <<'EOF'
services:
  proxy:
    image: caddy:2-alpine
    container_name: ie-eff-proxy
    restart: unless-stopped
    depends_on:
      - app
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    networks:
      - internal
    profiles: []   # remove the proxy profile gate so it always starts

volumes:
  caddy-data:
  caddy-config:
EOF
```

> Compose auto-merges `docker-compose.override.yml` on top of `docker-compose.yml`. The base file's `proxy` service (Nginx with self-signed cert) is replaced by Caddy here.

## 3.10 First boot

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f proxy
```

Watch the Caddy logs. Within ~30 seconds you should see lines like:

```
certificate obtained successfully
serving initial configuration
```

If you see ACME challenge failures, the most likely causes are: DNS A record not propagated yet, port 80 not actually reachable from the internet, or Cloudflare proxy enabled (turn it off, retry, turn it back on after success).

Once Caddy is happy:

```bash
docker compose logs -f app
# wait for: "Started ShoeManagementProject1Application in N seconds"
```

## 3.11 Verify end-to-end

```bash
curl -I https://ie-eff.example.com/login
# expect: HTTP/2 200
curl -s http://127.0.0.1:8081/actuator/health
# expect: {"status":"UP"}
```

From a browser, open `https://ie-eff.example.com`. Log in as `admin` with the password you set in `.env`.

**Do not stop here — go straight to Part 4.**

---

# PART 4 — Application hardening before sharing the URL

This app was originally designed for an internal LAN. Some defaults that are fine on LAN are dangerous on the public internet. Do all of these **before** you announce the URL to anyone.

## 4.1 Rotate the default seed users

The usernames `admin` and `manager` are the first ones attackers try. Two options:

**Option A — keep the usernames, change passwords frequently.** Log in, settings → change password. Set a 16+ char random password.

**Option B (better) — replace with non-obvious accounts.** Log in as `admin`, create a new admin account with an unguessable username (e.g. `ie_root_a4f9`), log out, log back in as the new account, delete `admin` and `manager`. Now password-spray bots have no valid usernames to try.

## 4.2 Lock down `/register`

The Spring Security config currently allows public access to `/register/**`. On the public internet that means anyone can self-register. Decide:

- If self-registration is **not** a product requirement: remove the route, or change the config to `hasRole("ADMIN")`. Edit `src/main/java/thienloc/manage/security/SecurityConfig.java` line 41:
  ```java
  .requestMatchers("/register/**").hasRole("ADMIN")
  ```
  Rebuild: `docker compose up -d --build app`.
- If it is a requirement: at minimum add a CAPTCHA and rate limit.

## 4.3 Confirm the rate limiter covers `/login`

The repo has a `RateLimitInterceptor`. Check the registration:

```bash
grep -r "RateLimitInterceptor" src/main/java/thienloc/manage/config/
```

Make sure `addPathPatterns(...)` includes `/login` (or `/**` minus static assets). If not, edit the WebMvcConfig and add it. Without this, password-spray attacks succeed quickly.

## 4.4 (Recommended) Put Cloudflare in front

Free tier gives you:
- Layer-7 DDoS protection
- A basic WAF
- IP-reputation filtering (most botnets are pre-blocked)
- Visibility into who's hitting you

Setup:

1. Add your domain to Cloudflare (free plan).
2. Change nameservers at your registrar to Cloudflare's.
3. Set the A record for `ie-eff` to "Proxied" (orange cloud).
4. In Cloudflare → SSL/TLS → set mode to **Full (strict)**.
5. In Caddy, restrict to only Cloudflare IPs (optional, prevents direct-IP access bypassing CF):

   Edit your Caddyfile, add at the top of the site block:
   ```
   @cloudflare {
       remote_ip 173.245.48.0/20 103.21.244.0/22 103.22.200.0/22 103.31.4.0/22 \
                 141.101.64.0/18 108.162.192.0/18 190.93.240.0/20 188.114.96.0/20 \
                 197.234.240.0/22 198.41.128.0/17 162.158.0.0/15 104.16.0.0/13 \
                 104.24.0.0/14 172.64.0.0/13 131.0.72.0/22
   }
   handle @cloudflare {
       reverse_proxy app:8080 { ... existing block ... }
   }
   handle {
       respond "Direct access blocked" 403
   }
   ```
   `docker compose restart proxy`.

## 4.5 Reduce the upload limit if you don't need 100 MB

100 MB × public internet × concurrent uploads = trivial DoS surface. If your Excel imports are typically < 10 MB:

```properties
# src/main/resources/application-prod.properties
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=20MB
```

Match in `Caddyfile`:
```
request_body { max_size 20MB }
```

Rebuild + restart: `docker compose up -d --build`.

## 4.6 Confirm secure cookie flags

```bash
curl -I -c /tmp/c.txt https://ie-eff.example.com/login
grep -i 'set-cookie' /tmp/c.txt
```

The session cookie should include `Secure; HttpOnly; SameSite=Lax`. Spring sets these automatically when the request comes in over HTTPS and `server.forward-headers-strategy=framework` is configured (it is). Verify it in practice.

---

# PART 5 — Day-2 operations

## 5.1 Automated backups + offsite copy

Local backup (daily 03:00) — append to crontab:

```bash
mkdir -p ~/IE-Eff/backups
crontab -e
```

Add:

```
0 3 * * * cd /home/deploy/IE-Eff && docker compose exec -T db pg_dump -U ie_app -F c shoe_eff_db | gzip > backups/ie-$(date +\%F).dump.gz 2>> backups/backup.log && find backups -name 'ie-*.dump.gz' -mtime +60 -delete
```

**Offsite copy** — pick one:

### Backblaze B2 (cheap, ~$0.005/GB/mo)

```bash
sudo apt -y install rclone
rclone config           # follow prompts: name "b2", type "b2", appKey + appKeyId from B2 dashboard
```

Append to crontab:
```
30 3 * * * rclone copy /home/deploy/IE-Eff/backups b2:ie-eff-backups/$(hostname) --include "ie-*.dump.gz" --max-age 36h
```

### AWS S3

```bash
sudo apt -y install awscli
aws configure          # access key + secret
```

```
30 3 * * * aws s3 sync /home/deploy/IE-Eff/backups s3://your-bucket/ie-eff/ --exclude "*" --include "ie-*.dump.gz"
```

**Test the restore path quarterly.** A backup you've never restored is wishful thinking.

## 5.2 External uptime monitor

Free: https://uptimerobot.com — add a monitor for `https://ie-eff.example.com/login`, check every 5 min, email/SMS alerts. This catches problems your internal monitoring will miss (DNS, certs, ISP).

## 5.3 Log rotation (Docker)

Edit `docker-compose.yml` and add to each service:

```yaml
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "5"
```

`docker compose up -d` to apply.

## 5.4 Updates

Follow [UPDATING.md](UPDATING.md) for app and Postgres updates. Two additions for an internet-exposed host:

- `sudo unattended-upgrades` is enabled by default on Ubuntu Server — keep it on. It auto-applies security patches.
- Watch for Docker / Caddy / Postgres CVEs: subscribe to https://github.com/docker/docker-ce/releases, https://github.com/caddyserver/caddy/releases, https://www.postgresql.org/support/security/.

## 5.5 Snapshots (Hyper-V side)

On the Windows host, take a Hyper-V **Checkpoint** of the VM before any major upgrade. This is your "oh crap, revert everything" button:

- Hyper-V Manager → right-click `ie-eff-ubuntu` → **Checkpoint**.
- Name it: `pre-upgrade-2026-05-15`.
- Delete old checkpoints periodically — they grow over time.

---

# PART 6 — Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Browser shows "ERR_CONNECTION_TIMED_OUT" | Router not forwarding 443, or VM firewall blocking | Re-check router port forward (§2.2) and `sudo ufw status` (§3.3) |
| Caddy logs "no such host" or "ACME challenge failed" | DNS A record not propagated, or Cloudflare proxy is on | `dig ie-eff.example.com +short` from outside, turn off Cloudflare proxy until cert issued |
| Cert obtained but site shows "Bad Gateway" | App container not healthy yet | `docker compose logs -f app`, wait for Flyway + Spring start (~60s) |
| `IllegalStateException: Seed user passwords are not configured` | Missing env vars in `.env` | Confirm `APP_DEFAULT_ADMIN_PASSWORD` and `APP_DEFAULT_MANAGER_PASSWORD` set, `docker compose up -d` |
| HikariCP "password authentication failed" | DB volume contains pre-existing Postgres data with a different password | First boot only: `docker compose down -v` (wipes volume), fix `.env`, `up -d`. Never do this after data is loaded — restore from backup instead. |
| Excel upload returns 413 | Caddy `request_body max_size` doesn't match Spring's `max-request-size` | Make them equal in `Caddyfile` and `application-prod.properties`, restart both |
| SSH suddenly refuses key auth | fail2ban banned your IP after typos | From the Hyper-V console: `sudo fail2ban-client unban <your-ip>` |
| VM IP changed and port-forwards broke | DHCP lease expired without reservation | Pin the IP per §2.1 |
| `docker compose up` fails with "permission denied on docker.sock" | You didn't re-login after adding yourself to docker group | `exec sudo -i -u $USER` or log out + back in |
| Public IP changed (no static IP from ISP) | ISP gave you a new dynamic IP | Use a DDNS service (no-ip.com, duckdns.org) and update DNS to a CNAME pointing at the DDNS hostname |

---

# Sanity checklist before announcing the URL

- [ ] `https://ie-eff.example.com/login` loads with a valid (green-lock) cert from a public browser
- [ ] `curl -I https://ie-eff.example.com` shows `HTTP/2 200`
- [ ] Default admin / manager accounts have been renamed or have 16+ char random passwords
- [ ] `/register` is locked down (or removed) per §4.2
- [ ] `RateLimitInterceptor` is wired to `/login` per §4.3
- [ ] (Optional) Cloudflare proxy is on, mode = Full (strict)
- [ ] `.env` contains only env-var-style placeholders in git — no real secrets committed
- [ ] First backup file exists in `backups/`
- [ ] Offsite backup destination has received its first sync
- [ ] UptimeRobot monitor is green
- [ ] Hyper-V Checkpoint `initial-deployment-<date>` taken on the Windows host
- [ ] Document the public IP, domain, VM LAN IP, and admin contact in a place that survives a disaster (not just on this server)

---

# Quick reference (after deployment)

| Task | Command (inside VM) |
|---|---|
| Tail app logs | `docker compose logs -f app` |
| Tail Caddy logs | `docker compose logs -f proxy` |
| Restart app | `docker compose restart app` |
| Manual backup | `docker compose exec -T db pg_dump -U ie_app -F c shoe_eff_db \| gzip > backups/manual-$(date +%F-%H%M).dump.gz` |
| DB shell | `docker compose exec db psql -U ie_app shoe_eff_db` |
| Update to latest code | `git pull && docker compose up -d --build app` |
| Check cert expiry | `curl -vI https://ie-eff.example.com 2>&1 \| grep -i 'expire'` |
| Reload Caddy after Caddyfile change | `docker compose restart proxy` |
| Hyper-V Checkpoint (from Windows host) | Hyper-V Manager → right-click VM → Checkpoint |

---

# See also

- **[README.md](README.md)** — project overview, dev setup, env-var reference
- **[DEPLOY.md](DEPLOY.md)** — generic Docker deployment (LAN)
- **[UPDATING.md](UPDATING.md)** — how to apply app/Postgres updates safely
- **[docs/SERVER_CONFIG.md](docs/SERVER_CONFIG.md)** — bare-metal Windows sizing + tuning (legacy path)
