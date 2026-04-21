-- ============================================================
-- V5__add_indexes_and_optimistic_locking.sql
-- Phase 1: Composite indexes + optimistic locking version columns
-- ============================================================

-- ─── Composite indexes for daily_production ──────────────────────────────────
-- Thường query filter theo section + line
CREATE INDEX IF NOT EXISTS idx_dp_section_line
    ON daily_production (section, line);

-- Query báo cáo filter theo ngày + section + line
CREATE INDEX IF NOT EXISTS idx_dp_date_section_line
    ON daily_production (production_date, section, line);

-- ─── Composite index for split_entry ─────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_se_section_line
    ON split_entry (section, line);

-- ─── Index cho master_db.ref ──────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_mdb_ref
    ON master_db (ref);

-- ─── Optimistic locking — version columns ─────────────────────────────────────
-- Ngăn 2 user sửa cùng 1 bản ghi cùng lúc (last-write-wins)
ALTER TABLE daily_production
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE split_entry
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
