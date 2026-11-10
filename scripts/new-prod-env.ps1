# Creates .env.prod with freshly generated secrets. It refuses to run if the file exists, because a new DB_PASSWORD
# would no longer match an existing database, and a new ENCRYPTION_KEY would make stored readings unreadable.
param([string]$Domain = "localhost")

$target = Join-Path (Split-Path $PSScriptRoot -Parent) ".env.prod"
if (Test-Path $target) {
    Write-Error ".env.prod already exists, so nothing was changed. Delete it yourself if you really want new secrets."
    exit 1
}

function New-Base64Key {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    [Convert]::ToBase64String($bytes)
}
$dbPassword = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })

$lines = @(
    "DOMAIN=$Domain",
    "HSTS_MAX_AGE=0",
    "DB_PASSWORD=$dbPassword",
    "JWT_SECRET=$(New-Base64Key)",
    "ENCRYPTION_KEY=$(New-Base64Key)"
)
[IO.File]::WriteAllText($target, (($lines -join "`n") + "`n"))
Write-Host ".env.prod created. Save a copy of it in your password manager now."
Write-Host "Losing ENCRYPTION_KEY makes every stored reading permanently unreadable."