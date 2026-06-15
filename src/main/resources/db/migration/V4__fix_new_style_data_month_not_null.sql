-- ============================================================
-- V4__fix_new_style_data_month_not_null.sql
-- Backfill data_month NULLs then enforce NOT NULL constraint
-- Bọc điều kiện: trên DB rỗng new_style_entry chưa tồn tại (Hibernate-era table,
-- V17 catch-up sẽ tạo shape cuối với NOT NULL sẵn) → bỏ qua an toàn.
-- ============================================================

DO $$
BEGIN
    IF to_regclass('new_style_entry') IS NOT NULL THEN
        -- Backfill any existing NULLs with current month as a safe default
        UPDATE new_style_entry
        SET data_month = TO_CHAR(NOW(), 'YYYY-MM')
        WHERE data_month IS NULL;

        ALTER TABLE new_style_entry
            ALTER COLUMN data_month SET NOT NULL;
    END IF;
END $$;
