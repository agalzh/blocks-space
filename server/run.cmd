@echo off
REM Run Paper server. Usage: server\run.cmd
cd /d "%~dp0"
set "JAVA_HOME=%~dp0..\tools\jdk-17.0.19+10"
"%JAVA_HOME%\bin\java.exe" -Xms2G -Xmx4G -XX:+UseG1GC -jar paper.jar nogui
