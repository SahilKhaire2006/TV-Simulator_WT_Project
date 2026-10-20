@echo off
setlocal
echo ============================================================
echo   Starting Virtual Television Broadcast Simulator
echo   Launching Backend (8085) and Frontend Remote (5173)...
echo ============================================================

REM 1. Start Tomcat 10 Backend in a new window
echo [1/3] Checking Backend Server on port 8085...
start "TV Simulator - Tomcat Backend (Port 8085)" cmd /k call "%~dp0run-backend.bat"

REM 2. Start React Vite Remote in a new window
timeout /t 2 /nobreak >nul
echo [2/3] Checking React PWA Remote on port 5173...
start "TV Simulator - Vite Remote (Port 5173)" cmd /k call "%~dp0run-frontend.bat"

REM 3. Wait for backend to be ready
echo [3/3] Waiting for servers to initialize...
timeout /t 5 /nobreak >nul

echo Opening TV Client and Remote in browser...
start http://localhost:8085/tv-simulator/client-player.jsp
start http://localhost:5173/

echo ============================================================
echo   Both servers are now running!
echo   - TV Client:   http://localhost:8085/tv-simulator/client-player.jsp
echo   - Remote App:  http://localhost:5173/
echo ============================================================
