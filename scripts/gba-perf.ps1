<#
.SYNOPSIS
    Captura una ventana de rendimiento del emulador GBA y emite el veredicto Go/No-Go de la Fase 2.

.DESCRIPTION
    El emulador escribe una linea "[perf] {json}" en logcat (etiqueta NahuaMiniApp) 60 s despues de
    arrancar una partida: fps, avg/p50/p95/max por frame, porcentaje fuera de presupuesto, frames
    perdidos y las latencias que reporta WebAudio.

    Este script limpia el buffer, espera esa linea y la traduce a una decision:
      - NO-GO  : el WebView sostiene el ritmo del GBA (>= 55 fps, <= 2% de frames fuera). No hay
                 razon tecnica para instalar el NDK: se queda EmulatorJS.
      - GO     : el frame pacing se cae. Ahi si se justifica un nucleo nativo (mGBA + AAudio),
                 porque el sintoma no es cosmetico: el audio se estira para compensar.

    Nota honesta sobre la medicion: requestAnimationFrame va clavado al vsync de la pantalla
    (16.67 ms) y no puede distinguir 59.7275 de 60 Hz. La deriva exacta del reloj del GBA se
    confirma escuchando el audio, no leyendo este reporte.

.EXAMPLE
    ./scripts/gba-perf.ps1

.EXAMPLE
    ./scripts/gba-perf.ps1 -TimeoutSeconds 300 -Serial emulator-5554
#>
param(
    [string]$Adb = "",
    [string]$Serial = "",
    [int]$TimeoutSeconds = 240,
    [string]$Package = "com.nahuanotoca.superapp.debug"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Resolve-Adb {
    param([string]$Explicit)
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($Explicit)) { $candidates += $Explicit }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") }
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) { $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe") }
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw "No encuentro adb. Pasa -Adb C:\ruta\a\adb.exe o instala platform-tools."
}

$adb = Resolve-Adb -Explicit $Adb
Write-Host "adb: $adb"

$deviceArgs = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $deviceArgs = @("-s", $Serial) }

$devices = & $adb devices
$online = @($devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" })
if ($online.Count -eq 0) {
    Write-Host ""
    Write-Host "No hay ningun dispositivo conectado (o falta autorizar la depuracion USB)." -ForegroundColor Yellow
    Write-Host "  1. Conecta el telefono por USB y acepta 'Permitir depuracion USB'."
    Write-Host "  2. ./gradlew :app:installDebug"
    Write-Host "  3. Vuelve a correr este script."
    exit 1
}
if ($online.Count -gt 1 -and [string]::IsNullOrWhiteSpace($Serial)) {
    Write-Warning "Hay varios dispositivos; uso el primero. Para elegir otro: -Serial <id>"
}

function Get-Prop([string]$Name) {
    $value = & $adb @deviceArgs shell getprop $Name 2>$null
    if ($null -eq $value) { return "" }
    return ($value -join "").Trim()
}

$model = Get-Prop "ro.product.model"
$sdk = Get-Prop "ro.build.version.sdk"
$abis = Get-Prop "ro.product.cpu.abilist"
Write-Host "Dispositivo: $model (API $sdk, $abis)"

& $adb @deviceArgs logcat -c | Out-Null
Write-Host ""
Write-Host "Buffer limpio. Ahora, en el telefono:" -ForegroundColor Cyan
Write-Host "  1. Abre Nahua y entra al Emulador GBA."
Write-Host "  2. Carga una ROM exigente (Modo 7 + musica: Mario Kart, Golden Sun)."
Write-Host "  3. Juega 70 s sin salir. El reporte sale solo a los 60 s."
Write-Host ""
Write-Host "Esperando la linea [perf] (max $TimeoutSeconds s)..."

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$line = $null
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    $dump = & $adb @deviceArgs logcat -d -s NahuaMiniApp:D 2>$null
    if ($null -eq $dump) { continue }
    $hits = @($dump | Where-Object { $_ -match "\[perf\]" })
    if ($hits.Count -gt 0) {
        $line = $hits[$hits.Count - 1]
        break
    }
}

if ($null -eq $line) {
    Write-Host ""
    Write-Error "No llego ningun [perf] en $TimeoutSeconds s. Revisa que el emulador arrancara la partida (la medicion empieza al cargar la ROM, no en la portada)."
}

$json = $line.Substring($line.IndexOf("{"))
$perf = $json | ConvertFrom-Json

$frames = [double]$perf.frames
$dropped = [double]$perf.droppedFrames
$dropPct = if ($frames -gt 0) { [math]::Round(($dropped / $frames) * 100, 2) } else { 0 }
$fps = [double]$perf.fps
$overBudget = [double]$perf.overBudgetPct

Write-Host ""
Write-Host "--- Reporte ($($perf.label), $($perf.seconds) s) ---"
$summary = [ordered]@{
    "fps"                 = $fps
    "media ms"            = $perf.avgMs
    "p50 ms"              = $perf.p50Ms
    "p95 ms"              = $perf.p95Ms
    "peor frame ms"       = $perf.maxMs
    "fuera de presupuesto" = "$overBudget %"
    "frames perdidos"     = "$dropped ($dropPct % de $frames)"
}
if ($null -ne $perf.audio) {
    $summary["audio"] = "48k? $($perf.audio.sampleRate) Hz, baseLatency $($perf.audio.baseLatencyMs) ms, outputLatency $($perf.audio.outputLatencyMs) ms, state $($perf.audio.state)"
}
$summary.GetEnumerator() | ForEach-Object { Write-Host ("  {0,-22} {1}" -f $_.Key, $_.Value) }

$perfDir = Join-Path $PSScriptRoot "..\app\build\perf"
New-Item -ItemType Directory -Force -Path $perfDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outFile = Join-Path $perfDir ("gba-perf-" + $stamp + ".json")
@{
    capturedAt = (Get-Date).ToString("o")
    device     = @{ model = $model; sdk = $sdk; abis = $abis }
    perf       = $perf
} | ConvertTo-Json -Depth 6 | Set-Content -Path $outFile -Encoding UTF8
Write-Host ""
Write-Host "Guardado: $(Resolve-Path $outFile)"

Write-Host ""
$healthy = ($fps -ge 55) -and ($overBudget -le 2) -and ($dropPct -le 2)
if ($healthy) {
    Write-Host "VEREDICTO: NO-GO a la Fase 2." -ForegroundColor Green
    Write-Host "  El WebView sostiene el ritmo del GBA en este dispositivo. Instalar NDK + mGBA + AAudio"
    Write-Host "  no compra nada que se note: el siguiente paso barato es exportar/importar .srm y seguir."
} else {
    Write-Host "VEREDICTO: GO a la Fase 2." -ForegroundColor Yellow
    Write-Host "  El frame pacing se cae (fps $fps, $overBudget% de frames fuera, $dropPct% perdidos)."
    Write-Host "  Hay justificacion tecnica para el nucleo nativo: mGBA (MPL-2.0) + AAudio + renderer EGL."
}
Write-Host ""
Write-Host "Recuerda: si el audio suena cortado con estos numeros ya 'sanos', el reporte no lo vera nunca"
Write-Host "(rAF no distingue 59.7275 de 60 Hz). Escuchalo tu antes de dar el veredicto por bueno."
