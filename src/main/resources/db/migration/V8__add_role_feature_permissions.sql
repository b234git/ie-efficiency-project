-- ============================================================
-- V8__add_role_feature_permissions.sql
-- Dynamic role + feature permission system.
-- Replaces hardcoded URL->role matchers in SecurityConfig with
-- DB-driven authorization. Default seeding mirrors the previous
-- hardcoded behavior so existing users see no access change.
-- ============================================================

CREATE TABLE roles (
    id           BIGSERIAL    NOT NULL,
    name         VARCHAR(50)  NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    is_system    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE features (
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

CREATE TABLE role_features (
    role_id    BIGINT NOT NULL,
    feature_id BIGINT NOT NULL,
    CONSTRAINT pk_role_features PRIMARY KEY (role_id, feature_id),
    CONSTRAINT fk_rf_role    FOREIGN KEY (role_id)    REFERENCES roles(id)    ON DELETE CASCADE,
    CONSTRAINT fk_rf_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE
);

CREATE INDEX idx_role_features_role ON role_features (role_id);

-- ─── Seed system roles ──────────────────────────────────────
INSERT INTO roles (name, display_name, is_system) VALUES
    ('ROLE_ADMIN',   'Administrator', TRUE),
    ('ROLE_MANAGER', 'Manager',       TRUE),
    ('ROLE_USER',    'User',          TRUE);

-- ─── Seed features ──────────────────────────────────────────
-- Higher priority = checked first. More specific patterns get
-- higher priority so they shadow general patterns when matching.
INSERT INTO features (feature_key, display_name, url_patterns, http_methods, category, priority) VALUES
    ('SYSTEM_HEALTH',         'System Health',           '/actuator/**,/api/v1/system-health/**',         NULL,         'System',     900),
    ('ADMIN',                 'User Management',         '/admin/**,/api/v1/admin/**',                    NULL,         'System',     900),
    ('MASTERDB',              'Master DB',               '/masterdb/**,/api/v1/masterdb/**',              NULL,         'Data',       500),
    ('DASHBOARD',             'Dashboard',               '/dashboard/**,/api/v1/dashboard/**',            NULL,         'Analytics',  500),
    ('REPORT',                'Reports',                 '/report/**,/api/v1/reports/**',                 NULL,         'Analytics',  500),
    ('ENTRY_MUTATE',          'Entry Edit/Delete',       '/entry/edit,/entry/admin-delete,/entry/delete', NULL,         'Production', 800),
    ('ENTRY',                 'Entry',                   '/entry/**,/api/v1/entries/**',                  NULL,         'Production', 400),
    ('SPLIT_ENTRY_DELETE',    'Split Entry Delete',      '/split-entry/delete/**,/api/v1/split-entries/bulk-delete', 'POST,DELETE', 'Production', 800),
    ('SPLIT_ENTRY_API_DELETE','Split Entry API Delete',  '/api/v1/split-entries/**',                      'DELETE',     'Production', 850),
    ('SPLIT_ENTRY_OUTPUT',    'Split Entry Output',      '/split-entry/output,/split-entry/output/**',    NULL,         'Production', 700),
    ('SPLIT_ENTRY_ARTICLES',  'Split Entry Articles',    '/split-entry/articles,/split-entry/articles/**',NULL,         'Production', 700),
    ('SPLIT_ENTRY',           'Split Entry',             '/split-entry/**,/api/v1/split-entries/**',      NULL,         'Production', 300),
    ('NOTIFICATIONS',         'Notifications',           '/notifications/**,/api/v1/notifications/**',    NULL,         'Common',     500),
    ('EFF_CONFIG',            'EFF Config',              '/eff-config/**,/api/v1/eff-config/**',          NULL,         'Setup',      500),
    ('WEEKLY_TRACKING',       'Weekly Tracking',         '/weekly-tracking/**,/api/v1/weekly-tracking/**',NULL,         'Production', 500),
    ('NEW_STYLE',             'New Style',               '/new-style/**,/api/v1/new-styles/**',           NULL,         'Production', 500),
    ('SALARY',                'Salary',                  '/salary/**,/api/v1/salary/**',                  NULL,         'Analytics',  500),
    ('IMPORTS',               'Imports',                 '/api/v1/imports/**',                            NULL,         'Data',       500);

-- ─── Seed default role_features (mirrors prior SecurityConfig) ─

-- ADMIN gets every feature
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name = 'ROLE_ADMIN';

-- MANAGER gets every feature EXCEPT system-only ones
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name = 'ROLE_MANAGER'
  AND f.feature_key NOT IN ('SYSTEM_HEALTH', 'ADMIN');

-- USER gets only SPLIT_ENTRY (view + non-destructive ops)
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name = 'ROLE_USER'
  AND f.feature_key = 'SPLIT_ENTRY';
