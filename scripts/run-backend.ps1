# Loads variables from .env into this process, then starts the backend.
$envFile = Join-Path $PSScriptRoot "..\.env"
if (-not (Test-Path $envFile)) {
    Write-Error ".env not found. Copy .env.example to .env and fill it in."
    exit 1
}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $name, $value = $line -split "=", 2
    Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
}
Push-Location (Join-Path $PSScriptRoot "..\backend")
try { & .\mvnw.cmd spring-boot:run } finally { Pop-Location }