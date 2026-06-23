-- ============================================================
-- V20__voc_production.sql
-- Per-(date, section, line, article) production output from each VOC workbook's
-- "Data" sheet — the source the Excel uses to compute allowance (output × recipe).
-- output is pre-apportioned per article by the sheet's slot weights at import time,
-- so reconciliation stays a plain Σ(output × recipe). Section is the VOC section
-- (SEW/ASSY/SF) as written in the file. Idempotent for re-run on existing DBs.
-- ============================================================

CREATE TABLE IF NOT EXISTS voc_production (
    id              BIGSERIAL        NOT NULL,
    version         BIGINT,
    production_date DATE             NOT NULL,
    section         VARCHAR(20)      NOT NULL DEFAULT 'SEW',
    line            VARCHAR(10)      NOT NULL,
    article_no      VARCHAR(40)      NOT NULL,
    output          DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_voc_production PRIMARY KEY (id),
    CONSTRAINT uq_voc_production UNIQUE (production_date, section, line, article_no)
);

CREATE INDEX IF NOT EXISTS idx_voc_prod_date          ON voc_production (production_date);
CREATE INDEX IF NOT EXISTS idx_voc_prod_date_sec_line ON voc_production (production_date, section, line);
