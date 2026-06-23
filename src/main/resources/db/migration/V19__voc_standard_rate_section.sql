-- ============================================================
-- V19__voc_standard_rate_section.sql
-- VOC recipe is per-section: each section (SEW/ASSY/SF) defines its own
-- chemicals + dosages, and the same article can appear in several sections
-- with different rates. The old key (article_no, chemical_code) collided
-- across sections (e.g. 98NH1 differs 30x between SEW and ASSY). Add section
-- to the key. Existing data is SEW-only, so backfill 'SEW'.
-- Idempotent + name-agnostic: the table may have been created by Hibernate
-- (unique constraint auto-named) or by Flyway V13/V17 (uq_voc_std_rate).
-- ============================================================

ALTER TABLE voc_standard_rate ADD COLUMN IF NOT EXISTS section VARCHAR(20) NOT NULL DEFAULT 'SEW';

-- Drop whatever UNIQUE constraint currently sits on exactly (article_no, chemical_code),
-- regardless of its name.
DO $$
DECLARE c record;
BEGIN
  FOR c IN
    SELECT con.conname
    FROM pg_constraint con
    WHERE con.conrelid = 'voc_standard_rate'::regclass AND con.contype = 'u'
      AND (SELECT array_agg(att.attname::text ORDER BY att.attname)
           FROM unnest(con.conkey) k
           JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = k)
          = ARRAY['article_no', 'chemical_code']
  LOOP
    EXECUTE format('ALTER TABLE voc_standard_rate DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

ALTER TABLE voc_standard_rate DROP CONSTRAINT IF EXISTS uq_voc_std_rate;
ALTER TABLE voc_standard_rate ADD CONSTRAINT uq_voc_std_rate UNIQUE (section, article_no, chemical_code);
