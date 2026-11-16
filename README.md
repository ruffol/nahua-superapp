# Nahua · Super App de Colima

[![Android CI](https://github.com/ruffol/nahua-superapp/actions/workflows/android.yml/badge.svg)](https://github.com/ruffol/nahua-superapp/actions/workflows/android.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Una **super app** hecha para **Colima**: pagos, transporte, tianguis de productores locales,
mapa **offline** con GPS, mascota virtual y **Nahuanotoka**, un estudio que convierte tus PDFs
en mesas de trabajo por materia: resúmenes con página, ideas propias, correlación entre documentos,
figuras y un PDF listo para exponer. Todo en un solo binario, con un **runtime de mini-apps** y
**actualizaciones OTA** desde GitHub Releases.

> **Estado:** v0.4.3 — demo funcional, no lista para producción (sin backend, KYC ni PCI).

---

## Lo que incluye

- **Mapa offline de Colima** con teselas empaquetadas (z9–13 estatal, z14–15 metropolitano) y
  **GPS** para centrarte; teselas en línea de OpenStreetMap como respaldo.
- **Tianguis de Colima**: productores de Colima, Villa de Álvarez, Comala, Tecomán, Manzanillo y
  Armería, con canasta y pago desde la cartera.
- **Nahuanotoka (mesas de estudio)**: crea una mesa por materia, agrega varios PDFs, pide resumen,
  ideas, correlación o guion de exposición, revisa los tokens gastados y exporta el análisis a PDF.
- **Actualizaciones OTA**: desde *Perfil → Versión* la app busca la última release en GitHub, la
  descarga y la instala sin volver a pasar un APK.
- **Hub de servicios**, cartera con DataStore, pagos entre mini-apps, escáner QR, avisos, perfil y
  tema claro/oscuro.
- **54 tests unitarios** y CI en GitHub Actions.

### Mini-apps y features

| App | Tipo | Qué hace | Integración |
| --- | --- | --- | --- |
| **Mapas** | mini-app | mapa offline de Colima, tianguis por municipio y “mi ubicación” | Leaflet + GPS |
| **Tianguis** | mini-app | productores colimenses de seis municipios | `Nahua.pay` |
| **Nahua Ride** | mini-app | destino, tarifa, ETA y viaje | `Nahua.pay` |
| **Pagar servicios** | mini-app | luz, agua e internet | `Nahua.pay` |
| **Axolote** | mini-app | mascota virtual con hambre, ánimo y niveles | `localStorage` |
| **Nahuanotoka** | feature nativa | mesas de estudio: resumen, ideas, correlación y PDF | HTTP + PDFBox |

---

## Mapa offline de Colima

- Teselas `{z}/{x}/{y}.png` empaquetadas en `assets/miniapps/mapa/tiles` (2,151 teselas, ~14 MB).
- Cobertura: estado de Colima en z9–13; zona metropolitana Colima–Villa de Álvarez–Comala en z14–15.
- Si falta una tesela (o alejas el mapa fuera de la cobertura), Leaflet usa OpenStreetMap en línea.
- Botón **📍 Mi ubicación**: pide permiso de ubicación la primera vez y dibuja tu posición con su
  precisión.
- Atribución a OpenStreetMap en el mapa y en el pie.

## Nahuanotoka (mesas de trabajo)

**El problema:** tienes doce PDFs y una exposición en tres días. **La solución:** una mesa por
materia que guarda tus documentos, tu contexto, tus consultas y lo que llevas gastado.

1. **Servicios → Nahuanotoka → Nueva mesa** (nombre, materia y contexto: público, duración, tono).
2. **Agregar PDF** (texto seleccionable, hasta 300 páginas, varios a la vez). Se indexan en el
   teléfono y sus figuras quedan disponibles como galería.
3. **Herramientas**: *Resumen*, *Ideas nuevas*, *Correlación*, *Guion de exposición*, *Glosario* y
   *Preguntas de repaso*. Cada una manda el contexto completo de la mesa, no palabras clave.
4. **Pregunta libre** en el compositor. Las respuestas citan `[Documento, p. N]`.
5. **PDF** exporta el análisis (portada, encabezados, tablas, fuentes y paginación) y lo comparte.
6. **Tokens**: entrada, salida y total por mesa y por respuesta.

Por defecto usa **DeepSeek V4.1 Flash** (`deepseek-v4.1-flash`) vía OpenCode Go: es el modelo más
barato para estudiar con muchos PDFs y su contexto alcanza para varias lecturas a la vez. Puedes
cambiar base URL y modelo a cualquier API compatible con OpenAI.

**Requiere API:** el texto se indexa en el teléfono, pero la redacción (resumen, ideas, correlación)
la hace siempre el modelo configurado. Si la API falla o falta la llave, la app muestra el error y
abre la configuración en lugar de inventar una respuesta.

Los documentos se quedan en el teléfono; sólo viajan la pregunta y los fragmentos elegidos.
Detalles de arquitectura, algoritmo y límites en [docs/RAG.md](docs/RAG.md).

## Actualizaciones OTA

1. **Perfil → Versión** abre el panel de actualizaciones (o *Actualizar app*).
2. La app consulta `releases/latest` del repo, compara versiones y muestra las notas.
3. **Descargar e instalar** baja el APK a caché, verifica el SHA-256 cuando GitHub lo publica y
   abre el instalador del sistema (la primera vez hay que permitir “instalar apps desconocidas”
   para Nahua).

Publicar una versión nueva:

```bash
git tag v0.3.1
git push origin v0.3.1
```

El workflow `release.yml` compila el APK con `versionName=0.3.1` y `versionCode=301`, lo firma y lo
adjunta a la release. En el teléfono basta con tocar *Buscar actualizaciones*.

> **Firma:** el repo incluye `signing/nahua-demo.keystore` a propósito para que las compilaciones de
> CI y las locales compartan firma y puedan actualizarse entre sí. Es una llave de demo, pública:
> reemplázala antes de distribuir fuera de este repositorio.

---

## Arquitectura

```
app/
└─ com.nahuanotoca.superapp
   ├─ core/
   │  ├─ data/            cartera, sesión, avisos, preferencias
   │  ├─ data/catalog/    catálogo de servicios
   │  ├─ design/          tema y componentes
   │  ├─ location/        LocationManager + permisos (GPS de las mini-apps)
   │  ├─ miniapp/         manifiestos, registro y puente JS (pago, ubicación)
   │  ├─ model/           modelos + WalletLedger (aritmética pura)
   │  ├─ navigation/      rutas
   │  ├─ rag/             PDF (texto y figuras), índice, contexto, LLM, PDF de salida
   │  ├─ update/          GitHub Releases + PackageInstaller
   │  └─ util/            formateo de fechas
   ├─ feature/            home, services, wallet, scan, inbox, profile, miniapp, search, rag, update
   │  └─ rag/            mesas de estudio (lista, detalle, herramientas, exportar PDF)
   ├─ ui/                 shell de navegación y fábrica de ViewModels
   ├─ NahuaApplication.kt
   └─ MainActivity.kt
```

```mermaid
flowchart LR
    UI[Compose UI] --> VM[ViewModels]
    VM --> REPO[Repositories]
    REPO --> DS[(DataStore)]
    REPO --> FILES[(filesDir/rag)]
    UI --> NAV[Navigation]
    NAV --> WEB[MiniApp WebView]
    WEB -- Nahua.pay / Nahua.location --> BRIDGE[MiniAppBridge]
    BRIDGE -- confirmación / GPS --> UI
    VM --> RAG[contexto: búsqueda + cobertura]
    RAG --> LLM[API compatible OpenAI]
    VM --> PDF[ReportWriter: PDF]
    PDF --> SHARE[FileProvider]
    UI --> UPD[GitHub Releases]
    UPD --> INST[PackageInstaller]
```

Decisiones: DI manual (`AppContainer`), DataStore y JSON en `filesDir` en lugar de Room, dinero en
centavos, mini-apps HTML, recuperación léxica por raíz + cobertura por documento (sin embeddings) y
mapa raster offline para no depender de Google Play Services.

Más detalle: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/MINIAPP_SDK.md](docs/MINIAPP_SDK.md)
y [docs/RAG.md](docs/RAG.md).

---

## Cómo compilar

Requisitos: JDK 17 y Android SDK con la plataforma **35**.

```bash
echo "sdk.dir=/ruta/a/Android/Sdk" > local.properties
./gradlew assembleDebug        # APK de debug
./gradlew testDebugUnitTest    # 54 tests
./gradlew installDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

---

## Privacidad

- Los PDFs se procesan en el dispositivo; solo se envían pregunta y fragmentos a tu API.
- La ubicación se usa únicamente cuando tocas “Mi ubicación” dentro del mapa.
- La API key se guarda en el almacenamiento privado de la app (Keystore recomendado en producción).

---

## Hoja de ruta

- [ ] Backend real (cuentas, ledger de doble entrada, webhooks) y OAuth/OIDC.
- [ ] Embeddings on-device para búsqueda semántica en Nahuanotoka.
- [ ] OCR on-device para los PDFs escaneados de las mesas.
- [ ] Firma de mini-apps y permisos granulares.
- [ ] CameraX + ML Kit para QR real.
- [ ] Módulos Gradle y Hilt.

## Contribuir

Lee [CONTRIBUTING.md](CONTRIBUTING.md).

## Licencia

MIT © 2026 Nahua contributors. Ver [LICENSE](LICENSE).