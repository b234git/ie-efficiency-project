# Updating IE-Eff After Deployment

How to apply updates to a Docker-deployed IE-Eff stack. Most updates are routine; DB-related ones need care. Pick the scenario below that matches your change.

---

## 1. App code change (most common)

Pull the new release, rebuild the `app` image, recreate just that container. The DB volume is untouched.

```bash
cd ~/IE-Eff
git fetch && git pull           # or `git checkout <tag>`
docker compose build app        # rebuild image
docker compose up -d app        # recreate container; db keeps running
docker compose logs -f app      # watch Flyway migrate + Spring start
```

Downtime: ~15–40 s while the new container starts. Compose tears down the old container only once the new one passes its healthcheck.

> If you have a release tag workflow, prefer `git checkout v1.2.0` over `git pull` so you know exactly what shipped.

---

## 2. Config / `.env` change

Env vars are read at JVM start, so the app must restart. The DB doesn't need to.

```bash
$EDITOR .env
docker compose up -d app        # picks up new env; recreates container
```

If you changed a `db` service env var (rare — e.g. `POSTGRES_PASSWORD`), see [§5 Rotating secrets](#5-rotating-secrets-db-password-or-seed-user-passwords).

---

## 3. Database schema change

You don't run migrations manually — **Flyway runs on every app boot** and is idempotent. So the §1 procedure already migrates the schema.

What to watch for:

- `docker compose logs app | grep -i flyway` should show `Successfully applied N migrations` (or `Schema is up to date`).
- If Flyway fails (checksum mismatch, validation error), the app exits — your DB is left at the previous version, so it's safe.
- **Always back up before a release that includes new `V*.sql` files** — see [§7 Backup before any non-trivial update](#7-backup-before-any-non-trivial-update).

---

## 4. Updating the base image (security patches)

JRE / Postgres / Nginx images get CVE patches periodically. Refresh them:

```bash
docker compose pull             # pulls newer postgres:15-alpine / nginx:1.27-alpine
docker compose build --pull app # forces re-pull of the JRE base in Dockerfile
docker compose up -d
```

For Postgres **minor** versions (15.5 → 15.7), this is safe — same data directory format. For **major** versions (15 → 16) it is not — see [§6 Postgres major version upgrade](#6-postgres-major-version-upgrade-15--16).

---

## 5. Rotating secrets (DB password or seed user passwords)

### DB password

Two steps because Postgres stores its own copy:

```bash
# 1. Change in Postgres
docker compose exec db psql -U ie_app -d shoe_eff_db \
  -c "ALTER USER ie_app WITH PASSWORD 'NEW_STRONG_VALUE';"

# 2. Update .env, then restart the app
$EDITOR .env                    # set DB_PASSWORD=NEW_STRONG_VALUE
docker compose up -d app
```

> Do **not** change `POSTGRES_PASSWORD` in the compose env and `up -d db` — that env var only seeds the initial DB and is ignored after the volume exists. Use `ALTER USER` as above.

### Seed user passwords

`APP_DEFAULT_ADMIN_PASSWORD` and `APP_DEFAULT_MANAGER_PASSWORD` are consumed only on first boot, when the `admin` / `manager` rows don't yet exist. After that they're inert — the password lives in the `users` table, BCrypt-hashed. To change an admin's password later, log in as admin and use the UI.

---

## 6. Postgres major version upgrade (15 → 16)

This is the one update that needs care because the data directory format changes.

```bash
# 1. Stop app
docker compose stop app

# 2. Dump current DB
docker compose exec -T db pg_dump -U ie_app -F c shoe_eff_db \
  > backups/pre-upgrade-$(date +%F).dump

# 3. Edit docker-compose.yml: image: postgres:16-alpine

# 4. Wipe ONLY the db volume (this is the destructive bit — backup must be good)
docker compose down
docker volume rm ie-eff_pgdata

# 5. Bring up new db with empty volume, restore dump
docker compose up -d db
sleep 10
docker compose exec -T db pg_restore -U ie_app -d shoe_eff_db --clean --if-exists \
  < backups/pre-upgrade-*.dump

# 6. Start app
docker compose up -d app
```

Test in a staging copy first.

---

## 7. Backup before any non-trivial update

```bash
docker compose exec -T db pg_dump -U ie_app -F c shoe_eff_db \
  > backups/pre-update-$(date +%F-%H%M).dump
```

Keep at least the last 3 pre-update dumps separate from the daily rotation.

---

## 8. Rollback

The image you ran before is still in your local Docker cache as long as you didn't `docker image prune -a`.

### Code rollback (no schema change)

```bash
git checkout <previous-tag>
docker compose up -d --build app
```

### Code + schema rollback (last release added a Flyway migration)

Flyway has no automatic down-migration. You need to restore from the pre-update dump:

```bash
docker compose stop app
docker compose exec -T db pg_restore -U ie_app -d shoe_eff_db --clean --if-exists \
  < backups/pre-update-<timestamp>.dump
git checkout <previous-tag>
docker compose up -d --build app
```

This is why the [§7](#7-backup-before-any-non-trivial-update) backup matters.

---

## Routine update checklist

**Before:**
- [ ] `docker compose ps` — both services healthy
- [ ] `docker compose exec -T db pg_dump … > backups/pre-update-*.dump`
- [ ] Read the release notes — any new `V*.sql` migrations or env vars?
- [ ] If new required env var → update `.env` first

**Update:**
- [ ] `git pull` (or checkout tag) → `docker compose up -d --build app`
- [ ] `docker compose logs -f app` until you see `Started ShoeManagementProject1Application in N seconds`

**After:**
- [ ] `curl -s http://127.0.0.1:8081/actuator/health` → `"status":"UP"`
- [ ] Smoke-test: log in, open one report, do a small Excel import
- [ ] `docker image prune` to remove the old image tag once you're confident (skip this for ~24 h if you might rollback)

---

## What you'll never need to do manually

- Run Flyway by hand → app does it on boot.
- Recreate the `db` container for normal app updates → leave it running.
- Restart the proxy unless `nginx.conf` or certs changed → `docker compose up -d proxy` only when needed.

---

## See also

- **[DEPLOY.md](DEPLOY.md)** — first-time deployment
- **[README.md](README.md)** — project overview and dev setup
- **[docs/SERVER_CONFIG.md](docs/SERVER_CONFIG.md)** — bare-metal sizing and tuning
