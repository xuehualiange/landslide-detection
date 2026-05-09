param(
    [string]$DbUser = "root",
    [string]$DbPassword = "root",
    [string]$DbName = "landslide_db"
)

$ErrorActionPreference = "SilentlyContinue"

if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    Write-Host "FAIL: mysql client not found in PATH" -ForegroundColor Red
    exit 1
}

$mysqlArgsBase = @("--user=$DbUser", "--password=$DbPassword")

Write-Host "Checking database connection..." -ForegroundColor Cyan
& mysql @mysqlArgsBase -e "SELECT 1;" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL: cannot connect to MySQL with provided credentials" -ForegroundColor Red
    exit 1
}

Write-Host "OK: MySQL connection success" -ForegroundColor Green

& mysql @mysqlArgsBase -e "USE $DbName; SHOW TABLES LIKE 'sys_user';" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL: database '$DbName' not found" -ForegroundColor Red
    exit 1
}

Write-Host "OK: database '$DbName' exists" -ForegroundColor Green

$userCount = & mysql @mysqlArgsBase -N -e "USE $DbName; SELECT COUNT(*) FROM sys_user WHERE username IN ('superadmin','admin','monitor');"
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL: sys_user table missing or query failed" -ForegroundColor Red
    exit 1
}

Write-Host "Seed user count (superadmin/admin/monitor): $userCount" -ForegroundColor Cyan
if ([int]$userCount -lt 3) {
    Write-Host "WARN: seed users incomplete, please run docs/data.sql" -ForegroundColor Yellow
} else {
    Write-Host "OK: seed users are ready" -ForegroundColor Green
}
