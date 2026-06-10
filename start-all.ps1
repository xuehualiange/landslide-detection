param(
    [string]$JavaHome = "",
    [string]$MavenHome = "",
    [string]$NodeDir = "",
    [string]$DbUser = "root",
    [string]$DbPassword = "mysql",
    [string]$DeepSeekApiKey = "",
    [string]$ChatPort = "8000",
    [switch]$SkipAssistant,
    [switch]$ForceLangChain,
    [switch]$ForceOfflineAssistant,
    [switch]$SkipPreflight
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($projectRoot)) {
    $projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}

$backendDir = Join-Path $projectRoot "backend"
$frontendDir = Join-Path $projectRoot "frontend"
$langchainDir = Join-Path $projectRoot "langchain-chat-api"
$fastapiDir = Join-Path $projectRoot "fastapi-chat"
$langchainScript = Join-Path $langchainDir "start-langchain-chat.ps1"
$fastapiScript = Join-Path $fastapiDir "start-fastapi-chat.ps1"

function Resolve-JavaHome {
    param([string]$Override)
    if ($Override -and (Test-Path (Join-Path $Override "bin\java.exe"))) { return $Override }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { return $env:JAVA_HOME }
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $bin = Split-Path $javaCmd.Source -Parent
        return (Split-Path $bin -Parent)
    }
    $patterns = @(
        "C:\Program Files\Java\jdk*",
        "C:\Program Files\Eclipse Adoptium\jdk*",
        "C:\Program Files\Microsoft\jdk*",
        "C:\Program Files\JetBrains\*\jbr"
    )
    foreach ($pattern in $patterns) {
        $hit = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

function Resolve-MavenHome {
    param([string]$Override)
    if ($Override -and (Test-Path (Join-Path $Override "bin\mvn.cmd"))) { return $Override }
    if ($env:MAVEN_HOME -and (Test-Path (Join-Path $env:MAVEN_HOME "bin\mvn.cmd"))) { return $env:MAVEN_HOME }
    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvnCmd) {
        $bin = Split-Path $mvnCmd.Source -Parent
        return (Split-Path $bin -Parent)
    }
    $patterns = @(
        "C:\Program Files\Apache\maven*",
        "C:\apache-maven*",
        "E:\apache-maven*"
    )
    foreach ($pattern in $patterns) {
        $hit = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\mvn.cmd") } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

function Resolve-NodeDir {
    param([string]$Override)
    if ($Override -and (Test-Path (Join-Path $Override "npm.cmd"))) { return $Override }
    $npmCmd = Get-Command npm -ErrorAction SilentlyContinue
    if ($npmCmd) { return (Split-Path $npmCmd.Source -Parent) }
    if (Test-Path "C:\Program Files\nodejs\npm.cmd") { return "C:\Program Files\nodejs" }
    return $null
}

function Get-DeepSeekApiKey {
    param([string]$Override)
    if ($Override) { return $Override.Trim() }
    $k = [Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "Process")
    if ($k) { return $k.Trim() }
    $k = [Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "User")
    if ($k) { return $k.Trim() }
    $keyFile = Join-Path $langchainDir ".deepseek_key"
    if (Test-Path -LiteralPath $keyFile) {
        return (Get-Content -LiteralPath $keyFile -Raw -ErrorAction SilentlyContinue).Trim()
    }
    return ""
}

function Wait-HttpOk {
    param(
        [string]$Url,
        [int]$MaxSeconds = 120,
        [string]$Label = "service"
    )
    $elapsed = 0
    while ($elapsed -lt $MaxSeconds) {
        try {
            $null = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 4 -ErrorAction Stop
            return $true
        } catch {}
        try {
            $r = Invoke-RestMethod -Uri $Url -TimeoutSec 4 -ErrorAction Stop
            if ($r.code -eq 200 -and $r.data.status -eq "UP") { return $true }
            if ($r.status -eq "ok") { return $true }
        } catch {}
        Start-Sleep -Seconds 2
        $elapsed += 2
        if (($elapsed % 12) -eq 0) {
            Write-Host "  ... waiting for $Label (${elapsed}s / ${MaxSeconds}s)" -ForegroundColor DarkGray
        }
    }
    return $false
}

function Escape-SingleQuoted([string]$s) {
    if (-not $s) { return "" }
    return $s.Replace("'", "''")
}

function Test-PortListening([int]$Port) {
  return [bool](netstat -ano 2>$null | Select-String ":$Port\s")
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Landslide AI - start all services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$resolvedJava = Resolve-JavaHome -Override $JavaHome
$resolvedMaven = Resolve-MavenHome -Override $MavenHome
$resolvedNode = Resolve-NodeDir -Override $NodeDir

if (-not $resolvedJava) {
    Write-Host "FAIL: Java 17+ not found." -ForegroundColor Red
    Write-Host "  Install JDK 17+ and add java to PATH, or pass -JavaHome <path>" -ForegroundColor Yellow
    exit 1
}
if (-not $resolvedMaven) {
    Write-Host "FAIL: Maven not found." -ForegroundColor Red
    Write-Host "  Install Maven and add mvn to PATH, or pass -MavenHome <path>" -ForegroundColor Yellow
    exit 1
}
if (-not $resolvedNode) {
    Write-Host "FAIL: Node.js / npm not found." -ForegroundColor Red
    Write-Host "  Install Node.js 18+ from https://nodejs.org/ or pass -NodeDir <path>" -ForegroundColor Yellow
    exit 1
}
if (!(Test-Path $backendDir)) {
    Write-Host "FAIL: backend directory not found: $backendDir" -ForegroundColor Red
    exit 1
}
if (!(Test-Path $frontendDir)) {
    Write-Host "FAIL: frontend directory not found: $frontendDir" -ForegroundColor Red
    exit 1
}

Write-Host "Resolved tools:" -ForegroundColor DarkGray
Write-Host "  JAVA_HOME  = $resolvedJava"
Write-Host "  MAVEN_HOME = $resolvedMaven"
Write-Host "  Node.js    = $resolvedNode"
Write-Host ""

if (-not $SkipPreflight) {
    if (-not (Test-PortListening 3306)) {
        Write-Host "WARN: MySQL port 3306 is not listening. Import docs/db-schema-v2.sql first." -ForegroundColor Yellow
    }
    if (-not (Test-PortListening 6379)) {
        Write-Host "NOTE: Redis port 6379 not listening (optional; some features may degrade)." -ForegroundColor DarkGray
    }
}

$jh = Escape-SingleQuoted $resolvedJava
$mh = Escape-SingleQuoted $resolvedMaven
$nd = Escape-SingleQuoted $resolvedNode
$bd = Escape-SingleQuoted $backendDir
$fd = Escape-SingleQuoted $frontendDir
$dbUserEsc = Escape-SingleQuoted $DbUser
$dbPassEsc = Escape-SingleQuoted $DbPassword

$backendCommand = @"
`$ErrorActionPreference='Stop';
`$env:JAVA_HOME='$jh';
`$env:MAVEN_HOME='$mh';
`$env:Path='`$env:JAVA_HOME\bin;`$env:MAVEN_HOME\bin;' + `$env:Path;
`$env:DB_USERNAME='$dbUserEsc';
`$env:DB_PASSWORD='$dbPassEsc';
Set-Location '$bd';
Write-Host 'Building backend (mvn package)...' -ForegroundColor Cyan;
& mvn -q -DskipTests package;
if (`$LASTEXITCODE -ne 0) { Write-Host 'FAIL: mvn package failed' -ForegroundColor Red; exit 1 }
`$jar = Get-ChildItem .\target\*.jar | Where-Object { `$_.Name -notlike 'original-*' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1;
if (-not `$jar) { Write-Host 'FAIL: jar not found under target/' -ForegroundColor Red; exit 1 }
Write-Host "Starting backend: `$(`$jar.Name)" -ForegroundColor Green;
& java -jar `$jar.FullName
"@

$frontendCommand = @"
`$ErrorActionPreference='Stop';
`$env:Path='$nd;' + `$env:Path;
Set-Location '$fd';
if (!(Test-Path 'node_modules')) {
  Write-Host 'Installing frontend dependencies (npm install)...' -ForegroundColor Cyan;
  & '$nd\npm.cmd' install
}
Write-Host 'Starting frontend (npm run dev)...' -ForegroundColor Green;
& '$nd\npm.cmd' run dev
"@

Write-Host "[1/3] Starting backend (port 8080)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $backendCommand)

$healthUrl = "http://localhost:8080/api/health"
if (Wait-HttpOk -Url $healthUrl -MaxSeconds 150 -Label "backend") {
    Write-Host "      Backend is ready." -ForegroundColor Green
} else {
    Write-Host "      WARN: backend not ready within 150s. Check the backend window for errors." -ForegroundColor Yellow
}

Write-Host "[2/3] Starting frontend (port 5173)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $frontendCommand)
Start-Sleep -Seconds 1

$assistantMode = "none"
if (-not $SkipAssistant) {
    $apiKey = Get-DeepSeekApiKey -Override $DeepSeekApiKey
    $useLangChain = $false
    $useOffline = $false

    if ($ForceOfflineAssistant) {
        $useOffline = $true
    } elseif ($ForceLangChain) {
        if (-not $apiKey) {
            Write-Host "FAIL: -ForceLangChain requires DEEPSEEK_API_KEY or langchain-chat-api\.deepseek_key" -ForegroundColor Red
            exit 1
        }
        $useLangChain = $true
    } elseif ($apiKey) {
        $useLangChain = $true
    } else {
        $useOffline = $true
    }

    if ($useLangChain -and (Test-Path $langchainScript)) {
        $assistantMode = "langchain"
        $keyEsc = Escape-SingleQuoted $apiKey
        $langEsc = Escape-SingleQuoted $langchainDir
        $scriptEsc = Escape-SingleQuoted $langchainScript
        $assistantCommand = "`$env:DEEPSEEK_API_KEY='$keyEsc'; Set-Location -LiteralPath '$langEsc'; Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass; & '$scriptEsc' -Port '$ChatPort' -DeepSeekKey '$keyEsc'"
        Write-Host "[3/3] Starting LangChain assistant (port $ChatPort)..." -ForegroundColor Cyan
        Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $assistantCommand)
    } elseif ($useOffline -and (Test-Path $fastapiScript)) {
        $assistantMode = "offline"
        $fastEsc = Escape-SingleQuoted $fastapiDir
        $fastScriptEsc = Escape-SingleQuoted $fastapiScript
        $assistantCommand = "Set-Location -LiteralPath '$fastEsc'; Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass; & '$fastScriptEsc' -Port '$ChatPort'"
        Write-Host "[3/3] Starting offline assistant (port $ChatPort)..." -ForegroundColor Cyan
        Write-Host "      No DeepSeek key - using FAQ placeholder. Add langchain-chat-api\.deepseek_key to enable LangChain." -ForegroundColor Yellow
        Start-Process powershell -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $assistantCommand)
    } else {
        Write-Host "WARN: assistant scripts missing, skipped." -ForegroundColor Yellow
    }

    if ($assistantMode -ne "none") {
        $assistUrl = "http://localhost:$ChatPort/docs"
        if (Wait-HttpOk -Url $assistUrl -MaxSeconds 120 -Label "assistant") {
            Write-Host "      Assistant is ready ($assistantMode)." -ForegroundColor Green
        } else {
            Write-Host "      WARN: assistant not ready within 120s." -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "[3/3] Assistant skipped (-SkipAssistant)." -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Done - keep PowerShell windows open" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Frontend:  http://localhost:5173" -ForegroundColor Yellow
Write-Host "  Backend:   http://localhost:8080" -ForegroundColor Yellow
Write-Host "  Health:    http://localhost:8080/api/health" -ForegroundColor Yellow
if ($assistantMode -eq "langchain") {
    Write-Host "  Assistant: http://localhost:$ChatPort/docs (LangChain)" -ForegroundColor Yellow
} elseif ($assistantMode -eq "offline") {
    Write-Host "  Assistant: http://localhost:$ChatPort/docs (offline FAQ)" -ForegroundColor Yellow
}
Write-Host "  Login: superadmin / admin / monitor  password: 123456" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Overrides: -JavaHome -MavenHome -NodeDir -DbPassword -DeepSeekApiKey -SkipAssistant" -ForegroundColor DarkGray
Write-Host ""