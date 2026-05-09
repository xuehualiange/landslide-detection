param(
    [string]$JavaHome = "C:\Program Files\JetBrains\PyCharm 2024.2.4\jbr"
)

$ErrorActionPreference = "Stop"

$backendRoot = Split-Path -Parent $PSScriptRoot
Set-Location $backendRoot

if (!(Test-Path "$JavaHome\bin\java.exe")) {
    Write-Host "FAIL: java.exe not found under $JavaHome" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;" + $env:Path

Write-Host "Using JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
& java -version

$mvnCmd = $null
if ($env:MAVEN_HOME -and (Test-Path "$env:MAVEN_HOME\bin\mvn.cmd")) {
    $mvnCmd = "$env:MAVEN_HOME\bin\mvn.cmd"
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnCmd = "mvn"
}

if (-not $mvnCmd) {
    Write-Host "FAIL: Maven not found. Set MAVEN_HOME or add mvn to PATH." -ForegroundColor Red
    exit 1
}

Write-Host "Using Maven: $mvnCmd" -ForegroundColor Cyan
Write-Host "Building backend jar (no clean; avoids locked jar)..." -ForegroundColor Cyan
& $mvnCmd -e -DskipTests package

$jar = Get-ChildItem -Path ".\target" -Filter "*.jar" |
    Where-Object { $_.Name -notlike "original-*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    Write-Host "FAIL: built jar not found under target" -ForegroundColor Red
    exit 1
}

Write-Host "Starting backend from jar: $($jar.FullName)" -ForegroundColor Cyan
& java -jar $jar.FullName
