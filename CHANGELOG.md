# Changelog

Todas las versiones notables de Nahua. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [SemVer](https://semver.org/lang/es/).

## [0.8.2] — 2026-11-17

### Cambiado

- **Correlación rediseñada**: la tarea ahora exige una **tabla por eje y por obra** (no un resumen de
  cada libro), una cita por celda y la etiqueta explícita “no se pronuncia” cuando una obra no dice
  nada del eje. Después: coincidencias, tensiones con las dos citas, intersección y huecos.
- El contexto de correlación sube a **16 000 caracteres** y usa 2 fragmentos por obra; la primera
  vuelta toma **una sola ventana por libro**, así ninguna obra queda fuera por presupuesto.
- Las citas usan el nombre de la obra **sin extensión**.

### Añadido

- **Huella SHA-256 al importar**: si el mismo archivo ya está indexado, se omite y se avisa (“ya
  estaba indexado”). Cada documento guarda su hash y su versión por nombre.
- 6 tests nuevos (comparación, cobertura por obra, etiquetas y huella); **117** en total.

## [0.8.1] — 2026-11-17

### Añadido

- **Corpus de evaluación real (NA-003)**: cinco obras (Cabnal 2010, Guzmán y Triana sobre Paredes,
  Segato, *Feminismo descolonial* y Federici) en `docs/eval/fixtures` —535 páginas, 6.1 MB— con
  `cases.json` de 7 casos y sus páginas esperadas.
- **Contexto por ventanas (NA-004)**: se conserva el fragmento completo y se eligen hasta dos
  ventanas de oraciones con mayor densidad de términos, con peso IDF; se añade el vecino cuando la
  ventana termina en el borde y se deduplican solapes por n-gramas.
- **Contrato de evidencia (NA-005)**: `RetrievedEvidence` y `RetrievalResult` con `chunkId` estable,
  score, página y estrategia; el chat muestra **EVIDENCIA DÉBIL** cuando no hubo coincidencias.
- 12 tests nuevos de ventanas, deduplicación, vecinos y evidencia, más 4 con prosa real
  (Cabnal, Segato y Federici); **107 tests** en total.

- **Caché por tarea (NA-006)**: en las tareas de estudio el historial ya no forma parte de la clave,
  y la clave incluye versión de índice y alcance de documentos; repetir “resumen” acierta aunque
  después hayan pasado otras consultas.
- **Regenerar**: botón en la última respuesta que repite la petición ignorando la caché.
- **Detener**: botón en la tarjeta de progreso que cancela el `Job` y corta la conexión con el
  proveedor (`ChatClient.cancelActive()`).

### Cambiado

- `RagChunk` lleva `id` estable (sha-256 corto del texto). Los índices anteriores siguen leyéndose:
  el id se calcula al vuelo si falta.
- 4 tests nuevos de la clave de caché; **111 tests** en total.

## [0.8.0] — 2026-11-16

### Añadido

- **Bazar/Trueque comunitario**: el tianguis de productores se convierte en un bazar donde cualquiera
  de la comunidad publica lo que ya no usa. Cada publicación se **valora** (precio de referencia,
  estado, meses de uso, accesorios y factura), muestra el rango estimado, el mínimo recomendado para
  trueque y el desglose del cálculo.
- **Trueque con aviso real**: el botón **Truquear** manda la propuesta al dueño y deja un aviso nativo
  en **Avisos** (`Nahua.announce`). Desde el trato se abre el **perfil del interesado** con los objetos
  que tiene disponibles para truequear.
- **Cierre de trato**: aceptar, pedir la diferencia en efectivo, rechazar o cancelar; al aceptar los dos
  objetos quedan apartados, se propone punto de encuentro y el trueque se marca como completado.
- **Valoración en Kotlin** (`BarterValuator`): categoría, estado, uso y accesorios, con piso de
  deprecación, tope de reventa y comparación entre dos objetos (*A tu favor / Parejo / Pide
  compensación*, con compensación sugerida redondeada a $50). Se expone a las mini-apps con
  `Nahua.valuate` y `Nahua.compare`.
- **SDK 1.2**: `Nahua.valuate`, `Nahua.compare` y `Nahua.announce`; nueva mini-app `bazar` con
  permisos `storage.local` e `inbox.notify`.
- **Avisos del bazar**: nuevo tipo de aviso *Bazar* en la bandeja nativa, con su propio glifo y color.
- 17 tests de valoración y trueque, y 3 del buzón de avisos.

### Cambiado

- El servicio **Tianguis** pasa a **Bazar/Trueque** (id `bazar`) en el catálogo, el inicio, el buscador
  y el manifiesto de mini-apps; la categoría *Tianguis* ahora se llama *Bazar*.
- La entrada del bazar en el inicio dice “Publica, vende o truequea” y usa el acento bugambilia.

### Corregido

- El aviso de trueque ya no se pierde: entra a la bandeja de Avisos en lugar de quedarse en la página.
- La navegación del bazar conserva el historial, así que “Ver perfil” regresa al trato y no al inicio.

## [0.7.0] — 2026-11-16

### Añadido

- **GPS en vivo en el mapa**: `LocationProvider.locationUpdates()` emite un flujo continuo con
  latitud, longitud, precisión, velocidad, rumbo y altitud usando `LocationManager` (sin Google
  Play Services). El marcador tiene pulso, círculo de precisión y cono de rumbo, y el mapa puede
  seguirte o dejarte mover libremente.
- **Panel de coordenadas**: la barra superior muestra lat/lng y precisión en tiempo real; la
  pestaña **GPS** añade velocidad, altitud, rumbo, antigüedad de la lectura, fuente y un botón para
  copiar las coordenadas al portapapeles.
- **Ir a un destino**: búsqueda de tianguis, plazas, hospitales, municipios o coordenadas
  (`19.2433, -103.7250`), ruta directa en el mapa con distancia, rumbo y ETA a pie y en auto, y
  **Navegar** para abrir la app de mapas del sistema con el destino puesto.
- **Zoom con detalle real z14–16**: nueva pirámide de teselas del área metropolitana con calles,
  edificios, parques, arroyos, ferrocarril y etiquetas de calles y colonias, renderizada a 2× y
  reducida con LANCZOS. Las capas de detalle se activan a z14 y z16 en lugar de estirar z13.
- **SDK 1.1**: `Nahua.watchLocation`, `Nahua.onLocationError`, `Nahua.stopLocation`,
  `Nahua.openDirections` y `Nahua.copy`; los fixes incluyen velocidad, rumbo, altitud y timestamp.
- Test de cobertura z16 y de tamaño real de las nuevas teselas.

### Cambiado

- El paquete offline pasa de 1,380 a **2,489 teselas (~8.1 MB)** y de z9–13 a **z9–16**; se genera con
  `.tmp/render_v3.py` (Natural Earth + calles, edificios y áreas de OSM).
- La mini app del mapa se reescribió con hoja de pestañas (Lugares / Ruta / GPS), búsqueda con
  sugerencias, controles de zoom, modo seguir, barra de estado GPS y tarjeta de ruta.
- El WebView usa caché (`LOAD_DEFAULT`), prerasterizado fuera de pantalla, zoom nativo desactivado
  y texto al 100 % para acelerar el rezoomy y evitar que el zoom del sistema compita con Leaflet.
- El inicio ya no inventa una posición en Colima Centro: `currentFix()` devuelve `null` cuando no
  hay permiso o lectura reciente y el mapa lo explica.
- El seguimiento se pausa al ocultar la app y se reanuda al volver, salvo que el usuario lo haya
  detenido o haya negado el permiso.

### Corregido

- Las teselas z14–15 se veían borrosas porque se escalaban desde z13; ahora hay detalle urbano
  nativo y la capa de barrio llega a z16.
- El mapa afirmaba “Estás aquí” con una posición de relleno aunque el GPS no tuviera señal.
- El primer fix cancelaba el `flyTo` inicial y el mapa se quedaba en z13.
- La hoja inferior y las sugerencias desaparecían en Android porque las unidades `vh` del WebView
  se resolvían a 0; ahora el alto se calcula en píxeles con `window.innerHeight`.

## [0.6.0] — 2026-11-16

### Añadido

- **EPUB sin dependencias**: `EpubParser` lee el ZIP, el OPF y el spine, convierte cada capítulo en
  una “página” y reutiliza el mismo índice y las mismas citas que los PDFs.
- **Limpieza de texto** (`TextCleaner`): elimina encabezados y pies repetidos, números de página
  sueltos y guiones de corte, y conserva los cortes de párrafo.
- **Fragmentación por palabras**: bloques de 450 palabras con 50 de solapamiento (antes ~150
  palabras por caracteres), con el conteo de palabras por bloque.
- **Búsqueda balanceada por libro**: con varios documentos se piden los 3 mejores fragmentos de
  cada uno y se intercalan, para que ninguna obra acapare el contexto.
- **Streaming**: la respuesta se pide con `"stream": true` y se muestra mientras llega, con
  detección automática si el proveedor ignora el streaming.
- **Progreso de indexación**: libro N de M, páginas procesadas y barra pixel en la mesa.
- 11 tests nuevos (limpieza, chunking por palabras, EPUB y búsqueda balanceada); 72 en total.

### Cambiado

- **Inicio de barro**: el mapa offline real ocupa el 33 % de la pantalla (como en la 0.5.1) y
  desaparecen el buscador y el tip; se mantienen el saludo y el carrusel de aplicaciones.
- **Paleta regional de Colima**: café de olla, teja, ocre y cal sustituyen al morado neón en toda
  la app, con un fondo de rombos de talavera dibujado en `PixelBackdrop`.
- **Mapa offline real**: se regeneró el paquete de teselas (Natural Earth + calles OSM del área
  metropolitana) porque el anterior eran 2 151 copias de la imagen «403 Access blocked» de
  OpenStreetMap. Las teselas ahora son un mapa de papel cálido con carreteras, pueblos y el
  Volcán de Colima, y la mini app ya no pide teselas a los servidores públicos de OSM.
- **Toda la app en pixel art**: Servicios, Cartera, Perfil, Avisos, Buscador, Escáner QR, la hoja
  de actualizaciones y el marco de las mini-apps usan la misma dirección visual que el inicio.
  Las piezas compartidas (cabecera con regreso, filas, interruptores, estado vacío y aviso
  flotante) viven en `core/design/PixelScreen.kt` y los glifos 9×9 de cada servicio en
  `PixelAppGlyphs.kt`.
- La barra de estado usa iconos claros en toda la app y el fondo del `Scaffold` es `PixelBgDeep`.
- Las mini-apps conservan su HTML: sólo cambia el marco (cabecera, barra de saldo y confirmación
  de pago, que pasa de `ModalBottomSheet` a diálogo pixel).
- Se retiró el selector de tema del Perfil: la interfaz pixel es oscura y el conmutador ya no
  cambiaba nada visible. La preferencia sigue guardada para el futuro.

### Pendiente (documentado en docs/RAG.md)

- Embeddings locales (ONNX/MediaPipe) y base vectorial con HNSW: requieren dependencias y un modelo
  que este build offline no puede descargar. La búsqueda léxica por raíz queda como respaldo.
- `WorkManager` para indexar en segundo plano: hoy la importación vive en el ViewModel y se cancela
  si se cierra la app.

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