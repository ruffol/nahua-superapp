# Changelog

Todas las versiones notables de Nahua. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto usa [SemVer](https://semver.org/lang/es/).

## [Unreleased]

## [0.22.0] — 2026-11-21

### Añadido

- **Panal de aplicaciones en dos filas de hexágonos**: seis accesos (Yōlcatl, RAG, Mapa, Bazar,
  Cartera y el emulador **GBA**) repartidos cuatro arriba y cuatro abajo, sin scroll horizontal.
  Cada hexágono es una silueta propia (`HexagonShape`) con borde, sprite y sombra desplazada.
- **Dos celdas reservadas para futuras apps**: al final del panal quedan dos hexágonos atenuados
  con una cruz, descritos como «Espacio libre para una futura app» para lectores de pantalla. El
  sitio ya está hecho, así que la próxima app sólo cambia el contenido.
- **Sprite del emulador**: `px_icon_gba.png`, una consola portátil horizontal en pixel art con la
  paleta del proyecto (carcasa violeta, pantalla azul, cruceta y botones A/B en diagonal).
- **La mirada se mide desde la pantalla**: `LookDirection` mezcla dos ejes según cómo se sostenga
  el teléfono —acostado manda el borde superior de la pantalla, en alto el eje de la cámara
  trasera— y la mezcla pesa cada uno por la inclinación real, así que el cono sigue la mirada en
  las dos posturas y en el camino entre ellas, sin el vector fijo a 45° de antes.
- **Brújula pequeña en el mapa**: dial bajo la tarjeta superior con la rosa (N/E/S/O y aguja roja)
  girando con el rumbo, el ojo al centro y la muesca dorada arriba como marca de tu mirada, más el
  rumbo escrito (`NE 38°`). Sin sensor se oculta en vez de inventar el norte.
- **El mapa del inicio es un pergamino**: el recuadro se dibuja como un rollo de papel viejo —hoja
  con fibras, manchas de edad y varilla de madera con sus dos perillas— y se **desenrolla al
  tocarlo**, hasta el tercio de pantalla de siempre. Al tocar el encabezado o la varilla se vuelve a
  enrollar. El estado abierto/cerrado sobrevive a la rotación, el rollo cerrado tapa el mapa con una
  hoja de papel en vez de recargar el WebView, y el alto se sigue avisando a Leaflet para que los
  toques no caigan donde no es.

### Cambiado

- **El inicio pierde la bienvenida y el resumen de cifras**: se retiran el saludo con el Tlalchichi
  («¡Hola…! ¿Qué te gustaría hacer hoy?») y los tres recuadros de Saldo, Avisos y Servicios. La
  portada queda en el pergamino del mapa y el panal de aplicaciones.
- **Las URL de las mini-apps llevan la versión como cache-buster** (`…?v=1.4.0`): el WebView cachea
  por URL y sin eso una actualización del APK podía seguir ejecutando el HTML anterior.
- **La neblina ahora despeja 12 m a la redonda** (antes 4.2 m): el radio anterior era menor que la
  precisión que se acepta (±8 m), así que una lectura podía despejar un sitio equivocado; con 12 m el
  punto real siempre cae dentro del despeje. El recorrido además se lee mucho mejor a zoom de detalle.
  Los filtros no cambian (menos de 12 s, precisión mejor que 8 m, sin saltos a más de 30 m/s y tres
  lecturas seguidas tras un brinco del GPS).
- **La lista de destacados queda en cinco** servicios (Ride, Pagar servicios, Cartera, Avisos y Mi
  cuenta) en la barra del inicio y en el catálogo.

### Eliminado

- **Lector de QR**: se elimina la pantalla de escaneo, su ruta, el servicio «Escanear QR» del
  catálogo, el glifo, su acceso en el saludo del inicio, el mensaje de la cartera que mandaba a esa
  pantalla y la captura `docs/screens/escaner.png`. Nada queda llamando a una ruta que ya no existe.

## [0.21.0] — 2026-11-20

### Añadido

- **Neblina de exploración en el mapa** (botón 🌫 en los controles, también en el mapa del
  inicio): al encenderla el mapa queda cubierto y sólo se despeja un radio de **4.2 m** alrededor
  de lo caminado, así que el estado se va descubriendo a pie. El recorrido se guarda en el
  dispositivo (`nahua.mapa.neblina.v3`) y sobrevive a cerrar la app; una pulsación larga en el
  botón arma el borrado y un segundo toque lo confirma.
- **La neblina no acepta falsas confirmaciones**: sólo despeja con lecturas de menos de 12 s,
  precisión mejor que 8 m, sin saltos a más de 30 m/s y, si el GPS pega un brinco, exige tres
  lecturas seguidas en el sitio nuevo antes de abrir nada. Una lectura dudosa nunca despeja.
- **Brújula con ojo y cono**: el marcador de posición es un 👁 y un cono azul gira con la
  orientación del teléfono, de modo que se ve hacia dónde estás mirando. La barra muestra el
  rumbo (`👁 NE 45°`). El rumbo lo calcula el host con el sensor de rotación
  (`HeadingProvider`): se toma la dirección del aparato suponiendo el teléfono con el borde
  superior inclinado hacia adelante, corregida por la rotación de pantalla y suavizada sobre el
  vector para que el cono no tiemble. Sin sensor, la página avisa en vez de inventar el rumbo.
- `Nahua.watchHeading(handler)` y `Nahua.onHeadingError(handler)` en el SDK (v1.7.0), con
  respaldo `DeviceOrientationEvent` para probar la página en el navegador.

### Cambiado

- **El buscador del mapa se retira**: quedan el GPS y las coordenadas, tanto en la mini app como
  en el mapa del inicio. Sin buscador tampoco hay destinos ni tarjeta de ruta; el mapa se centra
  en ti y la neblina se encarga de lo demás.
- **El mapa del inicio ya tiene GPS en vivo** (antes sólo pedía un punto al tocar ◎), que es lo
  que necesita la neblina para despejar el recorrido.
- Al encender la neblina el mapa se acerca a z17: un despeje de 4.2 m sólo se lee con el detalle
  urbano delante.
- **El mapa sigue al ojo al caminar** mientras la neblina está encendida; si arrastras el mapa, el
  seguimiento se suelta y vuelve al tocar ◎.

### Corregido

- **El mapa del inicio a veces dejaba de responder** después de abrir el mapa a pantalla completa
  y volver: `pauseTimers()` del WebView es global al proceso, así que al regresar los timers
  podían quedar congelados. Ahora el mapa del inicio hace `resumeTimers()` al crearse y en cada
  `ON_RESUME`, avisa `triggerResize()` en cada cambio real de tamaño y al terminar de cargar, y
  destruye su WebView al salir (antes cada visita dejaba un WebView vivo colgado con su GPS).

## [0.20.5] — 2026-11-20

### Cambiado

- **El gesto de correr + saltar salta solo al final del recorrido**: antes disparaba al 34 % del
  radio, así que un roce ya hacía saltar a Mario. Ahora corre en cuanto apoyas el dedo, y el salto
  entra al **85 %** del recorrido (con la perilla ya en el tope, marcado con un aro punteado) y sale
  al 62 %. El control crece a 132×72 px para que ese recorrido sea cómodo con el pulgar.

## [0.20.4] — 2026-11-20

### Corregido

- **Los botones no podían bajar del rectángulo del juego**: EmulatorJS crea su gamepad **dentro de
  `#display`**, así que al ajustar la pantalla a la proporción del cartucho la capa del mando quedó
  encerrada en esa caja (y la cámara del teléfono tapaba la fila de arriba). Ahora la capa se
  re-monta en `#game` (pantalla completa, `position: fixed`) con `pointer-events: none` en el
  contenedor y `auto` en los controles: los mandos viven en la franja libre de abajo, fuera de la
  imagen, y los toques siguen llegando a cada botón (verificado con `elementFromPoint`).

### Añadido

- **Gesto «CORRER · SALTO»** encima de A/B: apoyas el dedo y B queda pulsado (Mario corre),
  arrastras el dedo fuera del centro y se pulsa A (salta), regresas al centro y se suelta el salto
  **sin dejar de correr**. Funciona en cualquier dirección, con histéresis (entra al 34 % del
  radio, sale al 20 %) para que el salto no parpadee con el temblor del pulgar. Se puede apagar
  desde *Ajustes → Mando*.

## [0.20.3] — 2026-11-20

### Añadido

- **Posición de la pantalla con ajuste propio** (pestaña *Pantalla*): un deslizador de 0 a 100 %
  coloca el juego donde quieras —0 % pegado a la franja superior, 100 % pegado al borde de abajo—
  sin tocar los controles. Se guarda en `nahua.arcade.screen`.

### Corregido

- **Acomodar el mando movía la pantalla**: `layoutScreen()` centraba el canvas en el hueco que
  dejaba el gamepad, así que cambiar su altura o su tamaño recolocaba el juego. Ahora la pantalla
  usa todo el alto disponible entre la franja superior y el borde inferior, y su posición depende
  solo de su propio ajuste.

## [0.20.2] — 2026-11-20

### Corregido

- **Las esquinas redondeadas del teléfono recortaban el juego**: el canvas ocupaba desde el borde
  superior de la pantalla, justo donde están el recorte y la cámara. Ahora hay una franja superior
  reservada (`env(safe-area-inset-top)` + 62 px) y el juego arranca debajo, así que no se pierde ni
  una esquina.
- **La pantalla se estiraba a lo ancho**: ahora respeta la proporción del cartucho (GB/GBC
  160×144, GBA 240×160), se centra horizontalmente y se baja al centro del hueco que queda entre
  la franja y el mando, para que el espacio libre se reparta en vez de amontonarse arriba.

### Añadido

- **Accesos en la franja libre**: «Ajustes» abre el menú del motor (guardados, trucos, avance
  rápido, captura de pantalla, ajustes del núcleo) y «Mando» abre el panel de controles. Ya no hay
  que buscar el engrane flotante sobre la imagen del juego.

## [0.20.1] — 2026-11-20

### Corregido

- **El joystick enviaba la dirección girada 90°**: en Super Mario Bros. Deluxe, empujar hacia
  arriba avanzaba a la derecha. La causa era el mando de EmulatorJS: mide el centro del área
  táctil con `clientWidth/clientHeight`, que no contemplan el `transform: scale()` con el que el
  layout ajusta el tamaño, así que el eje quedaba desfasado. Ahora el mando direccional es propio
  (`NahuaPad`): dibuja el joystick o la cruceta y envía los botones del pad de RetroArch
  directamente (`simulateInput(0, 4|5|6|7, 1|0)`), midiendo el centro con
  `getBoundingClientRect()`, que sí incluye el transform. Verificado con eventos de puntero
  reales: arriba → 4, derecha → 7, diagonal → 4+7, centro → nada.
- **Cambiar joystick por cruceta ya no pide reiniciar**: el pad se vuelve a montar en vivo.
- **El modo zurdo** ahora solo intercambia de lado los contenedores por CSS (antes delegaba en el
  toggle de EmulatorJS, que se llevaba consigo el pad propio).

### Cambiado

- **Vertical fijo**: el emulador ya no gira. Un Game Boy es una consola vertical, en horizontal el
  mando se comía media pantalla y la geometría del pad cambiaba a media partida. El manifiesto
  declara `orientation: "portrait"`, el anfitrión bloquea la Activity en vertical
  (`SCREEN_ORIENTATION_PORTRAIT`, sin volteo por sensor) y el panel muestra «Vertical · fijo» en
  lugar del selector Auto/Vertical/Horizontal.

## [0.20.0] — 2026-11-20

### Añadido

- **Game Boy Color, no solo Advance**: se vendoriza el núcleo `gambatte` (normal y `-legacy`)
  junto a mgba, y la mini-app elige el núcleo por la extensión del archivo. `.gba/.agb/.bin` van
  a mgba; `.gbc/.gb` a gambatte, que detecta el modo por la cabecera del cartucho. `RomStore`
  conserva la extensión en el nombre final (era lo que faltaba para distinguir consolas) y el
  mando de GB/GBC ya no muestra L/R, que en esa consola no existen.
- **Super Mario Bros. Deluxe incluido en el APK** como juego de demostración (`games/smbdx.gbc`),
  con tarjeta propia en la portada marcada «incluido». El resto de juegos siguen entrando por el
  selector del sistema, como copias que el usuario posee.
- **Portada nueva con escenario pixel animado**: un canvas de 176×104 px dibujado a mano
  (cielo nocturno con estrellas, nubes en parallax, colinas, monedas que giran, bloques `?`,
  tubería y un axolote con gorra que corre y salta) escalado por enteros para que no se vea
  borroso. Se detiene solo al entrar al juego o al ocultar la app, y respeta
  `prefers-reduced-motion`. Letrero en la fuente pixel del proyecto.
- **Panel de ajustes con pestañas** (Pantalla / Mando / Juego): filtros de imagen Pixel, LCD, CRT
  y Suave (CSS sobre el canvas, en vivo), modo zurdo, vibración, avance rápido ×2/×3/×5,
  rebobinar, ranuras de guardado 1–5, guardado automático de la pila y contador de FPS. Lo que
  EmulatorJS puede cambiar en caliente se aplica por `changeSettingOption`; lo que exige reinicio
  (rebobinar) se siembra en su `localStorage` antes de construirlo.

### Corregido

- **El mando no se reacomodaba al girar la pantalla**: EmulatorJS escucha `resize` pero su
  `handleResize` solo toca el menú; el gamepad quedaba con la geometría de la orientación
  anterior porque sus contenedores miden px fijos. Ahora hay un layout propio que se recalcula en
  `resize`, `orientationchange` y `visualViewport` (con debounce, porque en Android el evento
  llega antes del tamaño final), escala según el lado corto de la pantalla, anula el `bottom:50px`
  del padre y respeta el área segura de la barra de gestos.
- **Icono**: la «N» ahora se dibuja con bloques escalonados, sombra dura y la moneda coral como
  punto, sobre un jade más profundo; se lee bien a 48 dp y el monocolor hereda la misma silueta.

### Cambiado

- La mini-app se llama «Emulador» (antes «Emulador GBA») y su versión sube a 2.0.0.
- El candado del bundle en los tests ahora exige los cuatro núcleos y solo permite la ROM de
  demostración, además de seguir bloqueando `.gba` sueltos y archivos comprimidos.

## [0.19.0] — 2026-11-19

### Añadido

- **Catálogo real desde el servidor**: la app pide la foto (`/v1/catalog?full=1`) una sola vez y
  después sólo los parches (`/v1/catalog/delta?since=N`) a `https://tienda.tlalchichi.xyz`, que corre
  en el teléfono del dueño. `CatalogApi.kt` hace el HTTP en un hilo propio y entrega el JSON por el
  puente (`Nahua.catalogHead/Full/Delta/Order/Report`); la WebView sigue sin acceso a la red.
- **Estado honesto del catálogo**: el chip distingue *al día*, *con cambios*, *sin actualizar desde
  hace X* y *sin conexión · catálogo guardado*, con la vigencia (`validUntil`) que declara `/head`.
  Los productos se marcan «Agotado» y la compra pide confirmación cuando el catálogo no está al día.
- **Órdenes y reportes al servidor**: al pagar se confirma la compra en `/v1/orders` con la versión
  del catálogo que vio el usuario; si responde `price_changed` u `out_of_stock` se avisa y se vuelve
  a pedir el catálogo. Los reportes se envían a `/v1/reports` y siguen guardándose en el teléfono.
- **Modo demostración explícito**: sin puente o sin red, el bazar usa la foto del bundle y lo dice
  en la portada («sin servidor · catálogo de demostración»).

### Cambiado

- Las tiendas y los banners salen del catálogo, no de una lista fija: una tienda nueva aparece sola.
- `docs/CATALOGO_API.md` documenta el servidor en producción, el puente y la actividad; se retira
  `docs/MERCADO_API.md`. SDK 1.6.0.
- `scripts/test-bazar-catalogo.js` prueba además la foto real del servidor (`scripts/fixtures/`).

## [0.18.0] — 2026-11-19

### Añadido

- **Mercado (mini-app nueva, `mercado`)**: landing del catálogo de las tiendas oficiales del
  campus en el menú principal. Arriba, un banner por tienda (arte CSS con sus últimos productos y
  la oferta vigente) que al tocarlo abre la tienda; abajo, lo recién llegado de todo el mercado.
  La tienda ordena **primero sus productos fijados** —para impulsar la venta— y después los
  últimos agregados.
- **API de catálogo versionado (`catalogo.js`)**: la foto completa se descarga una sola vez y las
  siguientes sincronizaciones piden sólo altas, bajas y campos cambiados. Todas las versiones
  quedan en memoria y se consultan en `Catálogo → Versiones`, incluidos los productos retirados.
  Contrato en `docs/MERCADO_API.md` y prueba en `scripts/test-mercado-catalogo.js`.
- **Trueque con match**: para abrir un chat de trueque los dos tienen que marcar interés. Marcar
  «Me interesa» es privado; el aviso con nombres y objetos se genera sólo cuando el interés es
  mutuo. El interés recibido se muestra como conteo anónimo hasta el match, para que nadie
  truequee por compromiso o presión social.
- **Contacto por teléfono**: el perfil del bazar usa nombre + teléfono (10 dígitos, formato
  `312 123 4567`) en lugar de `@usuario`; el teléfono se comparte sólo dentro de un match y se
  puede editar desde el perfil.
- **Categorías para productos locales**: textiles y bordados, artesanías, comida y conservas,
  plantas y semillas, arte y pintura, y escolar, con su valoración en `BarterValuator`.

### Cambiado

- El Bazar/Trueque se reorienta a la comunidad estudiantil (600 alumnos de distintos municipios):
  los datos semilla, las comunidades y los objetos son locales y de estudiantes. El historial de
  chat de un match queda aunque el trato se cancele o se cierre.
- El modelo guardado del bazar pasa a `nahua.bazar.v3` (`interests` + `matches`); los datos de la
  v2 se descartan y se vuelven a sembrar.

## [0.17.0] — 2026-11-19

### Añadido

- **Yōlcatl (antes Axolote/tamagochi)**: la mini-app de mascota se renombra con voz náhuatl y se
  reconstruye según la arquitectura v2. El manifiesto pasa a `id: "yolcatl"`, `version: 2.0.0`, y
  el catálogo, el glifo del icono, la tarjeta de Inicio y el botón de mascota de la barra inferior
  apuntan al id nuevo.
- **Motor vóxel propio (`engine3d.js`, ~34 KiB)**: renderizador WebGL 1.0 sin dependencias, con
  modelos definidos como matrices 3×16³, culling de caras y sombreado por cara. Cinco especies
  (axolote, ajolote dorado albino, tortuga de río, xoloitzcuintle y colibrí), seis accesorios
  anclados a sockets (`head`, `face`, `neck`, `back`, `floor`) y cuatro biomas con props,
  partículas y cámara que enmarca por especie. Si el WebView no expone WebGL, cae a un render 2D
  por pintor sobre el mismo canvas.
- **Utility AI offline (`ai-soul.js`)**: cuatro genes ocultos (curiosidad, sociabilidad, vitalidad,
  apetito) que modulan metabolismo y decisiones; pulso autónomo de 5–15 s que elige entre dormir,
  buscar comida, jugar, saludar, limpiarse, observar o posar; memoria emocional (afecto, escena y
  accesorio favoritos, conteos de cuidado) y ciclo circadiano por reloj del teléfono. Todo en el
  hilo local: cero peticiones de red.
- **Mosaico del alma (`mosaic-codec.js`)**: 16×16 teselas con paleta de 8 colores (3 bits/celda).
  32 celdas de calibración dejan **224 celdas útiles = 672 bits = 84 bytes**, con nombre, especie,
  genes, nivel/XP, barras, escena, accesorios y memoria, más CRC-16 sobre todo el payload. Se
  exporta como código `YLC2:…` (base64url) y se importa validando el checksum.
- **Interfaz pixel art (`pixel.css`, `app.js`)**: tipografía monoespaciada con versalitas, bordes de
  2 px, sombras duras, iconos SVG de 8×8 y barras segmentadas. Tres pestañas (Cuidar, Mundo, Alma),
  soporte de `prefers-color-scheme: dark` y edición del nombre en línea.
- **Migración del alma v1**: al abrir por primera vez se recuperan nombre, barras y XP de
  `nahua.axolote.v1`; el guardado nuevo vive en `nahua.yolcatl.v2`.

### Corregido

- **Decaimiento congelado**: se elimina el umbral de 0.02 h y `lastSeen` se refresca en cada tick de
  60 s, así la simulación avanza en vivo en lugar de quedarse quieta.
- **Pérdida por redondeo**: las barras se persisten como flotantes y `Math.round` se aplica solo al
  pintar (0.15 pts/min de comida ya no desaparecen).
- **Nivel máximo**: al llegar a Espíritu del lago la barra de XP muestra `MÁXIMO` al 100 % en lugar
  de quedarse en `0 / 40`.
- **Accesorios al importar**: un alma restaurada desde el mosaico recupera los accesorios que su
  nivel y su afecto ya merecían (antes solo se regalaban al subir de nivel en la sesión).

## [0.16.3] — 2026-11-19

### Añadido

- **Jugar en vertical**: el panel *Configurar controles* añade **Pantalla: Auto / Vertical /
  Horizontal**. El giro se aplica al instante por el puente (`Nahua.orientation` →
  `requestedOrientation` de la Activity) y queda guardado con el resto de la configuración. En
  vertical el GBA se ve a 3:2 arriba, apartado de la cámara, y los mandos quedan en la zona negra
  inferior. El manifiesto del emulador pasa a `orientation: "auto"`: la preferencia del usuario
  manda sobre el valor por defecto.
- **Mando del emulador con joystick y menú de configuración de la consola**: el mando izquierdo
  pasa de cruceta a **joystick** (tipo `zone` de EmulatorJS, nipplejs) y un panel nuevo permite
  ajustar **tamaño**, **altura** y **margen lateral** del gamepad, además de alternar
  joystick/cruceta. El tamaño y la posición se aplican **en vivo** por variables CSS (sin
  reiniciar la partida); el tipo de mando se aplica al reiniciar. Se guarda en
  `localStorage` (`nahua.gba.controls`) y el botón **Menú** de la tira nativa abre este panel,
  que a su vez enlaza con los ajustes propios de EmulatorJS.
- **Háptica real en los controles del emulador**: `NahuaNative.haptic(style)` en el puente y
  `Nahua.haptic()` en el SDK (v1.4). El host ejecuta `performHapticFeedback` sobre el WebView, sin
  permiso `VIBRATE` y respetando el ajuste del sistema; la mini-app `gba` lo engancha por
  delegación en `touchstart` sobre los botones virtuales de EmulatorJS (`.ejs_virtualGamepad_button`)
  con un tope de 40 ms para que un dedo apoyado no zumba sin fin.
- **Telemetría de frame pacing y audio** en la mini-app `gba`: ventana de 60 s por partida que
  publica `[perf]` en logcat (fps, p50/p95/max en ms, % fuera de presupuesto, frames perdidos y
  `baseLatency`/`outputLatency` de WebAudio), más `NahuaPerf.start()/report()/stop()` para medir a
  mano desde `chrome://inspect`. Es la base para decidir si vale la pena un núcleo nativo
  (mGBA + AAudio): el GBA refresca a 59.7275 Hz, no a 60, y esa deriva no se ve con
  `requestAnimationFrame` — el reporte lo deja claro para no leerlo de más.
- **`scripts/gba-perf.ps1`**: captura esa ventana desde adb (limpia el buffer, espera el `[perf]`,
  guarda el JSON en `app/build/perf/`) y emite el veredicto **Go/No-Go** de la Fase 2 con el
  criterio documentado en [docs/GBA-PERF.md](docs/GBA-PERF.md), incluido el orden de trabajo si
  el veredicto es Go (NDK, núcleo mGBA, EGL, AAudio, APK único).

## [0.16.2] — 2026-11-18

### Corregido

- **La portada del emulador no se veía**: el layout usaba `height: 100%` y unidades de
  viewport (`dvh`/`vh`/`svh`). En el WebView de Android con modo inmersivo el bloque contenedor
  inicial mide 0, así que todas resolvían a `0px`: `#home` quedaba en 26 px con `overflow: auto`
  (contenido recortado) y los toques no alcanzaban los botones. Ahora las pantallas usan
  `position: fixed; inset: 0`, que sí toma el viewport real, y el canvas del juego llena con
  `flex`. Verificado en emulador: portada visible y pulsable, ROM importada y mGBA renderizando.
- **Volver a la portada estando en juego**: nueva tira nativa **Salir · Inicio · Menú** (con
  insets de `displayCutout`). **Inicio** recarga la portada dejando la partida en "Seguir jugando";
  **Menú** abre el panel de ajustes de EmulatorJS. Se quita el botón "Recargar", que era redundante.

## [0.16.1] — 2026-11-18

### Corregido

- **Pantalla negra al cargar la ROM**: los nucleos de EmulatorJS viajan empaquetados en 7z y el
  bundle no incluía `compression/extract7z.js`, así que el nucleo se descargaba pero nunca se
  descomprimía (404 del asset → fallback al CDN → 404 del bloqueo offline → pantalla negra).
  Ahora se vendoriza el extractor y la carga quedó verificada de punta a punta en navegador
  contra el bundle del APK: mGBA arranca la ROM de prueba y renderiza el título con letterbox
  3:2.
- **Botón de salida tapado por la cámara en horizontal**: el ✕ estaba en la esquina sin
  respetar el recorte del notch; solo se veía una franja inutilizable. Se sustituye por una tira
  nativa — **Salir**, **Menú** (abre el menú/ajustes de EmulatorJS vía
  `EJS_emulator.menu.toggle()`) y **Recargar** — con `displayCutout`/`statusBars` insets, y el
  bundle web suma `safe-area-inset` y una portada deslizable en landscape.
- `rooms/` queda ignorado: las ROMs de prueba del usuario nunca al repositorio ni al APK, y el
  test de integridad del bundle exige el extractor 7z para que la regresión no regrese.

## [0.16.0] — 2026-11-18

### Añadido

- **Emulador de Game Boy Advance como mini-app**: nueva mini-app inmersiva `gba` sobre EmulatorJS
  4.2.3 (núcleo mGBA en WebAssembly), vendorizada (2.47 MB) en `assets/miniapps/gba/emulatorjs/`
  con `scripts/fetch-emulatorjs.ps1`. El usuario carga sus propias ROMs `.gba` con el selector del
  sistema; el anfitrión las copia a `filesDir/roms` (validación de extensión, tope de 64 MiB y
  deduplicación por SHA-256) y las sirve por el handler `/rom/` del asset loader. Partidas en
  IndexedDB, pantalla completa horizontal, audio pausado en segundo plano y recuperación si el
  proceso de render muere. Sin ROMs ni BIOS en el APK; licencia GPL-3.0 de EmulatorJS documentada
  en `assets/licenses/`. El nucleo pesa 2.47 MB dentro de un APK de 41.9 MB y los tests suben a
  156 (RomStore, MiniAppManifest y el candado anti-ROM del bundle).
- **Runtime de mini-apps v1.3**: el manifiesto admite `display: "immersive"` (pantalla completa,
  sin marco de Nahua) y `orientation: "landscape"`; el puente expone `pickRom`, `listRoms` y
  `deleteRom`; `shouldInterceptRequest` responde con 404 vacío a cualquier origen externo
  (offline garantizado); `ON_PAUSE`/`ON_RESUME` pausan el WebView y `onRenderProcessGone` evita
  que un crash del render tire la app. El SDK JS (`nahua-sdk.js`) sube a v1.3 con las tres
  funciones de ROMs.
- **Mapa estatal con cartografía del INEGI**: se integró la *Cartografía Geoestadística Urbana y
  Rural Amanzanada, junio 2016* de Colima (171 localidades, 17,148 manzanas, 15,812 ejes viales y
  224 servicios). Las teselas z14–16 ahora cubren las localidades amanzanadas de todo el estado, no
  solo el área metropolitana, y se guardan en WebP para que el paquete ronde los 10 MB.
- **Buscador de 395 lugares**: localidades y servicios del INEGI se suman a tianguis y puntos de
  interés; los servicios aparecen como etiquetas en las teselas y se pueden buscar sin una capa
  flotante.
- **Enlaces de descarga**: README documenta las URLs directas del INEGI para el paquete urbano, las
  hojas topográficas 1:50 000, la serie estatal 1:250 000, carreteras, uso de suelo y el MDE.

### Cambiado

- **Interfaz del mapa simplificada**: se quitaron el panel de lugares, la lista de tianguis, los
  chips de municipios, los botones de zoom, el botón de seguimiento y la capa de servicios. Quedan
  el buscador, la barra de GPS y un único botón **◎** para centrar el mapa.
- La capa de detalle es una sola capa estatal WebP z14–16; la base z9–13 sigue en PNG.
- Se regeneraron las teselas de detalle (2,306 archivos, 3,686 en total, ~10 MB) con
  `.tmp/inegi/render_v4.py`.
- El test de activos del mapa ahora valida WebP, cobertura en Manzanillo/Tecomán/Comala y el paquete
  estatal; 140 tests unitarios.

## [0.15.0] — 2026-11-17

### Validado con la API real (OpenCode Go)

- **Chat, streaming y comparación** probados contra `deepseek-v4.1-flash` con la key del usuario:
  C1, C2 y C7 devuelven respuestas citadas (hasta 17 citas) y C4 produce la tabla por eje y por obra
  con 28 citas y celdas “no se pronuncia”. El proveedor exige la cabecera `x-opencode-session`, que
  el cliente ya mandaba.
- **Hallazgo y arreglo importante**: `deepseek-v4.1-flash` es un modelo de **razonamiento**
  (`reasoning_content`). Con presupuestos cortos gastaba todos los tokens pensando y devolvía
  `content` vacío con `finish_reason=length`. Ahora el cliente detecta ese caso, **reintenta con el
  doble de tokens** (tope 16 000) y, si aun así no hay respuesta, explica el motivo. Presupuestos
  nuevos: correlación 8 000, tareas 4 000–4 500, fichas 2 500.

### Recuperación

- **Bonus de bigramas** en `Retriever`: si dos términos seguidos de la pregunta aparecen juntos en el
  fragmento (“salario doméstico”), suma; antes las palabras sueltas diluían la frase.
- **Benchmark con el corpus real** (`CorpusBenchmarkTest`, 535 páginas): 4/5 casos con la página
  esperada, C4 cubre 4 obras, C6 marca evidencia débil. C3 (“salario doméstico”) queda documentado
  como fallo conocido de BM25. El test escribe `build/eval/contexts.json` y `diag.txt` para
  inspección.

### Notas

- `docs/EVAL.md` con la medición, las respuestas reales y el detalle del razonamiento.
- 139 tests en JVM (1 nuevo: benchmark del corpus).

## [0.14.1] — 2026-11-17

### Añadido

- **Botón VECTORES en la mesa**: vectoriza los fragmentos que ya estaban indexados (libros subidos
  antes de configurar el proveedor de embeddings), por lotes de 32, con progreso “vectores 40/180”
  y sin reimportar nada.

### Corregido

- Un libro ya indexado nunca se vectorizaba al configurar embeddings después: la deduplicación por
  SHA-256 impide reimportarlo y el worker sólo vectoriza al importar. Ahora la acción es explícita.

## [0.14.0] — 2026-11-17

### Añadido

- **RAG híbrido opcional (NA-018/019)**: si configuras un proveedor con `/embeddings`, los fragmentos
  se vectorizan al importar y la búsqueda combina **BM25 + vectores** con *Reciprocal Rank Fusion*.
  Sin configurar nada, todo sigue siendo BM25 puro.
- **Vectores en Room** (`chunk_embeddings`, esquema v2 con `MIGRATION_1_2`): BLOB de `FloatArray`,
  modelo y dimensión; borrado en cascada con el fragmento.
- **Vectorización en segundo plano**: el worker embebe por lotes de 32 con etapa
  “vectorizando 2/6”; si el proveedor falla, la importación termina igual y se queda el índice léxico.
- **Ajustes de embeddings** en la hoja de configuración (base URL, modelo, key opcional que hereda la del chat).
- 6 tests nuevos (códec, coseno, fusión RRF, fallback y configuración); 138 en total.

### Notas

- Los embeddings son **opcionales a propósito**: OpenCode Zen/Go y OpenRouter sirven chat pero no
  `/embeddings`; para activar el modo híbrido usa un proveedor que sí lo exponga (OpenAI, Ollama,
  vLLM, un servidor propio). La chat puede seguir en el proveedor que prefieras: son endpoints
  independientes.
- El índice vectorial es un recorrido completo (coseno sobre los vectores de la mesa), suficiente
  para decenas de libros; un índice HNSW queda para cuando haya cientos.

## [0.13.0] — 2026-11-17

### Añadido

- **Contratos del orquestador** (`core/agent`): `AssistantTool`, `ToolRegistry`, `ToolBudget` y
  `ModelGateway` con un adaptador `ChatClientGateway`. La UI ya no construye prompts ni elige
  modelo: pide una intención y el registro resuelve presupuesto y estrategia.
- **Enrutador determinista** (`IntentRouter`): "compárame los libros", "hazme un resumen",
  "prepara el guion"… se convierten en la herramienta correspondiente (sin acentos y sin gastar
  tokens). Lo que no parece una orden sigue siendo pregunta libre.
- **Presupuesto por herramienta**: correlación 6 llamadas / 3 000 tokens de salida; guion y
  preguntas 2 800; el resto 2 400. Las fichas de la comparación usan 900 tokens cada una.
- 4 tests nuevos del enrutador y del registro; 132 en total.

### Documentación

- `docs/AGENT.md`: contratos, reglas de enrutamiento, presupuestos y cómo añadir una herramienta.

## [0.12.0] — 2026-11-17

### Añadido

- **OCR local para PDFs escaneados (NA-010)**: si más del 70 % de las páginas no traen texto
  seleccionable, el worker renderiza cada página con `PdfRenderer` y la reconoce con **ML Kit Text
  Recognition** (modelo empaquetado, sin red). El texto reconocido sustituye sólo las páginas vacías;
  lo que el PDF sí traía se conserva.
- Tope de 120 páginas por OCR y aviso de progreso por página (“ocr 12/80”) en la tarjeta de
  importación.
- Si tras el OCR el documento sigue sin texto, el error lo dice y sugiere revisar la calidad del
  escaneo.
- 4 tests nuevos del detector y de la mezcla de texto; 128 en total.

### Notas

- El OCR usa **ML Kit sin modelo empaquetado** (`play-services-mlkit-text-recognition` 19.0.1): el
  APK crece sólo ~2 MB (37.4 MB) y el modelo latino se descarga **una vez** vía Google Play Services
  la primera vez que se usa OCR. Funciona sin conexión después de esa descarga.
- Requiere Google Play Services en el teléfono; si no está o la descarga falla, el worker lo trata
  como “sin texto” y muestra el error con la causa.
- El OCR es lento (1–3 s por página) y por eso vive en el worker de fondo, nunca en la UI.

## [0.11.0] — 2026-11-17

### Añadido

- **Comparación en dos pasadas**: para la tarea de correlación con 2+ obras, primero se pide una
  **ficha por obra** (una llamada barata por libro, con sus citas) y después una sola llamada que
  cruza las fichas. Evita que el modelo resuma cada libro en lugar de compararlos.
- Progreso visible por obra (“Comparando 2/5 · Paredes”) y estrategia `2 PASADAS` en la tarjeta.
- La caché de dos pasadas usa una clave propia (sin historial) y guarda el consumo sumado de todas
  las llamadas.
- 3 tests nuevos de los prompts de ficha y de cruce; 124 en total.

### Notas de costo

- Una comparación de 5 obras pasa de 1 llamada a 6 (5 fichas + 1 cruce). Las fichas son cortas
  (≈1–2k tokens de entrada cada una); a cambio la tabla resultante sí compara con citas por celda.

## [0.10.0] — 2026-11-17

### Añadido

- **Biblioteca en Room (NA-007/008)**: `LibraryDatabase` con `DocumentEntity`, `ChunkEntity` y
  `FigureEntity` (claves foráneas en cascada, índices por mesa, documento y hash). El esquema se
  exporta en `app/schemas/` para futuras migraciones.
- **Migración automática desde JSON**: al abrir una mesa se importa una sola vez su índice anterior
  a Room; los JSON se archivan en `filesDir/rag-legacy` y las figuras se quedan en su carpeta.
- `RagRepository` mantiene la misma API pública, así que el motor y la UI no cambiaron.
- Dependencias: `room-runtime`/`room-ktx` 2.7.2 con KSP 2.2.10-2.0.2; `org.json` real sólo para
  pruebas JVM.

### Verificado

- En el emulador, con la app anterior instalada (índice JSON de 1 PDF), tras instalar esta versión la
  mesa siguió mostrando “1 PDF · 4 pág · 4 frag”: la migración a Room se ejecutó sin perder datos.
- 121 tests en JVM (4 nuevos del lector de JSON legado).

## [0.9.0] — 2026-11-17

### Añadido

- **Importación en segundo plano (NA-009)**: cada archivo se indexa en un `ImportWorker` de
  WorkManager, encadenados uno por libro. El trabajo sobrevive a cerrar la app, se reanuda si el
  sistema interrumpe el proceso y muestra progreso por libro con su etapa (leyendo, extrayendo,
  fragmentando, guardando).
- **Permiso persistente del selector**: se toma `takePersistableUriPermission` antes de encolar,
  así el worker puede reabrir el archivo aunque el proceso muera.
- La observación de la cola usa `getWorkInfosForUniqueWorkFlow`, fuera del hilo principal, y se
  cancela al borrar la mesa.

### Cambiado

- La importación ya no vive en el ViewModel: la UI encola y observa; el trabajo pesado corre en
  `WorkManager`.
- `work-runtime-ktx` 2.10.1 como nueva dependencia (trae Room 2.6.1 y Lifecycle transitivos).

### Notas

- Room sigue pendiente (NA-007/008): hoy la biblioteca es JSON con hash y versión por documento.
- La sustitución del motor de importación no se pudo probar de punta a punta en el emulador de esta
  sesión (el selector de archivos y el inicio pixel art son muy lentos); sí compila, pasa las 117
  pruebas y la app arranca y abre el estudio.

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