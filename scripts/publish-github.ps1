<#
.SYNOPSIS
    Crea el repositorio en GitHub y sube la rama main.

.DESCRIPTION
    Usa la API de GitHub para crear el repositorio y luego hace push por HTTPS con el token.
    El token nunca se guarda en .git/config: solo se usa en la URL del push.

.EXAMPLE
    $env:GITHUB_TOKEN = "github_pat_..."
    ./scripts/publish-github.ps1
#>
param(
    [string]$Token = $env:GITHUB_TOKEN,
    [string]$Owner = "",
    [string]$Repo = "nahua-superapp",
    [ValidateSet("public", "private")]
    [string]$Visibility = "public",
    [string]$Description = "Nahua - super app Android: hub de servicios, cartera y mini-apps (Kotlin + Jetpack Compose)"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Token)) {
    Write-Error "Falta el token. Define GITHUB_TOKEN o pasa -Token (permisos: Contents read/write y Administration read/write)."
}

$headers = @{
    Authorization          = "Bearer $Token"
    Accept                 = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
    "User-Agent"           = "nahua-publish-script"
}

Write-Host "Consultando usuario autenticado..." -ForegroundColor Cyan
$user = Invoke-RestMethod -Uri "https://api.github.com/user" -Headers $headers -Method Get
if ([string]::IsNullOrWhiteSpace($Owner)) { $Owner = $user.login }
Write-Host "  usuario: $($user.login) / destino: $Owner/$Repo"

$body = @{
    name        = $Repo
    description = $Description
    private     = ($Visibility -eq "private")
    has_issues  = $true
    has_wiki    = $false
} | ConvertTo-Json

$createUri = if ($Owner -eq $user.login) {
    "https://api.github.com/user/repos"
} else {
    "https://api.github.com/orgs/$Owner/repos"
}

try {
    Write-Host "Creando repositorio..." -ForegroundColor Cyan
    $created = Invoke-RestMethod -Uri $createUri -Headers $headers -Method Post -Body $body -ContentType "application/json"
    Write-Host "  creado: $($created.html_url)"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -eq 422) {
        Write-Host "  el repositorio ya existe, continúo con el push." -ForegroundColor Yellow
    } else {
        throw
    }
}

$remoteUrl = "https://github.com/$Owner/$Repo.git"
git remote remove origin 2>$null | Out-Null
git remote add origin $remoteUrl

Write-Host "Subiendo main..." -ForegroundColor Cyan
$pushUrl = "https://x-access-token:$Token@github.com/$Owner/$Repo.git"
git push $pushUrl "main:main" --follow-tags
if ($LASTEXITCODE -ne 0) { throw "git push falló con código $LASTEXITCODE" }

Write-Host ""
Write-Host "Listo: https://github.com/$Owner/$Repo" -ForegroundColor Green
