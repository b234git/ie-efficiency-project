-- ============================================================
-- V13__add_voc_standard_rate.sql
-- VOC standard recipe — the "DB" sheet of the VOC workbook.
-- Per article, the standard kg-per-pair of each chemical. Used to
-- derive the allowance (output × kg_per_pair × 1.1) that actual
-- consumption is reconciled against on the % report.
-- ============================================================

CREATE TABLE voc_standard_rate (
    id            BIGSERIAL        NOT NULL,
    article_no    VARCHAR(40)      NOT NULL,
    chemical_code VARCHAR(40)      NOT NULL,
    kg_per_pair   DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP(6),
    CONSTRAINT pk_voc_standard_rate PRIMARY KEY (id),
    CONSTRAINT uq_voc_std_rate UNIQUE (article_no, chemical_code)
);

CREATE INDEX idx_voc_std_rate_article ON voc_standard_rate (article_no);
