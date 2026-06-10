# Offline chat API (keyword FAQ). Port 8000 — do not run with langchain-chat-api at the same time.
param(
    [string]$Port = "8000"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($root)) {
    $root = Split-Path -Parent $MyInvocation.MyCommand.Path
}
Set-Location -LiteralPath $root

$venvDir = Join-Path $root ".venv"
if (-not (Test-Path -LiteralPath $venvDir)) {
    Write-Host "Creating .venv ..." -ForegroundColor Cyan
    & python -m venv $venvDir
}

$py = Join-Path $venvDir "Scripts\python.exe"
if (-not (Test-Path -LiteralPath $py)) {
    Write-Host "ERROR: venv python not found. Install Python and add to PATH." -ForegroundColor Red
    exit 1
}

Write-Host "Starting offline assistant on port $Port -> http://127.0.0.1:$Port/docs" -ForegroundColor Green
& $py -m uvicorn main:app --host 0.0.0.0 --port $Port
