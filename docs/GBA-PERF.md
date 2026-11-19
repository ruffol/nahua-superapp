# Emulador GBA: medir antes de reescribir

El emulador corre hoy sobre EmulatorJS (WebAssembly) dentro del WebView. La alternativa —un núcleo
nativo `mGBA` + `AAudio` + renderer EGL en un módulo NDK— es la única forma de quitar los techos del
WebView (audio sin resampleo forzado, háptica por frame, latencia de entrada), pero cuesta semanas y
deja dos motores que mantener. Por eso la decisión no se toma por intuición: se toma con números.

## Qué se mide

La mini-app `gba` instrumenta cada partida (`app/src/main/assets/miniapps/gba/gba.js`) y publica una
línea `[perf] {json}` en logcat, etiqueta `NahuaMiniApp`, a los 60 s de haber cargado la ROM:

| Campo | Qué dice |
|---|---|
| `fps` | frames realmente presentados en la ventana |
| `avgMs`, `p50Ms`, `p95Ms`, `maxMs` | tiempo entre frames |
| `overBudgetPct` | % de frames por encima de 1.5× el presupuesto del GBA |
| `droppedFrames` | frames perdidos acumulados |
| `audio.sampleRate`, `baseLatencyMs`, `outputLatencyMs`, `state` | lo que reporta WebAudio, el piso de latencia antes de tocar el audio del sistema |

El presupuesto es **16.746 ms**, no 16.67: el GBA refresca a **59.7275 Hz** (16.777216 MHz / 280896
ciclos por frame). Un frame de 16.7 ms ya está fuera del ritmo del hardware, aunque se vea bien.

## Qué NO mide (y hay que decirlo)

- **La deriva 59.7275 vs 60 Hz.** `requestAnimationFrame` está clavado al vsync de la pantalla
  (16.67 ms), así que no puede distinguir los dos relojes. Si el audio suena cortado con un reporte
  "sano", el problema existe igual: se confirma escuchando, y si hace falta con
  `chrome://inspect` mientras el juego corre.
- **Latencia de entrada.** Necesita el dispositivo en la mano.
- **El techo de gama media.** Una medición es de un teléfono. Si el objetivo comercial incluye
  equipos de gama baja, hay que repetir la captura en uno de ellos.

## Cómo se captura

```bash
./gradlew :app:installDebug          # o el APK firmado que uses para probar
./scripts/gba-perf.ps1               # limpia el buffer, espera el reporte y emite el veredicto
```

El script guarda cada captura en `app/build/perf/gba-perf-<fecha>.json` (ignorado por git) con el
modelo, API y ABI del dispositivo. En Linux/macOS el equivalente es:

```bash
adb logcat -c && adb logcat -s NahuaMiniApp:D | grep --line-buffered '\[perf\]'
```

Para medir a mano más de una vez sin reiniciar la partida, desde `chrome://inspect`:

```js
NahuaPerf.stop()                    // reporta lo acumulado y detiene
NahuaPerf.start()                   // ventana nueva
NahuaPerf.report('prueba-manual')   // resumen sin detener
```

## Criterio Go / No-Go

| Resultado | Decisión |
|---|---|
| `fps ≥ 55`, `overBudgetPct ≤ 2`, `droppedFrames ≤ 2 %` y el audio se escucha limpio | **No-Go.** El WebView sostiene el ritmo. Lo barato pendiente es exportar/importar `.srm` y seguir con el producto. |
| `fps < 55`, o `overBudgetPct > 5`, o audio estirado/cortado de forma audible | **Go.** Hay justificación técnica para el núcleo nativo. |
| Zona intermedia (55-59 fps estables con audio limpio) | No-Go por ahora: el listón del GBA es 59.7275 y la diferencia no se percibe en juego real. Repetir la captura en el equipo más lento que se quiera soportar. |

`./scripts/gba-perf.ps1` aplica la primera fila automáticamente y lo imprime como veredicto.

## Si el veredicto es Go

El orden de trabajo, de menor a mayor riesgo, y sin tocar `RomStore.kt` ni el catálogo:

1. **Toolchain**: falta el NDK y CMake en el SDK local (`AppData\Local\Android\Sdk` no los tiene).
   `sdkmanager "ndk;27.0.12077973" "cmake;3.22.1"`.
2. **Núcleo**: compilar `mGBA` (MPL-2.0, copyleft por archivo) para `arm64-v8a` y `armeabi-v7a`;
   expone `mgba/core/core.h` con un contrato estable (init/reset/runFrame).
3. **Vídeo**: `SurfaceView` + EGL (el renderer GL de mGBA) en lugar del canvas del WebView.
4. **Audio**: `AAudio` en modo `LOW_LATENCY` con ring buffer y resampler al periodo real del GBA;
   aquí es donde se gana lo que el WebView no puede dar.
5. **Empaquetado**: los `.so` viajan **dentro del mismo APK** (~1-2 MB por ABI sobre los 22 MB
   actuales). Play Feature Delivery queda descartado: la app se distribuye como APK sideloaded con
   su propio actualizador (`core/update/ApkInstaller.kt`), y los módulos dinámicos solo existen en
   AAB repartido por Play.
6. **Interfaz**: mantener `display: immersive` y la tira nativa Salir/Inicio/Menú; el gamepad táctil
   pasa a Compose sobre el `SurfaceView` (ahí la háptica es nativa de verdad, sin puente).
