-- ============================================================
-- V15__voc_chemical_ref_columns.sql
-- Bring voc_chemical to full parity with the workbook "R" sheet:
-- add the price/inventory reference columns that the page was missing.
--   unit              — R col F "UNIT"
--   container_size_kg — R col G "kg" (container weight)
--   container_price   — R col I "Price" (per container)
--   price_ref_note    — R col K "Date" — holds real dates AND free text (e.g. "TAIWAN"),
--                       so stored as text rather than DATE to avoid data loss / parse errors.
-- All nullable: existing rows and the legacy 6-column import stay valid.
-- ============================================================

ALTER TABLE voc_chemical ADD COLUMN unit              VARCHAR(20);
ALTER TABLE voc_chemical ADD COLUMN container_size_kg DOUBLE PRECISION;
ALTER TABLE voc_chemical ADD COLUMN container_price   DOUBLE PRECISION;
ALTER TABLE voc_chemical ADD COLUMN price_ref_note    VARCHAR(60);
