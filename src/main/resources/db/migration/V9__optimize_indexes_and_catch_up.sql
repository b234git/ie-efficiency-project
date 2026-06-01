-- ============================================================
-- V9__optimize_indexes_and_catch_up.sql
--
-- 1) Catches up schema changes that previously ran via the
--    SchemaPatcher and RolePermissionSeeder runtime hacks
--    (when Flyway was disabled). All statements are idempotent.
-- 2) Adds optimization indexes:
--    - FK indexes on split_entry for fast user cascade-delete
--    - Composite (article_no, data_month) for MasterDb batch lookup
--    - Drops the redundant idx_mdb_ref
-- ============================================================

-- ─── Catch up: users.enabled (was added by SchemaPatcher) ────────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- ─── Catch up: V5 composite indexes (ddl-auto=update may have skipped) ───────
CREATE INDEX IF NOT EXISTS idx_dp_section_line
    ON daily_production (section, line);
CREATE INDEX IF NOT EXISTS idx_dp_date_section_line
    ON daily_production (production_date, section, line);
CREATE INDEX IF NOT EXISTS idx_se_section_line
    ON split_entry (section, line);

ALTER TABLE daily_production
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE split_entry
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ─── Catch up: V8 role/feature schema ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS roles (
    id           BIGSERIAL    NOT NULL,
    name         VARCHAR(50)  NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    is_system    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS features (
    id           BIGSERIAL    NOT NULL,
    feature_key  VARCHAR(64)  NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    url_patterns VARCHAR(500) NOT NULL,
    http_methods VARCHAR(50),
    category     VARCHAR(50),
    priority     INT          NOT NULL DEFAULT 100,
    CONSTRAINT pk_features PRIMARY KEY (id),
    CONSTRAINT uq_features_key UNIQUE (feature_key)
);

CREATE TABLE IF NOT EXISTS role_features (
    role_id    BIGINT NOT NULL,
    feature_id BIGINT NOT NULL,
    CONSTRAINT pk_role_features PRIMARY KEY (role_id, feature_id),
    CONSTRAINT fk_rf_role    FOREIGN KEY (role_id)    REFERENCES roles(id)    ON DELETE CASCADE,
    CONSTRAINT fk_rf_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_role_features_role ON role_features (role_id);

CREATE TABLE IF NOT EXISTS user_feature_overrides (
    id         BIGSERIAL    NOT NULL,
    user_id    BIGINT       NOT NULL,
    feature_id BIGINT       NOT NULL,
    granted    BOOLEAN      NOT NULL,
    CONSTRAINT pk_user_feature_overrides PRIMARY KEY (id),
    CONSTRAINT uq_user_feature_overrides_user_feature UNIQUE (user_id, feature_id),
    CONSTRAINT fk_ufo_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_ufo_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ufo_feature ON user_feature_overrides (feature_id);

-- ─── New: FK indexes for fast user cascade-delete on split_entry (P0 #2) ─────
CREATE INDEX IF NOT EXISTS idx_se_manpower_filled_by ON split_entry (manpower_filled_by);
CREATE INDEX IF NOT EXISTS idx_se_output_filled_by   ON split_entry (output_filled_by);
CREATE INDEX IF NOT EXISTS idx_se_article_filled_by  ON split_entry (article_filled_by);

-- ─── New: Composite index for primary MasterDb lookup pattern (P0 #3) ────────
-- Matches findByArticleNoInAndDataMonthOrderByRefAsc — the hot path in
-- EfficiencyCalculatorService.populateEfficiencyMetricsBatch.
CREATE INDEX IF NOT EXISTS idx_mdb_article_month ON master_db (article_no, data_month);

-- Drop redundant idx_mdb_ref: uq_master_db_ref_month (ref, data_month) already
-- serves ref-prefix lookups via its underlying unique index.
DROP INDEX IF EXISTS idx_mdb_ref;

-- ─── Seed default features (idempotent) ──────────────────────────────────────
INSERT INTO features (feature_key, display_name, url_patterns, http_methods, category, priority) VALUES
    ('SYSTEM_HEALTH',          'System Health',          '/actuator/**,/api/v1/system-health/**',                    NULL,          'System',     900),
    ('ADMIN',                  'User Management',        '/admin/**,/api/v1/admin/**',                               NULL,          'System',     900),
    ('MASTERDB',               'Master DB',              '/masterdb/**,/api/v1/masterdb/**',                         NULL,          'Data',       500),
    ('DASHBOARD',              'Dashboard',              '/dashboard/**,/api/v1/dashboard/**',                       NULL,          'Analytics',  500),
    ('REPORT',                 'Reports',                '/report/**,/api/v1/reports/**',                            NULL,          'Analytics',  500),
    ('ENTRY_MUTATE',           'Entry Edit/Delete',      '/entry/edit,/entry/admin-delete,/entry/delete',            NULL,          'Production', 800),
    ('ENTRY',                  'Entry',                  '/entry/**,/api/v1/entries/**',                             NULL,          'Production', 400),
    ('SPLIT_ENTRY_DELETE',     'Split Entry Delete',     '/split-entry/delete/**,/api/v1/split-entries/bulk-delete', 'POST,DELETE', 'Production', 800),
    ('SPLIT_ENTRY_API_DELETE', 'Split Entry API Delete', '/api/v1/split-entries/**',                                 'DELETE',      'Production', 850),
    ('SPLIT_ENTRY_OUTPUT',     'Split Entry Output',     '/split-entry/output,/split-entry/output/**',               NULL,          'Production', 700),
    ('SPLIT_ENTRY_ARTICLES',   'Split Entry Articles',   '/split-entry/articles,/split-entry/articles/**',           NULL,          'Production', 700),
    ('SPLIT_ENTRY',            'Split Entry',            '/split-entry/**,/api/v1/split-entries/**',                 NULL,          'Production', 300),
    ('NOTIFICATIONS',          'Notifications',          '/notifications/**,/api/v1/notifications/**',               NULL,          'Common',     500),
    ('EFF_CONFIG',             'EFF Config',             '/eff-config/**,/api/v1/eff-config/**',                     NULL,          'Setup',      500),
    ('WEEKLY_TRACKING',        'Weekly Tracking',        '/weekly-tracking/**,/api/v1/weekly-tracking/**',           NULL,          'Production', 500),
    ('NEW_STYLE',              'New Style',              '/new-style/**,/api/v1/new-styles/**',                      NULL,          'Production', 500),
    ('SALARY',                 'Salary',                 '/salary/**,/api/v1/salary/**',                             NULL,          'Analytics',  500),
    ('IMPORTS',                'Imports',                '/api/v1/imports/**',                                       NULL,          'Data',       500)
ON CONFLICT (feature_key) DO NOTHING;

-- ─── Seed default roles ──────────────────────────────────────────────────────
INSERT INTO roles (name, display_name, is_system) VALUES
    ('ROLE_ADMIN',   'Administrator', TRUE),
    ('ROLE_MANAGER', 'Manager',       TRUE),
    ('ROLE_USER',    'User',          TRUE)
ON CONFLICT (name) DO NOTHING;

-- ─── Seed default grants (idempotent) ────────────────────────────────────────
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name = 'ROLE_MANAGER'
  AND f.feature_key NOT IN ('SYSTEM_HEALTH', 'ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name = 'ROLE_USER'
  AND f.feature_key = 'SPLIT_ENTRY'
ON CONFLICT DO NOTHING;
