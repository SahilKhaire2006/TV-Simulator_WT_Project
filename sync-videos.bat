@echo off
setlocal
echo ============================================================
echo   Syncing Local Videos to Webapp & Tomcat
echo ============================================================

set "SRC=%~dp0videos"
set "DST1=%~dp0backend\src\main\webapp\videos"
set "DST2=%~dp0.tools\apache-tomcat-10.1.20\webapps\tv-simulator\videos"

if not exist "%DST1%" mkdir "%DST1%"
if not exist "%DST2%" mkdir "%DST2%"

echo Copying video files from %SRC%...
copy /Y "%SRC%\*.*" "%DST1%\" >nul 2>&1
copy /Y "%SRC%\*.*" "%DST2%\" >nul 2>&1

echo [SUCCESS] Videos synchronized!
echo Location:
echo   - %DST1%
echo   - %DST2%
echo.
echo List of active video files:
dir /B "%DST2%"
echo ============================================================
pause
