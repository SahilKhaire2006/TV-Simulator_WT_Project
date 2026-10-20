@echo off
setlocal
echo ============================================================
echo   Opening 4 Virtual TVs + Remote Control for Multi-Testing
echo ============================================================

echo Opening TV #1: Living Room...
start "" "http://localhost:8085/tv-simulator/client-player.jsp?name=Living+Room+TV"

timeout /t 1 /nobreak >nul
echo Opening TV #2: Bedroom...
start "" "http://localhost:8085/tv-simulator/client-player.jsp?name=Bedroom+TV"

timeout /t 1 /nobreak >nul
echo Opening TV #3: Kitchen...
start "" "http://localhost:8085/tv-simulator/client-player.jsp?name=Kitchen+TV"

timeout /t 1 /nobreak >nul
echo Opening TV #4: Sports Bar...
start "" "http://localhost:8085/tv-simulator/client-player.jsp?name=Sports+Bar+TV"

timeout /t 1 /nobreak >nul
echo Opening Remote Control...
start "" "http://localhost:5173/"

echo ============================================================
echo   All 4 TVs + Remote launched!
echo   In the Remote Control, use the "TARGET" dropdown to switch
echo   between specific TVs or select "BROADCAST ALL".
echo ============================================================
