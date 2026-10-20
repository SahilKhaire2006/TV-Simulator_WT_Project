@echo off
setlocal
echo ============================================================
echo   Starting React Vite PWA Remote (Port 5173)
echo ============================================================
cd /d "%~dp0frontend-remote"
call npm run dev -- --host
