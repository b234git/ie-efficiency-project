-- "Throw" column of the VOC "Actual" sheet — recorded for parity with the workbook.
-- Not part of the net-VOC calc (= (quantity_kg - reuse_kg) * voc_factor); kept for completeness.
ALTER TABLE voc_consumption ADD COLUMN IF NOT EXISTS throw_kg double precision NOT NULL DEFAULT 0;
