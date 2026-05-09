param(
    [string]$JavaHome = "C:\Program Files\JetBrains\PyCharm 2024.2.4\jbr",
    [string]$MavenHome = "E:\apache-maven-3.9.6",
    [string]$NodeDir = "C:\Program Files\nodejs",
    [string]$DbUser = "root",
    [string]$DbPassword = "mysql",
    [string]$DeepSeekApiKey = "",
    [string]$ChatPort = "8000",
    [switch]$SkipAssistant
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $projectRoot "backend"
$frontendDir = Join-Path $projectRoot "frontend"
$langchainDir = Join-Path $projectRoot "langchain-chat-api"
$backendScript = Join-Path $backendDir "scripts\start-backend.ps1"
$assistantScript = Join-Path $langchainDir "start-langchain-chat.ps1"

if (!(Test-Path $backendScript)) {
    Write-Host "FAIL: backend start script not found: $backendScript" -ForegroundColor Red
    exit 1
}
if (!(Test-Path (Join-Path $NodeDir "npm.cmd"))) {
    Write-Host "FAIL: npm.cmd not found under $NodeDir" -ForegroundColor Red
    exit 1
}
if (!(Test-Path (Join-Path $MavenHome "bin\mvn.cmd"))) {
    Write-Host "FAIL: Maven not found under $MavenHome" -ForegroundColor Red
    exit 1
}

$backendCommand = @"
`$env:MAVEN_HOME='$MavenHome';
`$env:Path='`$env:MAVEN_HOME\bin;' + `$env:Path;
`$env:DB_USERNAME='$DbUser';
`$env:DB_PASSWORD='$DbPassword';
Set-Location '$backendDir';
powershell -ExecutionPolicy Bypass -File '$backendScript' -JavaHome '$JavaHome'
"@

$frontendCommand = @"
`$env:Path='$NodeDir;' + `$env:Path;
Set-Location '$frontendDir';
if (!(Test-Path 'node_modules')) { & '$NodeDir\npm.cmd' install }
& '$NodeDir\npm.cmd' run dev
"@

Write-Host "Starting backend (Spring Boot 8080)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-Command", $backendCommand
)

$healthUrl = "http://localhost:8080/api/health"
$maxWaitSec = 150
$elapsed = 0
Write-Host "Waiting for backend (GET $healthUrl)..." -ForegroundColor Cyan
$backendUp = $false
while ($elapsed -lt $maxWaitSec) {
    try {
        $r = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 4 -ErrorAction Stop
        if ($r.code -eq 200 -and $r.data.status -eq "UP") {
            $backendUp = $true
            break
        }
    } catch {}
    Start-Sleep -Seconds 2
    $elapsed += 2
    if (($elapsed % 12) -eq 0) {
        Write-Host "  ... still waiting (${elapsed}s / ${maxWaitSec}s)" -ForegroundColor DarkGray
    }
}
if (-not $backendUp) {
    Write-Host "WARN: Backend not UP within ${maxWaitSec}s — frontend may show disconnect until Spring is ready." -ForegroundColor Yellow
}
else {
    Write-Host "Backend health OK — starting frontend." -ForegroundColor Green
}

Write-Host "Starting frontend (Vite 5173)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-Command", $frontendCommand
)

Start-Sleep -Seconds 1

if (-not $SkipAssistant) {
    $apiKey = $DeepSeekApiKey
    if (-not $apiKey) { $apiKey = [Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "Process") }
    if (-not $apiKey) { $apiKey = [Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "User") }
    $keyFile = Join-Path $langchainDir ".deepseek_key"
    if (-not $apiKey -and (Test-Path -LiteralPath $keyFile)) {
        $apiKey = (Get-Content -LiteralPath $keyFile -Raw -ErrorAction SilentlyContinue).Trim()
    }

    if (-not $apiKey) {
        Write-Host ""
        Write-Host "SKIP: LangChain assistant (port $ChatPort) - no DEEPSEEK_API_KEY." -ForegroundColor Yellow
        Write-Host "      Set `$env:DEEPSEEK_API_KEY OR create $keyFile (one line) OR -DeepSeekApiKey sk-..." -ForegroundColor Yellow
    }
    elseif (!(Test-Path $assistantScript)) {
        Write-Host "WARN: start-langchain-chat.ps1 missing; skipping assistant." -ForegroundColor Yellow
    }
    else {
        $keyEsc = $apiKey.Replace("'", "''")
        $langEsc = $langchainDir.Replace("'", "''")
        $scriptEsc = $assistantScript.Replace("'", "''")
        $assistantCommand = "`$env:DEEPSEEK_API_KEY='$keyEsc'; Set-Location -LiteralPath '$langEsc'; Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass; & '$scriptEsc' -Port '$ChatPort' -DeepSeekKey '$keyEsc'"

        Write-Host "Starting LangChain assistant (Python $ChatPort)..." -ForegroundColor Cyan
        Start-Process powershell -ArgumentList @(
            "-NoExit",
            "-ExecutionPolicy", "Bypass",
            "-Command", $assistantCommand
        )

        $assistUrl = "http://localhost:$ChatPort/docs"
        $maxA = 120
        $ea = 0
        Write-Host "Waiting for assistant ($assistUrl)..." -ForegroundColor Cyan
        $assistUp = $false
        while ($ea -lt $maxA) {
            try {
                $null = Invoke-WebRequest -Uri $assistUrl -UseBasicParsing -TimeoutSec 4 -ErrorAction Stop
                $assistUp = $true
                break
            } catch {}
            Start-Sleep -Seconds 2
            $ea += 2
            if (($ea % 12) -eq 0) {
                Write-Host "  ... assistant still starting (${ea}s / ${maxA}s)" -ForegroundColor DarkGray
            }
        }
        if (-not $assistUp) {
            Write-Host "WARN: Assistant HTTP not ready within ${maxA}s — check the LangChain window." -ForegroundColor Yellow
        }
        else {
            Write-Host "Assistant HTTP OK." -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "Done. Watch the PowerShell windows (backend | frontend | assistant)." -ForegroundColor Green
Write-Host "  Frontend:      http://localhost:5173" -ForegroundColor Yellow
Write-Host "  Backend:       http://localhost:8080 | Health: http://localhost:8080/api/health" -ForegroundColor Yellow
if (-not $SkipAssistant) {
    Write-Host "  Assistant API: http://localhost:$ChatPort/docs" -ForegroundColor Yellow
}
Write-Host ""
