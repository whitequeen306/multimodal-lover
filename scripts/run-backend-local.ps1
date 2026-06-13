# 本地启动后端：自动加载项目根目录 .env（IDE 直接 Run 不会读 .env，请用这个或 launch.json）
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    Write-Error ".env 不存在，请先 copy .env.example .env 并填写"
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $name = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

Write-Host "MYSQL_HOST=$env:MYSQL_HOST MYSQL_PORT=$env:MYSQL_PORT" -ForegroundColor Cyan
Set-Location (Join-Path $root "backend")
mvn -pl virtual-lover-app spring-boot:run
