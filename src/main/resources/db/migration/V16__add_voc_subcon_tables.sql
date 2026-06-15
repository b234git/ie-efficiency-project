-- ============================================================
-- V16__add_voc_subcon_tables.sql
-- SUBCON (subcontractor) chemical tracking — workbook "CEMENT"/"ACTUAL CEMENT".
-- Header/detail split: OUTPUT is stored ONCE per (date, subcontractor, article)
-- in voc_subcon_entry (entered directly — no daily_production link), and the
-- per-chemical actuals hang off it in voc_subcon_detail. Standard = output ×
-- recipe(article, chem); shortage = standard − actual (computed in VocService).
-- ============================================================

CREATE TABLE voc_subcon_entry (
    id              BIGSERIAL        NOT NULL,
    version         BIGINT,
    production_date DATE             NOT NULL,
    subcontractor   VARCHAR(40)      NOT NULL,            -- e.g. 'TH2'
    article_no      VARCHAR(40)      NOT NULL,
    output          INTEGER          NOT NULL DEFAULT 0,  -- pairs (entered, not from production)
    created_at      TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP(6),
    CONSTRAINT pk_voc_subcon_entry PRIMARY KEY (id),
    CONSTRAINT uq_voc_subcon_entry UNIQUE (production_date, subcontractor, article_no)
);
CREATE INDEX idx_voc_subcon_entry_date ON voc_subcon_entry (production_date);

CREATE TABLE voc_subcon_detail (
    id            BIGSERIAL        NOT NULL,
    entry_id      BIGINT           NOT NULL,
    chemical_code VARCHAR(40)      NOT NULL,
    actual_kg     DOUBLE PRECISION NOT NULL DEFAULT 0,
    reuse_kg      DOUBLE PRECISION NOT NULL DEFAULT 0,
    CONSTRAINT pk_voc_subcon_detail PRIMARY KEY (id),
    CONSTRAINT uq_voc_subcon_detail UNIQUE (entry_id, chemical_code),
    CONSTRAINT fk_voc_subcon_detail_entry FOREIGN KEY (entry_id)
        REFERENCES voc_subcon_entry (id) ON DELETE CASCADE
);
CREATE INDEX idx_voc_subcon_detail_entry ON voc_subcon_detail (entry_id);
