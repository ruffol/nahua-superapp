# Nahua · Super App de Colima

[![Android CI](https://github.com/ruffol/nahua-superapp/actions/workflows/android.yml/badge.svg)](https://github.com/ruffol/nahua-superapp/actions/workflows/android.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Una **super app** hecha para **Colima**: pagos, transporte, un **bazar/trueque comunitario**,
mapa **offline** con GPS, mascota virtual y **Nahuanotoka**, un estudio *pixel art* que convierte tus PDFs
en mesas de trabajo por materia: resúmenes con página, ideas propias, correlación entre documentos,
figuras y un PDF listo para exponer. Todo en un solo binario, con un **runtime de mini-apps** y
**actualizaciones OTA** desde GitHub Releases.

> **Estado:** v0.16.2 — demo funcional, no lista para producción (sin backend, KYC ni PCI).

---

## Lo que incluye

- **Mapa estatal de Colima** con teselas propias (z9–16, Natural Earth + OSM + cartografía
  geoestadística del INEGI), manzanas y ejes viales de las localidades, estilo de papel cálido,
  **GPS en vivo** con coordenadas, velocidad y rumbo, ruta directa y búsqueda estatal.
- **Bazar/Trueque comunitario**: cualquiera publica lo que ya no usa, la app lo **valora** (precio
  nuevo, estado, uso y accesorios) y el dueño decide si lo vende o lo abre a **trueque**. El botón
  **Truquear** avisa al dueño en su bandeja de Avisos; él ve el perfil del interesado con los objetos
  que ofrece y cierran el trato, con compensación en efectivo cuando los valores no emparejan.
- **Nahuanotoka (mesas de estudio)**: crea una mesa por materia, agrega varios PDFs, pide resumen,
  ideas, correlación o guion de exposición, revisa los tokens gastados y exporta el análisis a PDF.
- **Importación en segundo plano** con WorkManager: cierra la app y la indexación sigue.
- **Biblioteca en Room** con migración automática del índice anterior.
- **OCR local** (ML Kit) para PDFs escaneados, en segundo plano.
- **Búsqueda híbrida opcional** (BM25 + vectores con RRF) si configuras un endpoint `/embeddings`.
- **Actualizaciones OTA**: desde *Perfil → Versión* la app busca la última release en GitHub, la
  descarga y la instala sin volver a pasar un APK.
- **Hub de servicios**, cartera con DataStore, pagos entre mini-apps, escáner QR, avisos, perfil y
  tema claro/oscuro.
- **Emulador de Game Boy Advance**: mini-app inmersiva horizontal con EmulatorJS 4.2.3 (núcleo
  mGBA en WebAssembly), 100 % offline, controles táctiles y partidas que sobreviven al cierre.
- **156 tests unitarios** y CI en GitHub Actions.

### Mini-apps y features

| App | Tipo | Qué hace | Integración |
| --- | --- | --- | --- |
| **Mapas** | mini-app | mapa estatal z9–16 con manzanas, GPS, rutas y búsqueda | Leaflet + GPS + INEGI |
| **Bazar/Trueque** | mini-app | publicar, valorar, vender y truequear entre vecinos | `Nahua.pay` · `Nahua.valuate` · `Nahua.announce` |
| **Nahua Ride** | mini-app | destino, tarifa, ETA y viaje | `Nahua.pay` |
| **Pagar servicios** | mini-app | luz, agua e internet | `Nahua.pay` |
| **Axolote** | mini-app | mascota virtual con hambre, ánimo y niveles | `localStorage` |
| **Emulador GBA** | mini-app | tus cartuchos de Game Boy Advance, horizontal y sin red | EmulatorJS 4.2.3 (mGBA) · `Nahua.pickRom` |
| **Nahuanotoka** | feature nativa | mesas de estudio: resumen, ideas, correlación y PDF | HTTP + PDFBox |

---

## Mapa estatal de Colima

- **Paquete offline**: 3,686 teselas (~10 MB) en `assets/miniapps/mapa/tiles`. La base z9–13 es PNG
  (Natural Earth + OSM); el detalle z14–16 es **WebP** y pesa casi la mitad que el PNG equivalente.
  Se renderiza a 2× y se reduce con LANCZOS para que calles y etiquetas no salgan dentadas.
- **Detalle urbano del INEGI**: se integró la *Cartografía Geoestadística Urbana y Rural Amanzanada,
  junio 2016* de Colima: **171 localidades, 17,148 manzanas, 15,812 ejes viales y 224 servicios**
  (plazas, mercados, iglesias, escuelas, hospitales). Las manzanas y calles se dibujan sobre la base
  de OSM/Natural Earth.
- **Todo Colima está descargado**: no hay capa online ni peticiones de red. La base z9–13 cubre
  todo el estado; z14–15 cubren todas las localidades amanzanadas y z16 las zonas urbanas de mayor
  densidad (Colima–Villa, Manzanillo, Tecomán, Comala, Armería, Coquimatlán, Cuauhtémoc, Minatitlán
  y otras). En una zona rural sin z16 se ve la base z13 ampliada, nunca un hueco ni un error.
  z17 escala z16 ×2.
- **Interfaz limpia**: sólo buscador, barra de GPS y el botón **◎** para centrar el mapa en tu
  ubicación. No hay panel de lugares, ni chips de municipios, ni botones de acercar/alejar; el zoom
  se hace con pellizco.
- **Buscar en todo el estado**: además de tianguis y plazas, el buscador incluye **395 localidades y
  servicios del INEGI** (plazas, mercados, iglesias, escuelas, hospitales). Los servicios también
  aparecen como etiquetas dentro de las teselas, sin una capa flotante que estorbe.
- **GPS en vivo**: `LocationProvider` usa `LocationManager` (sin Google Play Services) y emite un
  flujo con latitud, longitud, precisión, velocidad, rumbo y altitud. El mapa dibuja tu posición con
  pulso, círculo de precisión y cono de rumbo; puedes seguirte o moverte libremente por el mapa.
  El seguimiento se pausa al ocultar la app para no gastar batería.
- **Coordenadas**: la barra superior muestra lat/lng, precisión y velocidad en tiempo real; al
  tocarla copia las coordenadas al portapapeles.
- **Ir a un lugar**: busca un tianguis, una localidad, un servicio o escribe coordenadas
  (`19.2433, -103.7250`). El mapa traza la distancia directa con rumbo y ETA a pie y en auto, y
  **Navegar** abre la app de mapas del sistema con el destino ya puesto (`geo:`).
- **Rendimiento**: una sola capa de detalle estatal con `maxNativeZoom`, `keepBuffer` y caché de
  WebView; no hay peticiones de red para el mapa. Si una tesela falta, el hueco es transparente y se
  ve la capa inferior, nunca un error 403.
- Atribución a **OpenStreetMap** y **INEGI** en el mapa y en el pie.

### Datos fuente y descargas

La cartografía del INEGI es de consulta y descarga gratuita; respeta sus términos y cita la fuente.

| Dataset | Uso en el mapa | Descarga directa |
| --- | --- | --- |
| Cartografía Geoestadística Urbana y Rural Amanzanada, junio 2016, Colima (SHP, 29.15 MB) | manzanas, ejes viales, servicios | [702825218768_s.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/CGURA_Junio2016/Colima/702825218768_s.zip) |
| Topografía 1:50 000, E13B44 Colima, serie III (SHP, 10.74 MB) | calles rurales, curvas, hidrografía | [702825003010_s.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/imagen_cartografica/1_50_000/702825003010_s.zip) |
| Topografía 1:50 000, E13B34 Comala, serie III (SHP, 12.15 MB) | norte del estado | [702825002919_s.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/imagen_cartografica/1_50_000/702825002919_s.zip) |
| Topografía 1:50 000, E13B43 Manzanillo, serie III (SHP, 13.86 MB) | costa y puerto | [702825268398_s.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/imagen_cartografica/1_50_000/702825268398_s.zip) |
| Topografía 1:50 000, E13B54 Tecomán, serie III (SHP, 5.68 MB) | sur del estado | [702825268435_s.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/imagen_cartografica/1_50_000/702825268435_s.zip) |
| Topografía digital 1:250 000, condensado estatal serie IV (SHP, 3.89 MB) | visión estatal y rural | [702825208813_s.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/conden_estatales/serie_iv/702825208813_s.zip) |
| Carreteras y vialidades urbanas, edición 1.0, Colima (SHP, 2.51 MB) | red carretera estatal | [702825291884_s.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/carr_vial/Colima/702825291884_s.zip) |
| Modelo Digital de Elevación, Colima (BIL, 2.94 MB) | relieve y futura sombra de terreno | [702825547769_b.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/imagen_cartografica/1_250_000/mde/702825547769_b.zip) |
| Uso del suelo y vegetación, serie III, Colima (SHP, 7.90 MB) | cobertura vegetal y rural | [702825229573_s.zip](https://www.inegi.org.mx/contenidos/productos/prod_serv/contenidos/espanol/bvinegi/productos/geografia/tematicas/Uso_suelo_hist/1_250_00/serie_III/702825229573_s.zip) |

Cada ficha del catálogo del INEGI (por ejemplo
[`upc=702825218768`](https://www.inegi.org.mx/app/biblioteca/ficha.html?upc=702825218768)) muestra el
mismo enlace de descarga y los metadatos. Las hojas 1:50 000 restantes (E13B24, E13B25, E13B32,
E13B33, E13B35, E13B41, E13B42, E13B45, E13B53, E13B55, E13B64) siguen el patrón
`.../geografia/imagen_cartografica/1_50_000/<UPC>_s.zip`; el UPC de cada una está en el CSV que
exportaste.

## Bazar/Trueque comunitario

El tianguis de productores pasó a ser un **bazar de la comunidad**: cualquier persona publica lo que
ya no usa, la app le pone precio y el trueque es una decisión explícita de quien publica.

<p align="center">
  <img src="docs/screens/bazar.png" width="330" alt="Bazar/Trueque: objetos de la comunidad con valor estimado" />
  <img src="docs/screens/bazar-trueque.png" width="330" alt="Bazar/Trueque: ofrecer un objeto a cambio" />
</p>

1. **Publicar**: título, ícono, categoría, estado, meses de uso y precio de referencia (lo que costaba
   nuevo). La valoración se recalcula en vivo mientras escribes.
2. **Valorar**: `BarterValuator` combina el precio de referencia con la reventa de la categoría, el
   estado, el uso, los accesorios y la factura, y devuelve un valor con rango, confianza y desglose
   (*“−42 % reventa de bicis”*, *“−36 % por 3 años de uso”*). Además calcula el mínimo que conviene
   aceptar en un trueque.
3. **Habilitar trueque**: el interruptor sólo se enciende cuando el objeto ya está valorado. Sin ese
   paso el objeto se publica como venta normal.
4. **Truquear**: desde un objeto con trueque, el interesado elige qué de su bazar ofrece. La app
   compara las dos valoraciones (*A tu favor*, *Parejo*, *Pide compensación*) y **avisa al dueño** con
   un aviso nativo en **Avisos** y una propuesta en la pestaña **Trueques**.
5. **Ver el perfil del interesado**: desde el trato se abre su perfil con los objetos que tiene
   disponibles para truequear y el valor de cada uno.
6. **Cerrar el trato**: aceptar, pedir la diferencia en efectivo, rechazar o cancelar. Al aceptar, los
   dos objetos quedan apartados, la app propone punto de encuentro y el trueque se marca como
   completado cuando se entrega.

La aritmética vive en Kotlin (`core/model/Barter.kt`) y es la misma que expone el puente
`Nahua.valuate`: la página nunca decide cuánto vale un objeto por su cuenta. El bundle
(`assets/miniapps/bazar/`) guarda publicaciones y tratos en `localStorage`; el día que exista backend,
esa capa se sustituye sin tocar la UI.

## Emulador GBA

Una mini-app inmersiva para jugar tus cartuchos de Game Boy Advance dentro de Nahua, sin red y
sin tocar una línea de NDK.

- **Motor**: [EmulatorJS](https://emulatorjs.org/) 4.2.3 con el núcleo **mGBA** compilado a
  WebAssembly, vendorizado (2.47 MB, sin tocar) en `assets/miniapps/gba/emulatorjs/` con
  `scripts/fetch-emulatorjs.ps1`. Se incluyen las variantes normal y `-legacy` (equipos sin
  WebGL2) y las localizaciones en-US/es-ES.
- **Tus ROMs, tu decisión**: la app no incluye ni enlaza ROMs ni BIOS. Tocas **Cargar ROM**, el
  selector del sistema (`OpenDocument`) te deja elegir el `.gba`, y el anfitrión lo copia a
  `filesDir/roms` validando extensión y tope de 64 MiB, con deduplicación por SHA-256 (el nombre
  final lleva el `sha8`). La página lo recibe como URL `https` del propio origen: nunca hay
  `file://` ni URIs de contenido en el WebView.
- **Offline de verdad**: cualquier subrequest a otro origen se responde con un 404 vacío en
  `shouldInterceptRequest`, así que ni siquiera el *failsafe* de EmulatorJS hacia su CDN puede
  generar tráfico.
- **Inmersivo y horizontal**: el manifiesto declara `display: "immersive"` +
  `orientation: "landscape"`; el anfitrión oculta las barras del sistema, rota a horizontal y
  deja solo un botón ✕ flotante. `configChanges` evita que la rotación recre la Activity (y el
  juego). El ciclo de vida pausa el WebView en segundo plano para que el audio no siga sonando.
- **Partidas**: EmulatorJS guarda SRAM y estados en IndexedDB del origen, keyficado por el nombre
  estable de la ROM; cierra la app, vuelve y **Continuar** te deja donde estabas. Si el proceso
  de render muere (OOM de GPU), la app sobrevive y ofrece reintentar.
- **Licencias**: EmulatorJS es **GPL-3.0**; el texto completo y el aviso viven en
  `assets/licenses/`. Es un bundle estático sin modificar, integrado por configuración
  (`EJS_*`), no código enlazado: el proyecto sigue siendo MIT y el emulador se puede quitar
  borrando una carpeta del APK.
- **Controles que se sienten nativos**: los botones del gamepad son HTML, así que la vibración la
  ejecuta el anfitrión (`Nahua.haptic` → `performHapticFeedback`, sin permiso `VIBRATE` y
  respetando el ajuste del sistema) con un tope de 40 ms por pulsación.
- **Medido, no supuesto**: cada partida publica un reporte `[perf]` en logcat (frame pacing +
  latencias de WebAudio) y `scripts/gba-perf.ps1` lo convierte en el veredicto Go/No-Go sobre
  pasar a un núcleo nativo. Criterio y procedimiento en [docs/GBA-PERF.md](docs/GBA-PERF.md).

<p align="center">
  <img src="docs/screens/inicio-aplicaciones.png" width="330" alt="Hub de servicios de Nahua" />
</p>

## Nahuanotoka (mesas de trabajo)

**El problema:** tienes doce PDFs y una exposición en tres días. **La solución:** una mesa por
materia que guarda tus documentos, tu contexto, tus consultas y lo que llevas gastado.

1. **Servicios → Nahuanotoka → Nueva mesa** (nombre, materia y contexto: público, duración, tono).
2. **Agregar PDF o EPUB** (varios a la vez). Se limpian encabezados y números de página, se parten
   en bloques de 450 palabras con 50 de traslape y las figuras de los PDFs quedan como galería.
3. **Herramientas**: *Resumen*, *Ideas nuevas*, *Correlación*, *Guion de exposición*, *Glosario* y
   *Preguntas de repaso*. Con varios libros se envían los 3 mejores fragmentos de cada uno y la
   **Correlación** usa dos pasadas (una ficha por obra y luego el cruce). También puedes escribir la orden
   (“compárame los libros”, “hazme un resumen”) y el enrutador la ejecuta.
   Las respuestas llegan **en streaming**, token por token.
4. **Pregunta libre** en el compositor. Las respuestas citan `[Documento, p. N]`.
5. **PDF** exporta el análisis (portada, encabezados, tablas, fuentes y paginación) y lo comparte.
6. **Tokens**: entrada, salida y total por mesa y por respuesta.
7. **Caché de respuestas**: repite una consulta y no gasta tokens; la mesa acumula aciertos y
   tokens ahorrados.
8. **Copiar y descargar cada respuesta** en PDF, Markdown o TXT, con un lector a pantalla completa
   para las respuestas largas.
9. **Dictado local** con el reconocimiento del teléfono (modo offline cuando hay paquete de idioma).

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
git tag v0.5.1
git push origin v0.5.1
```

El workflow `release.yml` deriva la versión de la etiqueta (`0.5.1` → `versionCode=501`, con la
fórmula *mayor×10000 + menor×100 + parche*), compila y firma el APK y lo adjunta a la release. En el
teléfono basta con tocar *Buscar actualizaciones*.

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
   │  ├─ location/        LocationManager, fix puntual y flujo GPS en vivo
   │  ├─ miniapp/         manifiestos, registro y puente JS (pago, GPS, rutas, bazar y avisos)
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

Orquestador de herramientas: [docs/AGENT.md](docs/AGENT.md). Evaluación con el corpus real: [docs/EVAL.md](docs/EVAL.md).

Informe técnico del RAG: [docs/Nahuanotoka-Informe-RAG.pdf](docs/Nahuanotoka-Informe-RAG.pdf).
Auditoría y línea base (plan Nahua AI): [docs/AUDIT.md](docs/AUDIT.md) · [docs/BASELINE.md](docs/BASELINE.md) · [corpus de evaluación](docs/eval/README.md).

Más detalle: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/MINIAPP_SDK.md](docs/MINIAPP_SDK.md)
y [docs/RAG.md](docs/RAG.md).

---

## Cómo compilar

Requisitos: JDK 17 y Android SDK con la plataforma **35**.

```bash
echo "sdk.dir=/ruta/a/Android/Sdk" > local.properties
./gradlew assembleDebug        # APK de debug
./gradlew testDebugUnitTest    # 140 tests
./gradlew installDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

---

## Privacidad

- Los PDFs se procesan en el dispositivo; solo se envían pregunta y fragmentos a tu API.
- La ubicación se usa únicamente mientras el mapa está abierto (o al tocar “Mi ubicación” en el
  inicio) y nunca se envía a un servidor. Al pulsar **Navegar**, el destino se comparte con la app
  de mapas que elijas.
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