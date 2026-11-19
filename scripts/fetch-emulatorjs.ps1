# Descarga los archivos de EmulatorJS que la mini-app "gba" necesita y los deja en
# app/src/main/assets/miniapps/gba/emulatorjs. El resultado se versiona en git (igual que las
# teselas del mapa), asi el build y el APK funcionan sin red.
#
# Uso:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/fetch-emulatorjs.ps1
#       powershell -NoProfile -ExecutionPolicy Bypass -File scripts/fetch-emulatorjs.ps1 -Version nightly
#
# Notas:
# - La localizacion del CDN usa nombres de region ("en-US", "es-ES"); "en.json"/"es.json" no existen.
# - Los .data ya vienen comprimidos: no reenviamos compression/ y el APK apenas los reduce mas.
# - version.json se valida con ConvertFrom-Json y cada archivo se revisa contra tamanos minimos
#   y contra paginas HTML de error del CDN (un 404 soft devolveria HTML, no binario).
param(
    [string]$Version = "stable",
    [string]$Destination = (Join-Path $PSScriptRoot "../app/src/main/assets/miniapps/gba/emulatorjs")
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$Base = "https://cdn.emulatorjs.org/$Version/data"

# Solo el nucleo de Game Boy Advance (EJS_core = "gba" -> mgba). El "-legacy" cubre los equipos
# sin WebGL2, que EmulatorJS elige en tiempo de ejecucion. El extractor 7z es OBLIGATORIO: los
# nucleos .data van empaquetados en 7z y sin el el emulador muere en pantalla negra.
$Files = @(
    @{ Path = "loader.js";                   MinBytes = 4000 },
    @{ Path = "emulator.min.js";             MinBytes = 200000 },
    @{ Path = "emulator.min.css";            MinBytes = 15000 },
    @{ Path = "version.json";                MinBytes = 20 },
    @{ Path = "localization/en-US.json";     MinBytes = 5000 },
    @{ Path = "localization/es-ES.json";     MinBytes = 5000 },
    @{ Path = "cores/mgba-wasm.data";        MinBytes = 500000 },
    @{ Path = "cores/mgba-legacy-wasm.data"; MinBytes = 500000 },
    @{ Path = "cores/reports/mgba.json";     MinBytes = 40 },
    @{ Path = "compression/extract7z.js";    MinBytes = 100000 }
)

New-Item -ItemType Directory -Force -Path $Destination | Out-Null

$Total = 0
foreach ($File in $Files) {
    $Target = Join-Path $Destination $File.Path
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Target) | Out-Null
    $Url = "$Base/$($File.Path)"
    Write-Host "Descargando $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Target -UseBasicParsing -TimeoutSec 180

    $Size = (Get-Item $Target).Length
    if ($Size -lt $File.MinBytes) {
        throw "Archivo incompleto: $($File.Path) pesa $Size bytes (minimo $($File.MinBytes))"
    }

    # Un 404 "soft" del CDN devuelve HTML: sniff de los primeros bytes antes de aceptar el archivo.
    $Stream = [System.IO.File]::OpenRead($Target)
    $Head = New-Object byte[] 16
    $null = $Stream.Read($Head, 0, 16)
    $Stream.Close()
    if ([System.Text.Encoding]::ASCII.GetString($Head) -match '<!DOCTYPE|<html|<head') {
        throw "El CDN devolvio una pagina de error para $($File.Path)"
    }

    $Total += $Size
    "{0,10:N0} bytes  {1}" -f $Size, $File.Path
}

# version.json debe parsear como JSON real; su version queda registrada en VERSION.txt.
$VersionJson = Get-Content (Join-Path $Destination "version.json") -Raw | ConvertFrom-Json
if (-not $VersionJson.version) { throw "version.json no trae campo version" }

@"
EmulatorJS $($VersionJson.version) (canal: $Version)
Origen: https://cdn.emulatorjs.org/$Version/data/
Descargado con scripts/fetch-emulatorjs.ps1
Licencia: GPL-3.0 (ver app/src/main/assets/licenses/EmulatorJS-GPL-3.0.txt)
Solo se vendorizo el nucleo mgba (Game Boy Advance) y las localizaciones en-US/es-ES.
"@ | Set-Content (Join-Path $Destination "VERSION.txt") -Encoding utf8

"Total vendorizado: {0:N2} MB" -f ($Total / 1MB)
"VERSION.txt: EmulatorJS $($VersionJson.version)"
