param(
    [string]$DataYaml = "E:/landslide-yolo/landslide.yaml",
    [string]$Project = "E:/yolo-runs",
    [string]$Name = "bijie_v2",
    [int]$Epochs = 100,
    [int]$Batch = 16,
    [string]$Device = "0",
    [float]$EvalConf = 0.45,
    [switch]$SkipTrain
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$evalScript = Join-Path $root "tools\eval_image_level_confusion.py"
$weights = Join-Path $Project "$Name\weights\best.pt"
if (-not $SkipTrain) {
    py -3.9 -m ultralytics yolo detect train data=$DataYaml model=yolov8n.pt imgsz=640 epochs=$Epochs batch=$Batch device=$Device project=$Project name=$Name
}
py -3.9 -m ultralytics yolo detect val model=$weights data=$DataYaml device=$Device conf=$EvalConf
py -3.9 $evalScript --model $weights --data $DataYaml --split val --conf $EvalConf --out (Join-Path $Project $Name)