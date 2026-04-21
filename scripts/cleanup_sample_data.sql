-- =============================================================================
-- cleanup_sample_data.sql
-- Xóa toàn bộ dữ liệu mẫu được seed bởi SampleDataInitializer và DataLoader
-- Chạy một lần trước khi deploy lên production
-- Compatible với PostgreSQL
-- =============================================================================

BEGIN;

-- 1. DailyProductionDetail + DailyProduction tháng 2026-04 (toàn bộ dữ liệu mẫu)
DELETE FROM daily_production_details
WHERE daily_production_id IN (
    SELECT id FROM daily_production
    WHERE production_date BETWEEN '2026-04-01' AND '2026-04-30'
);

DELETE FROM daily_production
WHERE production_date BETWEEN '2026-04-01' AND '2026-04-30';

-- 2. MasterDb mẫu
DELETE FROM master_db
WHERE ref IN ('SAMPLE-SEW-01', 'SAMPLE-ASSY-01', 'SEW1A', 'SEW1B');

-- 3. SixSRecord tháng 2026-04
DELETE FROM sixs_record WHERE data_month = '2026-04';

-- 4. ReprocessRecord tháng 2026-04
DELETE FROM reprocess_record WHERE data_month = '2026-04';

-- 5. NewStyleEntry mẫu
DELETE FROM new_style_entry
WHERE style IN ('NV-SEW-A', 'NV-SEW-B', 'NV-ASSY-C');

COMMIT;

-- Kiểm tra kết quả (tất cả nên = 0 với tháng 2026-04)
SELECT 'daily_production' AS tbl,    COUNT(*) FROM daily_production    WHERE production_date BETWEEN '2026-04-01' AND '2026-04-30'
UNION ALL
SELECT 'master_db (sample)',          COUNT(*) FROM master_db           WHERE ref IN ('SAMPLE-SEW-01','SAMPLE-ASSY-01','SEW1A','SEW1B')
UNION ALL
SELECT 'sixs_record 2026-04',         COUNT(*) FROM sixs_record         WHERE data_month = '2026-04'
UNION ALL
SELECT 'reprocess_record 2026-04',    COUNT(*) FROM reprocess_record    WHERE data_month = '2026-04'
UNION ALL
SELECT 'new_style_entry (sample)',     COUNT(*) FROM new_style_entry     WHERE style IN ('NV-SEW-A','NV-SEW-B','NV-ASSY-C');
