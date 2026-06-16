-- ============================================================
-- V18__fix_assembly_line5_section.sql
-- Sửa dữ liệu lịch sử: các dòng ASSEMBLY line 5 từng được import/lưu bằng code cũ
-- (trước khi vá quy tắc line-5) bị gán section = 'ASSEMBLY BIG'. Theo quy ước nhà
-- máy, ASSEMBLY line 5 LUÔN là 'ASSEMBLY SMALL'. EFF được TÍNH (không lưu sẵn) nên
-- chỉ cần sửa lại section là EFF/lương tự đúng lại (dùng MP/Quota của SMALL).
-- Idempotent: chạy lại sẽ khớp 0 dòng. split_entry không bị (import split luôn đúng).
-- ============================================================

UPDATE daily_production
SET section = 'ASSEMBLY SMALL'
WHERE section = 'ASSEMBLY BIG' AND line = '5';
