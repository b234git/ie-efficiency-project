-- ============================================================================
-- IE-Eff PostgreSQL bootstrap script
-- ----------------------------------------------------------------------------
-- Run ONCE on a fresh PostgreSQL 15+ instance, BEFORE first application start.
-- Flyway (V1..V7 in src/main/resources/db/migration/) creates all tables and
-- indexes automatically on the first boot — do not run any DDL here for
-- application tables.
--
-- Connect as a superuser (postgres) when running this file, e.g.:
--   psql -U postgres -h localhost -f db-bootstrap.sql
--
-- After this script:
--   * Set env vars on the server:
--       DB_URL=jdbc:postgresql://localhost:5432/shoe_eff_db
--       DB_USERNAME=ie_app
--       DB_PASSWORD=<the password you set in step 2 below>
--       APP_DEFAULT_ADMIN_PASSWORD=<strong random>
--       APP_DEFAULT_MANAGER_PASSWORD=<strong random>
--   * Start the app with --spring.profiles.active=prod
--   * Flyway will create the schema on first connect.
-- ============================================================================

-- ── 1. Create the application role ─────────────────────────────────────────
-- The role is the DB user the Spring Boot app authenticates as.
-- REPLACE the placeholder password before running this file.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ie_app') THEN
        CREATE ROLE ie_app
            WITH LOGIN
                 PASSWORD 'CHANGE_ME_BEFORE_RUNNING'
                 NOSUPERUSER
                 NOCREATEDB
                 NOCREATEROLE
                 NOREPLICATION
                 CONNECTION LIMIT 60;
    END IF;
END
$$;

-- ── 2. Create the database ─────────────────────────────────────────────────
-- CREATE DATABASE cannot run inside a DO block / transaction, so it is
-- guarded with \gexec for idempotency.
SELECT 'CREATE DATABASE shoe_eff_db
        OWNER ie_app
        ENCODING ''UTF8''
        LC_COLLATE ''en_US.UTF-8''
        LC_CTYPE   ''en_US.UTF-8''
        TEMPLATE template0'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'shoe_eff_db')
\gexec

-- ── 3. Tighten default privileges on the new DB ────────────────────────────
\connect shoe_eff_db

-- Revoke the default PUBLIC grant on the public schema so only ie_app may use it.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT  ALL ON SCHEMA public TO ie_app;
ALTER  SCHEMA public OWNER TO ie_app;

-- Make sure objects Flyway / Hibernate create later are also owned by ie_app.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES, TRIGGER ON TABLES TO ie_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO ie_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO ie_app;

-- ── 4. (Optional) Read-only role for ad-hoc BI / reporting ─────────────────
-- Uncomment if a separate analyst account is needed.
-- DO $$
-- BEGIN
--     IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ie_readonly') THEN
--         CREATE ROLE ie_readonly
--             WITH LOGIN PASSWORD 'CHANGE_ME_TOO'
--                  NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
--                  CONNECTION LIMIT 10;
--     END IF;
-- END
-- $$;
-- GRANT CONNECT ON DATABASE shoe_eff_db TO ie_readonly;
-- GRANT USAGE   ON SCHEMA public        TO ie_readonly;
-- GRANT SELECT  ON ALL TABLES IN SCHEMA public TO ie_readonly;
-- ALTER DEFAULT PRIVILEGES FOR ROLE ie_app IN SCHEMA public
--     GRANT SELECT ON TABLES TO ie_readonly;

-- ── 5. Sanity check ────────────────────────────────────────────────────────
\du ie_app
\l shoe_eff_db

-- Done. Start the application — Flyway will materialise the schema.
