param(
    [string]$JavaHome = "C:\Program Files\JetBrains\PyCharm 2024.2.4\jbr",
    [string]$MavenHome = "E:\apache-maven-3.9.6",
    [string]$ModelPath = "models/landslide-yolov8.onnx",
    [string]$ImagePath = "",
    [switch]$Rebuild
)
$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $PSScriptRoot
Set-Location $backendRoot
$env:JAVA_HOME = $JavaHome
$env:MAVEN_HOME = $MavenHome
$env:Path = "$JavaHome\bin;$MavenHome\bin;" + $env:Path
$jar = Get-ChildItem .\target\*.jar -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike 'original-*' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($Rebuild -or -not $jar) {
    Write-Host "mvn package..." -ForegroundColor Cyan
    mvn -q -DskipTests package
    $jar = Get-ChildItem .\target\*.jar | Where-Object { $_.Name -notlike 'original-*' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}
$loaderArgs = @(
    "-Dloader.main=com.graduation.landslide.ai.YoloDetectorBenchmark",
    "-cp", $jar.FullName,
    "org.springframework.boot.loader.PropertiesLauncher",
    $ModelPath
)
if ($ImagePath) { $loaderArgs += $ImagePath }
Write-Host "Jar: $($jar.Name)" -ForegroundColor Cyan
& java @loaderArgs
