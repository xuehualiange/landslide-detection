$ErrorActionPreference = "SilentlyContinue"

Write-Host "=== Landslide Backend Env Check ===" -ForegroundColor Cyan

Write-Host "`n[Java]"
$javaVersion = & java -version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL: java not found in PATH" -ForegroundColor Red
} else {
    $line = $javaVersion | Select-Object -First 1
    Write-Host "OK: $line" -ForegroundColor Green
    $major = 0
    if ($line -match '"(\d+)\.') {
        $major = [int]$Matches[1]
    } elseif ($line -match '"1\.(\d+)\.') {
        $major = [int]$Matches[1]
    }
    if ($major -lt 17) {
        Write-Host "WARN: Java < 17. Spring Boot requires Java 17+" -ForegroundColor Yellow
    }
}

Write-Host "`n[Maven]"
$mvnVersion = & mvn -v 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL: mvn not found in PATH" -ForegroundColor Red
} else {
    Write-Host "OK: $($mvnVersion | Select-Object -First 1)" -ForegroundColor Green
}

Write-Host "`n[MySQL Port 3306]"
if (netstat -ano | Select-String ":3306") {
    Write-Host "OK: 3306 is listening" -ForegroundColor Green
} else {
    Write-Host "FAIL: 3306 not listening" -ForegroundColor Red
}

Write-Host "`n[Redis Port 6379]"
if (netstat -ano | Select-String ":6379") {
    Write-Host "OK: 6379 is listening" -ForegroundColor Green
} else {
    Write-Host "WARN: 6379 not listening" -ForegroundColor Yellow
}

Write-Host "`n[Backend Port 8080]"
if (netstat -ano | Select-String ":8080") {
    Write-Host "OK: 8080 is listening" -ForegroundColor Green
} else {
    Write-Host "WARN: 8080 not listening" -ForegroundColor Yellow
}

Write-Host "`n=== Check Finished ===" -ForegroundColor Cyan
