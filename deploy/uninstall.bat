@echo off
chcp 65001 > nul 2>&1
title IE-Eff Uninstaller

echo.
echo  ==========================================
echo   IE-Eff -- Go cai dat
echo  ==========================================
echo.

:: ---- Admin check ----
net session > nul 2>&1
if %errorlevel% neq 0 (
    echo [LOI] Hay chay lai voi quyen Administrator.
    pause
    exit /b 1
)

call "%~dp0config.bat"
set NSSM_EXE=%~dp0nssm\win64\nssm.exe

:: ---- Stop and remove service ----
echo [1/4] Dung va xoa service IE-Eff...
if exist "%NSSM_EXE%" (
    "%NSSM_EXE%" stop IE-Eff > nul 2>&1
    timeout /t 5 /nobreak > nul
    "%NSSM_EXE%" remove IE-Eff confirm > nul 2>&1
    echo       OK
) else (
    sc stop IE-Eff > nul 2>&1
    sc delete IE-Eff > nul 2>&1
    echo       OK (dung qua sc.exe)
)

:: ---- Remove firewall rule ----
echo [2/4] Xoa firewall rule...
netsh advfirewall firewall delete rule name="IE-Eff App" > nul 2>&1
echo       OK

:: ---- Remove backup task ----
echo [3/4] Xoa backup task...
schtasks /delete /tn "IE-Eff Backup" /f > nul 2>&1
echo       OK

:: ---- Remove install directory ----
echo [4/4] Xoa thu muc cai dat?
echo       Thu muc: %INSTALL_DIR% (bao gom logs va backup)
echo.
set /p CONFIRM=Xac nhan xoa (y/n):
if /i "%CONFIRM%"=="y" (
    if exist "%INSTALL_DIR%" (
        rmdir /s /q "%INSTALL_DIR%"
        echo       OK  (%INSTALL_DIR% da xoa)
    ) else (
        echo       Thu muc khong ton tai.
    )
) else (
    echo       Bo qua — giu lai %INSTALL_DIR%
)

echo.
echo  Go cai dat hoan tat.
echo  Database shoe_eff_db van con trong PostgreSQL.
echo  De xoa database: psql -U postgres -c "DROP DATABASE shoe_eff_db;"
echo.
pause
