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

`LocationProvider` usa `LocationManager` (sin Google Play Services). Prefiere un fix reciente en
caché y, si no hay, espera GPS (7 s) y red (5 s) con timeout. El puente expone `Nahua.location()`;
el permiso se pide desde la pantalla anfitriona con `RequestPermission`, de modo que la mini-app
nunca ve el diálogo del sistema.

## Actualizaciones OTA

`UpdateRepository` consulta `releases/latest` de GitHub, compara versiones con `Version` y descarga
el APK a caché verificando el SHA-256 cuando la release lo publica (`digest`). `ApkInstaller` usa
`PackageInstaller` con un `PendingIntent` mutable y un `BroadcastReceiver` que lanza la confirmación
del sistema. El repositorio incluye una llave de firma de demo para que CI y las builds locales
compartan firma; en producción debe sustituirse.

## Runtime de mini-apps

```
assets/miniapps/index.json  ──►  MiniAppRegistry  ──►  MiniAppManifest
                                                          │
MiniAppScreen ──► MiniAppWebView ──► WebViewAssetLoader ──┘
       │                   │
       │                   └── window.NahuaNative (addJavascriptInterface)
       └── MiniAppBridge ──► WalletRepository.pay()
```

- **Origen seguro**: los assets se sirven en `https://appassets.androidplatform.net/assets/...`,
  así el WebView no necesita `file://` ni tráfico en claro.
- **Puente mínimo**: `getSession`, `pay`, `toast`, `close`, `log`.
- **Confirmación obligatoria**: ningún pago se ejecuta desde JS; siempre pasa por una hoja de
  confirmación en Compose y por `WalletRepository`.
- **Resultado asíncrono**: el puente responde con `window.Nahua._resolve(id, payload)`, que resuelve
  o rechaza el `Promise` del SDK.

Más detalle en [MINIAPP_SDK.md](MINIAPP_SDK.md).

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