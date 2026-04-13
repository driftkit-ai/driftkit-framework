# DriftKit CLI Installation Script for Windows

Write-Host "Installing DriftKit CLI..." -ForegroundColor Green
Write-Host "=========================" -ForegroundColor Green

# Check if running as administrator
$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$isAdmin = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "This script needs to be run as Administrator" -ForegroundColor Red
    Write-Host "Please right-click and select 'Run as Administrator'" -ForegroundColor Yellow
    exit 1
}

# Check for Maven
try {
    mvn --version | Out-Null
} catch {
    Write-Host "Maven not found! Please install Maven first." -ForegroundColor Red
    exit 1
}

# Build if JAR doesn't exist
$jarPattern = "target\driftkit-cli-*-jar-with-dependencies.jar"
$jarFiles = Get-ChildItem -Path $jarPattern -ErrorAction SilentlyContinue

if ($jarFiles.Count -eq 0) {
    Write-Host "Building DriftKit CLI..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed!" -ForegroundColor Red
        exit 1
    }
    $jarFiles = Get-ChildItem -Path $jarPattern
}

$jarFile = $jarFiles[0].FullName

# Create installation directory
$installDir = "$env:ProgramFiles\DriftKit"
if (-not (Test-Path $installDir)) {
    New-Item -ItemType Directory -Path $installDir -Force | Out-Null
}

# Copy JAR
Write-Host "Installing JAR to $installDir..." -ForegroundColor Yellow
Copy-Item -Path $jarFile -Destination "$installDir\driftkit-cli.jar" -Force

# Create batch file
$batchPath = "$installDir\driftkit.cmd"
$batchContent = @"
@echo off
setlocal

rem Find Java
if defined JAVA_HOME (
    set JAVA="%JAVA_HOME%\bin\java"
) else (
    set JAVA=java
)

rem Check Java version
for /f tokens^=2-5^ delims^=.-_^" %%j in ('%JAVA% -version 2^>^&1') do set JAVA_VERSION=%%j
if %JAVA_VERSION% LSS 21 (
    echo Error: DriftKit CLI requires Java 21 or higher
    %JAVA% -version
    exit /b 1
)

rem Run DriftKit CLI
%JAVA% -jar "%ProgramFiles%\DriftKit\driftkit-cli.jar" %*
"@

Set-Content -Path $batchPath -Value $batchContent

# Add to PATH
$currentPath = [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::Machine)
if ($currentPath -notlike "*$installDir*") {
    Write-Host "Adding DriftKit to system PATH..." -ForegroundColor Yellow
    [Environment]::SetEnvironmentVariable(
        "Path",
        "$currentPath;$installDir",
        [EnvironmentVariableTarget]::Machine
    )
}

# Create PowerShell wrapper
$psWrapperPath = "$installDir\driftkit.ps1"
$psWrapperContent = @"
# DriftKit CLI PowerShell wrapper
& "$installDir\driftkit.cmd" `$args
"@
Set-Content -Path $psWrapperPath -Value $psWrapperContent

# Create uninstall script
$uninstallPath = "$installDir\uninstall.ps1"
$uninstallContent = @'
# DriftKit CLI Uninstall Script

Write-Host "Uninstalling DriftKit CLI..." -ForegroundColor Yellow

# Remove from PATH
$currentPath = [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::Machine)
$newPath = ($currentPath -split ';' | Where-Object { $_ -ne "$env:ProgramFiles\DriftKit" }) -join ';'
[Environment]::SetEnvironmentVariable("Path", $newPath, [EnvironmentVariableTarget]::Machine)

# Remove files
Remove-Item -Path "$env:ProgramFiles\DriftKit" -Recurse -Force

Write-Host "DriftKit CLI has been uninstalled." -ForegroundColor Green
'@
Set-Content -Path $uninstallPath -Value $uninstallContent

Write-Host "`n✅ DriftKit CLI has been installed successfully!" -ForegroundColor Green
Write-Host "`nYou can now use 'driftkit' command from any new Command Prompt or PowerShell window:" -ForegroundColor Cyan
Write-Host "  driftkit new my-app" -ForegroundColor White
Write-Host "  driftkit help" -ForegroundColor White
Write-Host "`nNote: You need to open a new terminal for the PATH changes to take effect!" -ForegroundColor Yellow
Write-Host "`nTo uninstall, run as Administrator: $installDir\uninstall.ps1" -ForegroundColor Gray

# Test in new process
Write-Host "`nTesting installation..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "driftkit --version" -NoNewWindow -Wait