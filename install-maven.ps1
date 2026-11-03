# Maven Installation Script for Windows PowerShell
# Downloads and installs Maven 3.9.6 (latest stable)

param(
    [string]$MavenVersion = "3.9.6",
    [string]$InstallPath = "$env:USERPROFILE\apache-maven"
)

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "Maven Installation Script for Windows" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# Check Java
Write-Host "Step 1: Checking Java Installation..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1
    Write-Host "✅ Java Found:" -ForegroundColor Green
    Write-Host $javaVersion[2] -ForegroundColor Green
} catch {
    Write-Host "❌ Java not found. Please install Java 17 or later." -ForegroundColor Red
    exit 1
}
Write-Host ""

# Set paths
$MavenZip = "$env:TEMP\apache-maven-$MavenVersion-bin.zip"
$MavenUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/apache-maven-$MavenVersion-bin.zip"
$MavenExtractPath = "$InstallPath\apache-maven-$MavenVersion"

# Step 2: Create directory
Write-Host "Step 2: Creating Maven directory..." -ForegroundColor Yellow
if (-not (Test-Path $InstallPath)) {
    New-Item -ItemType Directory -Path $InstallPath | Out-Null
    Write-Host "✅ Created: $InstallPath" -ForegroundColor Green
} else {
    Write-Host "✅ Directory already exists: $InstallPath" -ForegroundColor Green
}
Write-Host ""

# Step 3: Download Maven
Write-Host "Step 3: Downloading Maven $MavenVersion..." -ForegroundColor Yellow
Write-Host "Source: $MavenUrl" -ForegroundColor Gray
if (-not (Test-Path $MavenZip)) {
    try {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $MavenUrl -OutFile $MavenZip -UseBasicParsing
        Write-Host "✅ Downloaded: $MavenZip" -ForegroundColor Green
        Write-Host "   Size: $([Math]::Round((Get-Item $MavenZip).Length / 1MB, 2)) MB" -ForegroundColor Gray
    } catch {
        Write-Host "❌ Failed to download Maven" -ForegroundColor Red
        Write-Host "   Error: $_" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✅ ZIP file already exists" -ForegroundColor Green
}
Write-Host ""

# Step 4: Extract Maven
Write-Host "Step 4: Extracting Maven..." -ForegroundColor Yellow
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($MavenZip, $InstallPath, $true)
    Write-Host "✅ Extracted to: $MavenExtractPath" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to extract Maven" -ForegroundColor Red
    Write-Host "   Error: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 5: Find Java Home
Write-Host "Step 5: Detecting JAVA_HOME..." -ForegroundColor Yellow
$javaExe = (Get-Command java).Source
$javaHome = Split-Path -Parent (Split-Path -Parent $javaExe)
Write-Host "✅ JAVA_HOME: $javaHome" -ForegroundColor Green
Write-Host ""

# Step 6: Set Environment Variables (for current session)
Write-Host "Step 6: Configuring Environment Variables..." -ForegroundColor Yellow
$env:MAVEN_HOME = $InstallPath
$env:JAVA_HOME = $javaHome
$env:PATH = "$MavenExtractPath\bin;" + $env:PATH

Write-Host "✅ Current session PATH updated" -ForegroundColor Green
Write-Host ""

# Step 7: Verify Installation
Write-Host "Step 7: Verifying Maven Installation..." -ForegroundColor Yellow
& "$MavenExtractPath\bin\mvn" -v
Write-Host ""

if ($LASTEXITCODE -eq 0) {
    Write-Host "=====================================" -ForegroundColor Green
    Write-Host "✅ Maven installed successfully!" -ForegroundColor Green
    Write-Host "=====================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Quick Info:" -ForegroundColor Cyan
    Write-Host "  JAVA_HOME  : $javaHome" -ForegroundColor Gray
    Write-Host "  MAVEN_HOME : $InstallPath" -ForegroundColor Gray
    Write-Host "  Maven Bin  : $MavenExtractPath\bin" -ForegroundColor Gray
    Write-Host ""
} else {
    Write-Host "⚠️  Maven verification returned an error" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "1. Run Maven commands in this PowerShell window (session-scoped)" -ForegroundColor Gray
Write-Host "2. For permanent PATH, manually add to System Environment Variables:" -ForegroundColor Gray
Write-Host "   - Variable: PATH" -ForegroundColor Gray
Write-Host "   - Add: $MavenExtractPath\bin" -ForegroundColor Gray
Write-Host "3. Close and reopen PowerShell for permanent changes" -ForegroundColor Gray
Write-Host ""
Write-Host "Try building now:" -ForegroundColor Cyan
Write-Host "  cd D:\half-2026\DataTalk-AI---POC" -ForegroundColor Gray
Write-Host "  mvn clean install" -ForegroundColor Gray
Write-Host ""
