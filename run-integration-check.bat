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
echo Compiling integration check...
call "%MVN%" -q "-Dmaven.repo.local=%~dp0.m2\repository" test-compile
if errorlevel 1 exit /b 1
echo Running local socket and database scenarios...
java -cp "%~dp0target\test-classes;%~dp0target\classes;%~dp0.m2\repository\org\xerial\sqlite-jdbc\3.46.1.0\sqlite-jdbc-3.46.1.0.jar;%~dp0.m2\repository\org\slf4j\slf4j-api\1.7.36\slf4j-api-1.7.36.jar;%~dp0.m2\repository\org\slf4j\slf4j-nop\1.7.36\slf4j-nop-1.7.36.jar" chat.server.ChatServerIntegrationCheck
exit /b %errorlevel%
