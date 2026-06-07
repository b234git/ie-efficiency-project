-- ============================================================
-- V12__add_voc_tables.sql
-- VOC (Volatile Organic Compound) tracking module.
--   voc_chemical    : chemical master (VOC factor, price) — mirrors the "R" sheet
--   voc_consumption : daily per-line chemical usage      — mirrors the "Actual" sheet
-- Output for VOC g/pair is read from the existing daily_production table.
-- ============================================================

CREATE TABLE voc_chemical (
    id             BIGSERIAL        NOT NULL,
    code           VARCHAR(40)      NOT NULL,                 -- '577NT3', 'GH-7055'
    material_type  VARCHAR(20)      NOT NULL DEFAULT 'SOLVENT', -- 'WATER' | 'SOLVENT'
    classification VARCHAR(40),                               -- Adhesive / Hot melt / Primer ...
    manufacturer   VARCHAR(60),
    voc_factor     DOUBLE PRECISION NOT NULL DEFAULT 0,       -- 0..1
    price_per_kg   DOUBLE PRECISION,
    active         BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP(6),
    CONSTRAINT pk_voc_chemical PRIMARY KEY (id),
    CONSTRAINT uq_voc_chemical_code UNIQUE (code)
);

CREATE TABLE voc_consumption (
    id              BIGSERIAL        NOT NULL,
    version         BIGINT,
    production_date DATE             NOT NULL,
    section         VARCHAR(20)      NOT NULL DEFAULT 'SEW',
    line            VARCHAR(10)      NOT NULL,                -- '1A' (matches daily_production.line)
    chemical_code   VARCHAR(40)      NOT NULL,
    quantity_kg     DOUBLE PRECISION NOT NULL DEFAULT 0,
    reuse_kg        DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_voc_consumption PRIMARY KEY (id),
    CONSTRAINT uq_voc_consumption UNIQUE (production_date, section, line, chemical_code)
);

CREATE INDEX idx_voc_cons_date          ON voc_consumption (production_date);
CREATE INDEX idx_voc_cons_date_sec_line ON voc_consumption (production_date, section, line);

-- ─── Feature + role grants (mirrors V8 DB-driven authorization) ───
INSERT INTO features (feature_key, display_name, url_patterns, http_methods, category, priority) VALUES
    ('VOC', 'VOC', '/voc/**,/api/v1/voc/**', NULL, 'Analytics', 500);

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
CROSS JOIN features f
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_MANAGER')
  AND f.feature_key = 'VOC';
