:: ============================================================
:: CÂU HINH CAI DAT — Sua cac gia tri duoi day truoc khi chay
:: Luu y: Khong dung ky tu ! trong mat khau (bat file gioi han)
:: ============================================================

:: Mat khau user "postgres" (superuser) — nhap khi cai PostgreSQL
set POSTGRES_PASSWORD=postgres

:: Thong tin database ung dung
set DB_NAME=shoe_eff_db
set DB_USER=ie_app
set DB_PASSWORD=IeApp@Secure2024!

:: Thu muc cai dat tren server
set INSTALL_DIR=C:\ie-eff

:: Port ung dung (mac dinh 8080)
set APP_PORT=8080

:: ---- RAM cho ung dung (Java heap) ----
:: APP_XMX = RAM toi da cap cho app. Chon theo RAM cua server:
::   server RAM 8GB  -> 2g      16GB -> 4g      32GB -> 8g
set APP_XMX=2g
:: APP_XMS = RAM cap phat ban dau (de mac dinh 512m)
set APP_XMS=512m

:: ---- Mat khau tai khoan khoi tao ----
:: Dung khi tao tai khoan lan dau. DOI NGAY sau khi dang nhap.
set APP_ADMIN_PASSWORD=Admin@123
set APP_MANAGER_PASSWORD=Manager@123
