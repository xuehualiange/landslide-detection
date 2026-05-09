# LangChain + FastAPI chat (DeepSeek). Run inside langchain-chat-api folder.
# Windows PowerShell 5.1 compatible (Join-Path uses -Path, not -LiteralPath)
# Before run: $env:DEEPSEEK_API_KEY = "sk-..."
# If execution policy blocks: Set-ExecutionPolicy -Scope Process Bypass

param(
    [string]$Port = "8000",
    [string]$DeepSeekKey = ""
)

$ErrorActionPreference = "Stop"

# Resolve project root
$root = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($root)) {
    $p = $MyInvocation.MyCommand.Path
    if ([string]::IsNullOrWhiteSpace($p)) { $p = $PSCommandPath }
    if (-not [string]::IsNullOrWhiteSpace($p)) {
        $root = Split-Path -Parent $p
    }
}
$reqMarker = "requirements.txt"
if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path -LiteralPath (Join-Path -Path $root -ChildPath $reqMarker))) {
    $here = (Get-Location).ProviderPath
    if (Test-Path -LiteralPath (Join-Path -Path $here -ChildPath $reqMarker)) {
        $root = $here
    }
}
if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path -LiteralPath (Join-Path -Path $root -ChildPath $reqMarker))) {
    Write-Host "ERROR: Cannot find folder containing requirements.txt. cd to langchain-chat-api first." -ForegroundColor Red
    exit 1
}
Set-Location -LiteralPath $root

$key = $DeepSeekKey
if (-not $key) { $key = [Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "Process") }
if (-not $key) { $key = [Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "User") }
if (-not $key) {
    Write-Host "ERROR: Set DEEPSEEK_API_KEY first. Example: `$env:DEEPSEEK_API_KEY = 'sk-...'" -ForegroundColor Red
    exit 1
}

$env:DEEPSEEK_API_KEY = $key

$venvDir = Join-Path -Path $root -ChildPath ".venv"
if (-not (Test-Path -LiteralPath $venvDir)) {
    Write-Host "Creating .venv ..." -ForegroundColor Cyan
    & python -m venv $venvDir
}

$pip = Join-Path -Path $venvDir -ChildPath "Scripts\pip.exe"
$py  = Join-Path -Path $venvDir -ChildPath "Scripts\python.exe"
if (-not (Test-Path -LiteralPath $pip) -or -not (Test-Path -LiteralPath $py)) {
    Write-Host "ERROR: venv python not found. Install Python and add to PATH." -ForegroundColor Red
    exit 1
}

$req = Join-Path -Path $root -ChildPath "requirements.txt"
Write-Host "Installing dependencies..." -ForegroundColor Cyan
& $pip install -q -r $req

Write-Host "Starting API on port $Port -> http://127.0.0.1:$Port" -ForegroundColor Green
& $py -m uvicorn main:app --host 0.0.0.0 --port $Port