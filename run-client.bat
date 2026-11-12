@echo off
setlocal
call :use_java_25
if errorlevel 1 exit /b 1
set "MVN=%~dp0tools\apache-maven-3.10.0\bin\mvn.cmd"
if exist "%MVN%" goto maven_ready
set "MVN=mvn"
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven 3.9 or later is required. Install Maven and add it to PATH.
  exit /b 1
)
:maven_ready
call "%MVN%" -q "-Dmaven.repo.local=%~dp0.m2\repository" compile -Dchat.mainClass=chat.client.ClientMain -Dexec.args="%*" org.codehaus.mojo:exec-maven-plugin:3.3.0:java
exit /b %errorlevel%

:use_java_25
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
  "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /r /c:"version ""25\." >nul
  if not errorlevel 1 (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    exit /b 0
  )
)
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do if exist "%%~fD\bin\java.exe" (
  set "JAVA_HOME=%%~fD"
  set "PATH=%%~fD\bin;%PATH%"
  exit /b 0
)
java -version 2>&1 | findstr /r /c:"version ""25\." >nul
if not errorlevel 1 exit /b 0
echo Java 25 is required. Install Java 25 or set JAVA_HOME to it.
exit /b 1
