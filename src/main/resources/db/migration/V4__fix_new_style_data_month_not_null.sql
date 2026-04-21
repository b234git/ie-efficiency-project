-- ============================================================
-- V4__fix_new_style_data_month_not_null.sql
-- Backfill data_month NULLs then enforce NOT NULL constraint
-- ============================================================

-- Backfill any existing NULLs with current month as a safe default
UPDATE new_style_entry
SET data_month = TO_CHAR(NOW(), 'YYYY-MM')
WHERE data_month IS NULL;

ALTER TABLE new_style_entry
    ALTER COLUMN data_month SET NOT NULL;
