@echo off
setlocal enableextensions
chcp 65001 > nul 2>&1
title IE-Eff Installer

echo.
echo  ==========================================
echo   IE-Eff -- Cai dat tu dong (Windows LAN)
echo  ==========================================
echo.

:: ---- Admin check ----
net session > nul 2>&1
if %errorlevel% neq 0 (
    echo [LOI] Hay chay lai voi quyen Administrator.
    echo       Chuot phai vao file nay, chon "Run as administrator"
    pause
    exit /b 1
)

:: ---- Load config ----
if not exist "%~dp0config.bat" (
    echo [LOI] Khong tim thay config.bat
    pause
    exit /b 1
)
call "%~dp0config.bat"

:: ---- Check app.jar ----
if not exist "%~dp0app.jar" (
    echo [LOI] Khong tim thay app.jar trong thu muc deploy\
    echo.
    echo       Hay build tren may dev truoc:
    echo         mvn package -DskipTests
    echo       Roi copy file:
    echo         target\management-0.0.1-SNAPSHOT.jar
    echo       Vao thu muc deploy\ va doi ten thanh app.jar
    echo.
    pause
    exit /b 1
)

:: ---- Check NSSM ----
set NSSM_EXE=%~dp0nssm\win64\nssm.exe
if not exist "%NSSM_EXE%" (
    echo [LOI] Khong tim thay NSSM tai: %NSSM_EXE%
    echo.
    echo       1. Tai NSSM tai: https://nssm.cc/download
    echo       2. Giai nen file zip
    echo       3. Copy thu muc win64\ vao: deploy\nssm\
    echo.
    pause
    exit /b 1
)

:: ---- [1/6] Check Java ----
echo [1/6] Kiem tra Java 17...
set JAVA_EXE=
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set JAVA_EXE=%JAVA_HOME%\bin\java.exe
)
if not defined JAVA_EXE (
    for /f "delims=" %%j in ('where java 2^>nul') do (
        if not defined JAVA_EXE set JAVA_EXE=%%j
    )
)
if not defined JAVA_EXE (
    echo [LOI] Java chua cai dat.
    echo       Tai Eclipse Temurin 17: https://adoptium.net/
    pause
    exit /b 1
)
"%JAVA_EXE%" -version 2>&1 | findstr /i "version \"17\." > nul
if %errorlevel% neq 0 (
    echo [CANH BAO] Phien ban Java co the khong phai 17.
    echo            Khuyen nghi: Eclipse Temurin 17 LTS
    echo            Nhan Enter de tiep tuc, Ctrl+C de huy.
    pause > nul
)
echo       OK  (%JAVA_EXE%)

:: ---- [2/6] Find PostgreSQL ----
echo [2/6] Tim PostgreSQL...
set PSQL_EXE=
set PGDUMP_EXE=
for /d %%d in ("C:\Program Files\PostgreSQL\*") do (
    if exist "%%d\bin\psql.exe" (
        set PSQL_EXE=%%d\bin\psql.exe
        set PGDUMP_EXE=%%d\bin\pg_dump.exe
    )
)
if not defined PSQL_EXE (
    echo [LOI] PostgreSQL chua cai dat.
    echo       Tai: https://www.postgresql.org/download/windows/
    pause
    exit /b 1
)
echo       OK  (%PSQL_EXE%)

:: ---- [3/6] Setup database ----
echo [3/6] Tao database, user va bang du lieu...
set PGPASSWORD=%POSTGRES_PASSWORD%

"%PSQL_EXE%" -U postgres -h localhost -c "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='%DB_USER%') THEN CREATE USER %DB_USER% WITH PASSWORD '%DB_PASSWORD%'; ELSE ALTER USER %DB_USER% WITH PASSWORD '%DB_PASSWORD%'; END IF; END; $$;" > nul 2>&1
if %errorlevel% neq 0 (
    echo [LOI] Khong the ket noi PostgreSQL.
    echo       Kiem tra POSTGRES_PASSWORD trong config.bat co dung khong.
    echo       Hoac thu: psql -U postgres -h localhost
    pause
    exit /b 1
)

"%PSQL_EXE%" -U postgres -h localhost -c "CREATE DATABASE %DB_NAME% OWNER %DB_USER%;" > nul 2>&1
"%PSQL_EXE%" -U postgres -h localhost -c "GRANT ALL PRIVILEGES ON DATABASE %DB_NAME% TO %DB_USER%;" > nul 2>&1
"%PSQL_EXE%" -U postgres -h localhost -d %DB_NAME% -c "GRANT ALL ON SCHEMA public TO %DB_USER%;" > nul 2>&1
echo       OK  (database: %DB_NAME%, user: %DB_USER%)

if not exist "%~dp0init_db.sql" (
    echo [LOI] Khong tim thay init_db.sql trong thu muc deploy\
    pause
    exit /b 1
)
echo       Tao 14 bang du lieu...
set PGPASSWORD=%DB_PASSWORD%
"%PSQL_EXE%" -U %DB_USER% -h localhost -d %DB_NAME% -v ON_ERROR_STOP=1 -f "%~dp0init_db.sql" > nul
if %errorlevel% neq 0 (
    echo [LOI] Khong the tao bang du lieu — xem loi PostgreSQL o tren.
    set PGPASSWORD=
    pause
    exit /b 1
)
set PGPASSWORD=%POSTGRES_PASSWORD%
echo       OK  (schema da san sang)

:: ---- [4/6] Create install directory ----
echo [4/6] Tao thu muc cai dat...
if not exist "%INSTALL_DIR%"        mkdir "%INSTALL_DIR%"
if not exist "%INSTALL_DIR%\logs"   mkdir "%INSTALL_DIR%\logs"
if not exist "%INSTALL_DIR%\backup" mkdir "%INSTALL_DIR%\backup"
copy /y "%~dp0app.jar" "%INSTALL_DIR%\app.jar" > nul
echo       OK  (%INSTALL_DIR%)

:: ---- [5/6] Install Windows Service ----
echo [5/6] Cai dat Windows Service...
"%NSSM_EXE%" status IE-Eff > nul 2>&1
if %errorlevel% equ 0 (
    echo       Xoa service cu...
    "%NSSM_EXE%" stop IE-Eff > nul 2>&1
    timeout /t 3 /nobreak > nul
    "%NSSM_EXE%" remove IE-Eff confirm > nul 2>&1
)

"%NSSM_EXE%" install IE-Eff "%JAVA_EXE%"
"%NSSM_EXE%" set IE-Eff AppParameters "-Xmx%APP_XMX% -Xms%APP_XMS% -jar %INSTALL_DIR%\app.jar --spring.profiles.active=prod"
"%NSSM_EXE%" set IE-Eff AppDirectory "%INSTALL_DIR%"
"%NSSM_EXE%" set IE-Eff DisplayName "IE-Eff Production"
"%NSSM_EXE%" set IE-Eff Description "IE Efficiency Management"
"%NSSM_EXE%" set IE-Eff Start SERVICE_AUTO_START
"%NSSM_EXE%" set IE-Eff AppStdout "%INSTALL_DIR%\logs\app.log"
"%NSSM_EXE%" set IE-Eff AppStderr "%INSTALL_DIR%\logs\app.log"
"%NSSM_EXE%" set IE-Eff AppRotateFiles 1
"%NSSM_EXE%" set IE-Eff AppRotateBytes 10485760
"%NSSM_EXE%" set IE-Eff AppEnvironmentExtra "DB_URL=jdbc:postgresql://localhost:5432/%DB_NAME%" "DB_USERNAME=%DB_USER%" "DB_PASSWORD=%DB_PASSWORD%" "PORT=%APP_PORT%" "APP_DEFAULT_ADMIN_PASSWORD=%APP_ADMIN_PASSWORD%" "APP_DEFAULT_MANAGER_PASSWORD=%APP_MANAGER_PASSWORD%"

netsh advfirewall firewall delete rule name="IE-Eff App" > nul 2>&1
netsh advfirewall firewall add rule name="IE-Eff App" dir=in action=allow protocol=TCP localport=%APP_PORT% > nul
echo       OK  (service: IE-Eff, port: %APP_PORT%)

:: ---- [6/6] Daily backup task ----
echo [6/6] Cai dat backup tu dong (3:00 AM)...
(
echo @echo off
echo set PGPASSWORD=%DB_PASSWORD%
echo set D=%%DATE:~10,4%%%%DATE:~4,2%%%%DATE:~7,2%%
echo "%PGDUMP_EXE%" -U %DB_USER% -h localhost -d %DB_NAME% -F c -f "%INSTALL_DIR%\backup\db_%%D%%.dump"
echo forfiles /p "%INSTALL_DIR%\backup" /s /m *.dump /d -30 /c "cmd /c del @path" 2^>nul
) > "%INSTALL_DIR%\backup.bat"

schtasks /delete /tn "IE-Eff Backup" /f > nul 2>&1
schtasks /create /tn "IE-Eff Backup" /tr "\"%INSTALL_DIR%\backup.bat\"" /sc daily /st 03:00 /ru SYSTEM /f > nul
echo       OK

:: ---- Start service ----
echo.
echo Khoi dong service IE-Eff...
"%NSSM_EXE%" start IE-Eff
echo Doi app khoi dong (45 giay)...
timeout /t 45 /nobreak > nul

:: ---- Verify ----
curl -s -o nul -w "%%{http_code}" http://localhost:%APP_PORT% > "%TEMP%\ie_http.txt" 2>nul
set /p HTTP_CODE= < "%TEMP%\ie_http.txt"
del "%TEMP%\ie_http.txt" > nul 2>&1

if "%HTTP_CODE%"=="200" (
    echo.
    echo  ==========================================
    echo   CAI DAT THANH CONG!
    echo.
    echo   Truy cap noi bo:  http://localhost:%APP_PORT%
    echo   Tu may khac LAN:  http://<IP-server>:%APP_PORT%
    echo.
    echo   Tai khoan mac dinh: admin / admin
    echo   ^!^!^! DOI MAT KHAU NGAY SAU KHI DANG NHAP ^!^!^!
    echo  ==========================================
) else (
    echo.
    echo  [CANH BAO] App chua phan hoi (HTTP: %HTTP_CODE%)
    echo  Kiem tra log: %INSTALL_DIR%\logs\app.log
    echo  Trang thai:   "%NSSM_EXE%" status IE-Eff
    echo  Flyway co the dang chay — doi them 30 giay roi thu lai.
)

echo.
pause
