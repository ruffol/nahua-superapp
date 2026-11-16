# Changelog

Todas las versiones notables de Nahua. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [SemVer](https://semver.org/lang/es/).

## [0.5.1] — 2026-11-16

### Cambiado

- **Inicio rediseñado en pixel art**: mapa de Colima dibujado en `Canvas`, saludo con el
  Tlalchichi, carrusel de aplicaciones, tarjetas de resumen y tip del día. Los paneles con
  esquinas escalonadas y los glifos 9×9 viven en `core/design/PixelHome.kt`, la ilustración del
  mapa en `feature/home/HomePixelArt.kt` y los sprites en `res/drawable-nodpi`.
- **Barra inferior pixel art** (`ui/PixelBottomBar.kt`): Inicio, Aplicaciones, Actividad y Perfil,
  con el Tlalchichi como botón central de la mascota.
- Los iconos de la barra de estado se aclaran en el inicio (fondo oscuro) y vuelven a oscuro en
  las pantallas claras.

### Eliminado

- `RotaryAppDial` y `HomeOfflineMapCard`, sustituidos por el nuevo encabezado. El mapa offline
  sigue disponible desde el botón de expandir del mapa y desde el catálogo de servicios.

## [0.5.0] — 2026-11-16

### Añadido

- **Nueva dirección visual pixel art** en el estudio: tipografía *Press Start 2P* y *VT323*,
  paneles con esquinas escalonadas, bordes gruesos, sprites de axolote y del estudiante, fondo de
  puntos y botones con sombra dura (`core/design/Pixel.kt`, `PixelArt.kt`).
- **Caché de respuestas**: misma consulta, mismos documentos e historial → respuesta local sin
  llamar a la API. La mesa muestra aciertos y tokens ahorrados; el panel permite vaciar la caché.
- **Copiar y descargar cada respuesta** en PDF, Markdown y TXT, con `StudyExporter` y
  `FileProvider`.
- **Lector a pantalla completa** para respuestas largas: el chat se queda inmóvil y el texto se
  desplaza dentro de un recuadro.
- **Dictado local** en el compositor con `SpeechRecognizer` y `EXTRA_PREFER_OFFLINE`.
- 7 tests nuevos de la clave de caché; 61 en total.

### Cambiado

- El chat muestra vistas previas recortadas (~430 caracteres) con el botón **VER TODO**.
- El estudio usa fondos, tarjetas y diálogos propios en lugar de los Material de la app.

## [0.4.3] — 2026-11-16

### Añadido

- `ChatClient` reintenta una vez, sin `temperature` ni `max_tokens`, cuando el proveedor responde
  400 por parámetros no reconocidos: compatibilidad con gateways estrictos sin tocar la
  configuración del usuario.
- Cabeceras de sesión (`X-OpenCode-Session`, `X-OpenCode-Session-Id`, `X-Session-ID`) para los
  proxies que enrutan por sesión.
- Los errores 400 muestran ahora el mensaje del proveedor en vez de un texto genérico.

### Corregido

- Las cabeceras de sesión llevan un UUID por proceso. Antes mandaban los primeros 32 caracteres de
  la API key, filtrando material de la llave a cabeceras intermedias y logs.
- La apertura de la petición (timeouts y cabeceras) queda en un solo lugar, sin duplicar el bloque
  en el reintento.

## [0.4.2] — 2026-11-16

### Cambiado

- **Nahuanotoka analiza únicamente mediante API.** Se elimina el motor local
  (`LocalStudyEngine`) y su respaldo silencioso: si no hay API key configurada, la app abre los
  ajustes y explica que el análisis lo genera el modelo; si el proveedor falla, se muestra el error
  real (401/403 abren la configuración) en lugar de una respuesta generada en el teléfono.
- La mesa muestra el aviso de conexión también dentro del detalle, junto a los documentos.
- `ChatClient` avisa con un mensaje claro cuando el endpoint devuelve HTML en vez de JSON (base URL
  equivocada) en lugar de reventar con un error de parseo.
- Tests: se retira `LocalStudyEngineTest`; quedan 54.

## [0.4.1] — 2026-11-16

### Eliminado

- **Nahua Food**, **Cine**, **Farmacia** y **Seguros** salen del catálogo de servicios, de la fila de
  destacados y de la búsqueda.
- Se borran los paquetes `miniapps/food` y `miniapps/cinema` y sus entradas en
  `assets/miniapps/index.json`; también desaparece la categoría **Comida** del hub.
- Tests: nuevos casos que verifican que los cuatro servicios ya no existen y que las mini-apps
  restantes siguen declaradas; 58 tests en total.

## [0.4.0] — 2026-11-15

### Corregido

- **“No encontré pasajes relevantes” con PDFs que sí tienen texto.** El recuperador sólo comparaba
  palabras exactas: “Dame un resumen” buscaba literalmente `dame` y `resumen`, que no aparecen en
  un documento, y devolvía cero resultados. Ahora:
  - coincidencia por raíz y plurales (“obligacion” ↔ “obligaciones”),
  - las tareas globales usan **cobertura por documento** en lugar de búsqueda por palabras,
  - si no hay coincidencias puntuales se manda lo más representativo y se marca la consulta como
    débil en vez de fallar.
- La lista de mesas ya no pierde el contador de documentos al reabrir la app.

### Añadido

- **Mesas de trabajo**: una por materia, con nombre, materia, contexto propio, documentos,
  conversación y contador de tokens. Se crean, editan y eliminan desde la app.
- **Herramientas de estudio**: Resumen, Ideas nuevas, Correlación entre documentos, Guion de
  exposición, Glosario y Preguntas de repaso, cada una con su formato y su estrategia de contexto.
- **Exportar a PDF**: `ReportWriter` dibuja un A4 paginado (portada, encabezados, viñetas, tablas,
  fuentes y pie de página) y lo comparte con `FileProvider`.
- **Consumo de tokens** por mesa (entrada, salida, total) y por respuesta, leyendo `usage` del
  proveedor con estimación de respaldo.
- **Figuras de los PDFs**: extracción de imágenes incrustadas (≥160 px) con galería y vista ampliada.
- **DeepSeek V4.1 Flash** como modelo por defecto y presets editables (`deepseek-flash`,
  `deepseek-v4-pro`, `kimi-k2.7-code`, `glm-5.1`, `mimo-v2.5-pro`).
- **Selección múltiple** de PDFs al importar, con resumen de páginas, fragmentos y figuras.
- **Migración**: los PDFs del formato anterior pasan solos a la mesa “Mis documentos”.
- 12 tests nuevos (contexto, cobertura y el caso del PDF de 8 páginas); 52 en total.

### Cambiado

- La ruta `rag` pasa a ser `studio` y el servicio se llama “Mesas de estudio con tus PDFs”.
- `ChatClient` acepta historial, `max_tokens` y devuelve modelo y `usage`.
- El PDF ya no se reindexa por consulta: el contexto se arma por fragmentos con presupuesto de
  caracteres.

## [0.3.0] — 2026-11-15

### Añadido

- **Mapa offline de Colima**: 2,151 teselas empaquetadas (z9–13 estatal, z14–15 metropolitano) con
  respaldo en línea de OpenStreetMap y atribución.
- **GPS**: proveedor nativo basado en `LocationManager`, puente `Nahua.location()` y botón
  "Mi ubicación" en el mapa con permiso en tiempo de ejecución.
- **Tianguis de Colima**: catálogo de productores de Colima, Villa de Álvarez, Comala, Tecomán,
  Manzanillo y Armería; el mapa muestra sus mercados y horarios.
- **Actualizaciones OTA**: consulta de GitHub Releases, descarga con progreso, verificación SHA-256
  e instalación con `PackageInstaller`. Sección *Perfil → Versión*.
- Workflow `release.yml`: al empujar un tag `v*` compila y publica el APK en la release.
- Llave de firma de demo compartida entre CI y builds locales para que las actualizaciones instalen.
- 7 tests nuevos (versiones y teselas offline); 40 en total.

### Cambiado

- La app pasa a estar enfocada en Colima (mapa, tianguis y textos).
- "Nahua IA" se renombra a **Nahuanotoka**.
- Versión 0.3.0 (versionCode 300).

## [0.2.0] — 2026-11-15

### Añadido

- **Mapas** (mini-app): Leaflet + OpenStreetMap con tianguis de CDMX, Oaxaca, Puebla, Mérida y
  Guadalajara, búsqueda por ciudad y popups con especialidad de cada mercado.
- **Axolote** (mini-app): mascota virtual con comida, ánimo, energía y limpieza; decaimiento por
  tiempo, XP, cinco etapas y persistencia en `localStorage`.
- **Tianguis** (mini-app): catálogo de productores de cuatro regiones, canasta y pago con cartera.
- **Nahua IA** (feature nativa): RAG local sobre PDFs con PDFBox-Android, chunking con traslape,
  recuperación léxica BM25 y cliente compatible con OpenAI (OpenCode Go por defecto).
- Nueva categoría **Tianguis** en el hub y cuatro servicios nuevos en el catálogo.
- `docs/RAG.md` con el pipeline, la configuración de la API y los límites.
- Tests de chunking, retrieval y configuración del endpoint (14 nuevos, 33 en total).

### Cambiado

- README reescrito para la nueva oferta de servicios.
- Versión de la app a 0.2.0 (versionCode 2).

## [0.1.0] — 2026-11-15

### Añadido

- Shell de super app en Kotlin + Jetpack Compose (Material 3).
- Hub de servicios con catálogo, categorías y búsqueda.
- Cartera con DataStore, recargas y movimientos.
- Runtime de mini-apps (WebView aislado + puente de pagos).
- Mini-apps Ride, Food, Pagar servicios y Cine.
- Escáner QR, avisos, perfil, tema claro/oscuro.
- CI en GitHub Actions y 19 tests unitarios.