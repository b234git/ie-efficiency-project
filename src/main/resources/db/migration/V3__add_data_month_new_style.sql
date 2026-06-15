-- new_style_entry thời điểm V3 do Hibernate (ddl-auto=update) tạo, không có CREATE
-- migration. Trên DB rỗng bảng chưa tồn tại ở bước này → bỏ qua; V17 catch-up sẽ
-- tạo bảng theo shape cuối (đã gồm data_month). DB cũ có bảng thì ALTER như cũ.
-- (Sửa lại 2026-06-12 khi kích hoạt Flyway thật — chưa môi trường nào ghi checksum V3.)
DO $$
BEGIN
    IF to_regclass('new_style_entry') IS NOT NULL THEN
        ALTER TABLE new_style_entry ADD COLUMN IF NOT EXISTS data_month VARCHAR(7);
    END IF;
END $$;
