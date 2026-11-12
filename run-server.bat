@echo off
setlocal
set "MVN=%~dp0tools\apache-maven-3.10.0\bin\mvn.cmd"
if exist "%MVN%" goto maven_ready
set "MVN=mvn"
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven 3.9 or later is required. Install Maven and add it to PATH.
  exit /b 1
)
:maven_ready
call "%MVN%" -q "-Dmaven.repo.local=%~dp0.m2\repository" compile -Dchat.mainClass=chat.server.ServerMain -Dexec.args="%*" org.codehaus.mojo:exec-maven-plugin:3.3.0:java
