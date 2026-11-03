@echo off
REM Run Maven with proper environment setup

setlocal enabledelayedexpansion

set MAVEN_HOME=%USERPROFILE%\apache-maven\apache-maven-3.8.8
set MAVEN_BIN=%MAVEN_HOME%\bin
set PATH=%MAVEN_BIN%;%PATH%

REM Find Java installation
for /f "tokens=*" %%i in ('where java') do (
    set JAVA_BIN=%%i
)

REM Set JAVA_HOME (go up 3 levels from java.exe)
for /d %%i in ("%JAVA_BIN%\..\..\..") do set JAVA_HOME=%%~fi

echo.
echo ====================================
echo Maven Build Environment
echo ====================================
echo JAVA_HOME: %JAVA_HOME%
echo MAVEN_HOME: %MAVEN_HOME%
echo.

echo Java Version:
java -version
echo.

echo Maven Version:
mvn -version
echo.

echo ====================================
echo Running: mvn clean install
echo ====================================
echo.

mvn clean install %*

pause
