-- ============================================================
-- V14__add_voc_recipe_formula_and_article.sql
-- Make the VOC "DB" page a faithful copy of the workbook "DB" sheet:
--   1. voc_standard_rate.formula — the raw reciprocal expression
--      (e.g. "1/1300+1/1500+1/1400"); kg_per_pair stays its evaluated value.
--   2. voc_recipe_article — per-article identity columns shown to the left of
--      the chemical matrix: model code (col C), model name (col D), E/F bases.
-- ============================================================

ALTER TABLE voc_standard_rate ADD COLUMN formula VARCHAR(255);

CREATE TABLE voc_recipe_article (
    article_no  VARCHAR(40)      NOT NULL,
    model_code  VARCHAR(60),
    model_name  VARCHAR(160),
    base_e      DOUBLE PRECISION,
    base_f      DOUBLE PRECISION,
    created_at  TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP(6),
    CONSTRAINT pk_voc_recipe_article PRIMARY KEY (article_no)
);

-- Backfill matrix rows from existing recipe data so current data shows up at once;
-- model code/name and E/F stay null until the wide "DB" sheet is re-imported.
INSERT INTO voc_recipe_article (article_no)
SELECT DISTINCT article_no FROM voc_standard_rate
ON CONFLICT (article_no) DO NOTHING;
