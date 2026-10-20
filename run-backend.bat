@echo off
setlocal
echo ============================================================
echo   Starting Tomcat 10 Backend (Port 8085)
echo ============================================================
set "CATALINA_HOME=%~dp0.tools\apache-tomcat-10.1.20"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"

if not exist "%CATALINA_HOME%\bin\catalina.bat" (
    echo [ERROR] Tomcat 10 not found at %CATALINA_HOME%
    pause
    exit /b 1
)

REM Check if Tomcat is already running on port 8085
netstat -ano | findstr /R ":8085 .*LISTENING" >nul
if %ERRORLEVEL% equ 0 (
    echo [INFO] Backend is already running on port 8085.
    exit /b 0
)

echo Deploying WAR...
if not exist "%CATALINA_HOME%\webapps\tv-simulator.war" (
    copy /Y "%~dp0backend\target\tv-broadcast-simulator.war" "%CATALINA_HOME%\webapps\tv-simulator.war" >nul
)

if exist "%~dp0videos" (
    if not exist "%CATALINA_HOME%\webapps\tv-simulator\videos" mkdir "%CATALINA_HOME%\webapps\tv-simulator\videos"
    copy /Y "%~dp0videos\*.*" "%CATALINA_HOME%\webapps\tv-simulator\videos\" >nul 2>&1
)

echo Starting Tomcat...
"%CATALINA_HOME%\bin\catalina.bat" run
