# Writes the metrics token from .env to monitoring/metrics-token (which Git ignores), then starts Prometheus and Grafana.
$root = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) { Write-Error ".env not found"; exit 1 }

$vars = @{}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $name, $value = $line -split "=", 2
    $vars[$name.Trim()] = $value.Trim()
}
if (-not $vars["METRICS_TOKEN"] -or $vars["METRICS_TOKEN"].Length -lt 32) { Write-Error "METRICS_TOKEN in .env must be at least 32 characters"; exit 1 }
if (-not $vars["GRAFANA_PASSWORD"]) { Write-Error "GRAFANA_PASSWORD is missing from .env"; exit 1 }

[IO.File]::WriteAllText((Join-Path $root "monitoring\metrics-token"), $vars["METRICS_TOKEN"])
Push-Location $root
try { docker compose -f docker-compose.monitoring.yml up -d } finally { Pop-Location }
Write-Host "Prometheus: http://localhost:9090   Grafana: http://localhost:3000 (user admin, password GRAFANA_PASSWORD from .env)"