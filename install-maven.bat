@echo off
REM Maven Installation Script for Windows
REM This script downloads and installs Maven 3.9.6 (latest stable)

setlocal enabledelayedexpansion

echo.
echo ====================================
echo Maven Installation Script for Windows
echo ====================================
echo.

REM Set Maven version and directories
set MAVEN_VERSION=3.9.6
set MAVEN_HOME=%USERPROFILE%\apache-maven
set MAVEN_ZIP=%TEMP%\apache-maven-%MAVEN_VERSION%-bin.zip
set MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip

echo Java Version Check:
java -version 2>&1
echo.

REM Step 1: Create Maven directory
echo Step 1: Creating Maven directory at %MAVEN_HOME%
if not exist "%MAVEN_HOME%" (
    mkdir "%MAVEN_HOME%"
    echo Created: %MAVEN_HOME%
) else (
    echo Directory already exists
)
echo.

REM Step 2: Download Maven
echo Step 2: Downloading Maven %MAVEN_VERSION%...
echo Source: %MAVEN_URL%
if not exist "%MAVEN_ZIP%" (
    powershell -Command "& {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%MAVEN_ZIP%'
        Write-Host 'Downloaded: %MAVEN_ZIP%'
    }"
) else (
    echo File already exists: %MAVEN_ZIP%
)
echo.

REM Step 3: Extract Maven
echo Step 3: Extracting Maven...
if exist "%MAVEN_ZIP%" (
    powershell -Command "& {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory('%MAVEN_ZIP%', '%MAVEN_HOME%')
        Write-Host 'Extracted successfully'
    }"
) else (
    echo Error: ZIP file not found
    exit /b 1
)
echo.

REM Step 4: Add to PATH
echo Step 4: Adding Maven to PATH...
setx PATH "%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%\bin;!PATH!" /M
echo Added to system PATH (requires restart for permanent effect)
echo.

REM Step 5: Set JAVA_HOME
echo Step 5: Setting JAVA_HOME...
for /f "tokens=*" %%i in ('where java') do (
    set JAVA_BIN=%%i
)
REM Get the Java installation directory
for /d %%i in ("%JAVA_BIN%\..\..\..") do set JAVA_HOME=%%~fi
setx JAVA_HOME "%JAVA_HOME%" /M
echo JAVA_HOME set to: %JAVA_HOME%
echo.

REM Step 6: Verify Installation
echo Step 6: Verifying Maven installation...
set "PATH=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%\bin;!PATH!"
mvn -v
echo.

if errorlevel 1 (
    echo.
    echo WARNING: Maven command failed. Please restart PowerShell or Command Prompt.
    echo.
) else (
    echo ====================================
    echo ✅ Maven installed successfully!
    echo ====================================
    echo.
    echo JAVA_HOME: %JAVA_HOME%
    echo MAVEN_HOME: %MAVEN_HOME%\apache-maven-%MAVEN_VERSION%
    echo.
)

echo To use Maven in this session, run:
echo   set PATH=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%\bin;!PATH!
echo.
echo Or close and reopen PowerShell/Command Prompt to use Maven globally.
echo.

endlocal
pause
