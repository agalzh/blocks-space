@echo off
REM Sourcing helper. Run as:  call setenv.cmd
REM Sets JAVA_HOME -> Temurin 17, Maven on PATH. For this shell only.
set "PROJECT_ROOT=%~dp0"
set "JAVA_HOME=%PROJECT_ROOT%tools\jdk-17.0.19+10"
set "MAVEN_HOME=%PROJECT_ROOT%tools\apache-maven-3.9.16"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
echo MAVEN_HOME=%MAVEN_HOME%
