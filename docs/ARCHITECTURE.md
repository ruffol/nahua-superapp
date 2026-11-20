# Arquitectura

## Principios

1. **Una app, muchos servicios.** El catálogo (`ServiceCatalog`) es la única fuente de verdad:
   agregar un servicio es una entrada de datos, no una pantalla nueva.
2. **La UI no sabe de dónde vienen los datos.** Las pantallas hablan con ViewModels; los ViewModels
   con repositorios; los repositorios con almacenamiento o red.
3. **El dinero se modela con enteros.** Todos los montos son centavos (`Long`) y la aritmética vive
   en `WalletLedger`, una clase pura sin dependencias de Android.
4. **Las mini-apps son contenido, no código privilegiado.** Corren en un WebView aislado y solo
   pueden hablar con el host a través de `MiniAppBridge`.

## Capas

```
┌───────────────────────────────────────────────┐
│ UI (Compose)                                  │
│ feature/* · core/design                       │
├───────────────────────────────────────────────┤
│ Presentación (ViewModels)                     │
│ StateFlow · viewModelScope                    │
├───────────────────────────────────────────────┤
│ Dominio                                      │
│ core/model (WalletLedger, Money, Service)     │
├───────────────────────────────────────────────┤
│ Datos                                        │
│ core/data/* · DataStore · assets              │
└───────────────────────────────────────────────┘
```

### Inyección de dependencias

`AppContainer` se crea una vez en `NahuaApplication` y expone repositorios. En Compose,
`rememberAppViewModel { HomeViewModel(it) }` construye el ViewModel con el contenedor.

Para migrar a Hilt:

1. Anotar `NahuaApplication` con `@HiltAndroidApp` y `MainActivity` con `@AndroidEntryPoint`.
2. Convertir `AppContainer` en módulos `@Provides`.
3. Cambiar `rememberAppViewModel` por `hiltViewModel()`.

### Persistencia

| Dato | Almacén | Claves |
| --- | --- | --- |
| Saldo y movimientos | Preferences DataStore `nahua_wallet` | `balance_cents`, `transactions_json` |
| Tema, notificaciones, biometría | Preferences DataStore `nahua_preferences` | `theme_mode`, `notifications_enabled`, `biometrics_enabled` |

El saldo semilla (MXN 2,500) se materializa con el primer movimiento para que la demo sea usable
sin backend.

## RAG local

`Nahua IA` es un feature nativo (no mini-app) porque necesita acceso al selector de archivos, al
extractor de PDF y a la red con control de errores. La cadena es:
`PdfExtractor → TextChunker → RagRepository → Retriever → ChatClient`. El detalle vive en
[RAG.md](RAG.md).

## Ubicación

`LocationProvider` usa `LocationManager` (sin Google Play Services). Tiene dos modos:

- `currentFix()`: fix puntual; prefiere una lectura cacheada reciente y, si no hay, espera GPS (3 s)
  y red (2 s). Devuelve `null` si no hay permiso o lectura; nunca inventa una posición.
- `locationUpdates()`: `Flow` continuo con lat/lng, precisión, velocidad, rumbo y altitud para el
  mapa en vivo. Se cancela al cerrar la mini app o al ocultar la pantalla.

El puente expone `Nahua.location()`, `Nahua.watchLocation()`, `Nahua.openDirections()` y
`Nahua.copy()`. El permiso se pide desde la pantalla anfitriona con `RequestPermission` (fine o
coarse en Android 12+), de modo que la mini-app nunca ve el diálogo del sistema.

## Actualizaciones OTA

`UpdateRepository` consulta `releases/latest` de GitHub, compara versiones con `Version` y descarga
el APK a caché verificando el SHA-256 cuando la release lo publica (`digest`). `ApkInstaller` usa
`PackageInstaller` con un `PendingIntent` mutable y un `BroadcastReceiver` que lanza la confirmación
del sistema. El repositorio incluye una llave de firma de demo para que CI y las builds locales
compartan firma; en producción debe sustituirse.

## Runtime de mini-apps

```
assets/miniapps/index.json  ──►  MiniAppRegistry  ──►  MiniAppManifest
      │                                        (display, orientation)
      │                                                  │
      │         /assets/  bundles HTML + emulatorjs/      │
      │         /rom/     filesDir/roms (ROMs del GBA)    │
      ▼                                                  ▼
MiniAppScreen ──► MiniAppWebView ──► MiniAppAssetLoader ─┘
   (chrome |      │      │
   inmersivo)     │      └── request a otro host → 404 vacío (offline garantizado)
       │          └── window.NahuaNative (addJavascriptInterface)
       └── MiniAppBridge ──► WalletRepository.pay() / LocationManager / geo: intent
                            / RomStore (filesDir/roms) para el emulador
```

- **Origen seguro**: los assets se sirven en `https://appassets.androidplatform.net/assets/...`,
  así el WebView no necesita `file://` ni tráfico en claro. Las ROMs del emulador se sirven de
  `/rom/` (`InternalStoragePathHandler` sobre `filesDir/roms`): la página solo ve ese directorio,
  nunca `databases/` ni `shared_prefs/`.
- **Offline por diseño**: todo request `http(s)` a otro host se responde con un 404 vacío en
  `shouldInterceptRequest`. EmulatorJS trae un *failsafe* que cae a `cdn.emulatorjs.org` si falta
  un archivo del núcleo; con el bloqueo no hay tráfico real ni timeouts, el error es inmediato.
- **Puente mínimo**: `getSession`, `pay`, `toast`, `close`, `log`, `haptic`, `announceBarter` y
  el catálogo de las tiendas (`catalogHead`/`catalogFull`/`catalogDelta`/`catalogOrder`/
  `catalogReport`), más `pickRom`/`listRoms`/`deleteRom` para el emulador.
- **Confirmación obligatoria**: ningún pago se ejecuta desde JS; siempre pasa por una hoja de
  confirmación en Compose y por `WalletRepository`.
- **Resultado asíncrono**: el puente responde con `window.Nahua._resolve(id, payload)`, que resuelve
  o rechaza el `Promise` del SDK. El catálogo usa el mismo canal: la petición HTTP corre en un hilo
  propio (`CatalogApi`) y el resultado se entrega al volver al hilo principal.
- **Ciclo de vida**: `ON_PAUSE`/`ON_RESUME` pausan el WebView (`onPause` + `pauseTimers`), así el
  audio del emulador no sigue sonando en segundo plano. Si el proceso de render muere, la app
  sobrevive (`onRenderProcessGone` → `true`) y el WebView se recrea (`recreateSignal`).
- **Presupuesto del emulador**: el bundle de EmulatorJS 4.2.3 (solo núcleo mGBA, dos variantes
  wasm y dos idiomas) pesa **2.74 MB** vendorizados en `assets/miniapps/gba/emulatorjs/`, sin
  ROMs ni BIOS; véase [MINIAPP_SDK.md](MINIAPP_SDK.md).
- **Medir antes de reescribir**: la mini-app `gba` instrumenta su propio frame pacing y las
  latencias que reporta WebAudio (`[perf]` en logcat) durante 60 s por partida. La decisión de
  bajar a un núcleo nativo (mGBA + AAudio) depende de esos números, no de la intuición: el GBA
  refresca a 59.7275 Hz y el vsync de la pantalla a ~60, así que el WASM solo se justifica fuera
  de presupuesto si el frame pacing se cae o el audio se estira. Procedimiento y criterio Go/No-Go
  en [GBA-PERF.md](GBA-PERF.md).

Más detalle en [MINIAPP_SDK.md](MINIAPP_SDK.md).

## Bazar/Trueque y tiendas oficiales

El bazar no es una pantalla nativa: es una mini-app (`assets/miniapps/bazar/`). Dentro viven dos
cosas: las **organizaciones oficiales** con catálogo y stock, y el **bazar de la comunidad**.

- **Sin valoración automática**: el precio lo pone quien publica. Se retiró `BarterValuator` (y su
  puente `valuate`/`compareBarter`) porque el número inventado por la app generaba desconfianza. El
  SDK sube a `v1.6` sin esos métodos.
- **Portada**: los banners de las tiendas oficiales, sin bloque de texto previo. Cada banner abre la
  tienda; el chip de frescura dice si el catálogo está al día o sin actualizar.
- **Tienda**: primero los productos que la tienda fijó (`pinned`) para impulsar la venta, después los
  últimos agregados (`createdAt` descendente), con stock visible y «Agotado» cuando llega a 0.
- **Catálogo versionado** (`catalogo.js`): foto completa una sola vez y parches con altas, bajas,
  precios y stock; `snapshot(version)` reconstruye cualquier versión anterior, así que lo retirado
  sigue consultable. `audit()` compara las fotos reconstruidas y devuelve la actividad con valor
  anterior y nuevo. `applyRemote()` aplica el delta del servidor sin volver a pedir la foto.
- **Red en el anfitrión**: la WebView responde 404 a cualquier request externo, así que el catálogo
  llega por el puente (`Nahua.catalogHead/Full/Delta`) y `CatalogApi` hace el HTTP en un hilo propio
  contra `https://tienda.tlalchichi.xyz`. Sin `NahuaNative` (navegador) el bazar cae al catálogo de
  demostración del bundle y lo dice.
- **Frescura**: `syncedAt()` + la vigencia que declara `/head` (`validUntil`) deciden el estado del
  chip: al día, con cambios, sin actualizar o sin conexión. En los tres últimos la compra pide una
  segunda confirmación.
- **Órdenes**: al pagar se confirma en `/v1/orders` con el `catalogVersion` que vio el usuario; si el
  servidor responde `price_changed` u `out_of_stock` se avisa y se vuelve a pedir el catálogo.
- **Reportes**: artículo o usuario con motivo y nota; se guardan en `data.reports` con fecha, se
  anuncian en la bandeja (`announceBarter`) como `NoticeKind.MARKET` y se envían a `/v1/reports`
  cuando hay servidor.
- **Regla del match**: `interests` + `matches`; un chat sólo existe si `wantedFromMe(other)` y
  `wantedByMe(other)` tienen al menos un objeto. El interés recibido se resume en conteos anónimos y
  el aviso con nombres se emite únicamente al crear el match.
- **Contacto**: nombre + teléfono de 10 dígitos (`formatPhone`), visible sólo dentro de un match.
- **Persistencia**: comunidad en `nahua.bazar.v4` y catálogo en `nahua.bazar.catalogo.v1`, ambos en
  `localStorage` del WebView. Contrato en [CATALOGO_API.md](CATALOGO_API.md) y prueba con
  `node scripts/test-bazar-catalogo.js`.

## Decisiones y alternativas

| Decisión | Por qué | Alternativa |
| --- | --- | --- |
| DataStore + JSON | Build simple, sin KSP/Room | Room + KSP |
| DI manual | Menos ceremonia en v0.1 | Hilt |
| Mini-apps HTML locales | Iteración sin republicar | React Native / Flutter embebido |
| RAG léxico (BM25) | Sin modelos ni descargas; funciona offline | Embeddings on-device |
| Un módulo Gradle | Velocidad de arranque | Multi-módulo por feature |
| Iconos Material | Cero assets binarios | Íconos propios / SVG |

## De demo a producción

1. **Backend**: cuentas, ledger de doble entrada, idempotencia por `Idempotency-Key`, webhooks.
2. **Auth**: OAuth 2.0 + PKCE, refresh tokens en `EncryptedSharedPreferences` o Keystore.
3. **Pagos**: tokenización, 3-D Secure, cumplimiento PCI-DSS, límites y antifraude.
4. **Mini-apps de terceros**: firma del bundle, verificación de manifiesto, permisos por scope,
   actualización remota y *feature flags*.
5. **Observabilidad**: Crashlytics, trazas de pagos, métricas por servicio.
6. **Modularización**: `core:design`, `core:data`, `core:miniapp`, `feature:*`.

## Testing

- `WalletLedgerTest`: invariantes del saldo (nunca negativo, historial acotado).
- `MoneyTest`: formato MXN sin depender del locale del dispositivo.
- `ServiceCatalogTest`: ids únicos y que cada mini-app tenga su bundle en assets.
- `FormattersTest`: etiquetas relativas deterministas.
- `BazarCatalogoTest` (node): `scripts/test-bazar-catalogo.js` valida el delta, las versiones
  históricas, la actividad y la foto real del servidor.
- `InboxRepositoryTest`: los avisos del bazar entran sin leer, con id propio.