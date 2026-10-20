@echo off
setlocal
echo ============================================================
echo   Building Backend WAR with Maven
echo ============================================================
set "MAVEN_CMD=%~dp0.tools\apache-maven-3.9.6\bin\mvn.cmd"
set "JAVA_HOME=C:\Program Files\Java\jdk-21"

cd /d "%~dp0backend"
call "%MAVEN_CMD%" clean package -DskipTests

if %ERRORLEVEL% equ 0 (
    echo [SUCCESS] Backend WAR created at backend\target\tv-broadcast-simulator.war
    copy /Y "%~dp0backend\target\tv-broadcast-simulator.war" "%~dp0.tools\apache-tomcat-10.1.20\webapps\tv-simulator.war" >nul
    echo [SUCCESS] Deployed to Tomcat webapps\tv-simulator.war
) else (
    echo [ERROR] Build failed!
)
pause
