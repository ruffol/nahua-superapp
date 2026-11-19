# Yōlcatl · criatura viva de la mini-app

`assets/miniapps/yolcatl/` reemplaza al viejo `tamagochi`. Nada de Kotlin cambió para que funcione:
la mini-app sigue viviendo dentro del WebView, sin red y con `Nahua.close()` / `Nahua.haptic()`
como únicos contactos con el anfitrión.

## Piezas

| Archivo | Qué hace |
| --- | --- |
| `index.html` | Estructura de la interfaz pixel art (escenario, placa, pestañas, modales). |
| `pixel.css` | Tema visual: bordes duros, sombras desplazadas, barras segmentadas, modo claro/oscuro. |
| `app.js` | Cablea alma ↔ motor ↔ DOM, temporizadores y persistencia. |
| `ai-soul.js` | Genes, decaimiento, Utility AI, memoria emocional y guardado (`nahua.yolcatl.v2`). |
| `mosaic-codec.js` | Empaqueta el alma en un mosaico 16×16 (84 bytes, CRC-16) y lo dibuja en canvas. |
| `engine3d.js` | Motor vóxel WebGL propio: especies, accesorios por socket y biomas. |

## Ciclo de simulación

1. `applyDecay(soul, ms)` corre cada 60 s **sin umbral**: las barras son flotantes y solo se
   redondean al pintar. Con la app cerrada la energía baja a la mitad.
2. Cada 5–15 s un pulso de Utility AI puntúa conductas (`sleep`, `seekFood`, `play`, `greetUser`,
   `observe`, `cleanUp`, `pose`), elige la de mayor utilidad con algo de ruido y la sostiene unos
   segundos. El reloj del teléfono entra como ciclo circadiano y la memoria como bono de escena.
3. Las acciones del usuario suman XP con la escalera `0, 40, 95, 165, 250`; cada etapa regala un
   accesorio. La pelota flotante se gana con afecto ≥ 70. Al llegar al nivel 5 la barra muestra
   `MÁXIMO`.

## Alma portátil

`YolcatlMosaic.pack(soul)` produce **84 bytes**: esquema, nombre (12), especie, 4 genes, nivel y XP,
4 barras, escena, 6 accesorios, memoria y CRC-16 al final. `toMatrix()` reparte los 672 bits en 224
celdas útiles (las otras 32 son anclas de calibración), `render()` dibuja el mosaico con marco
ceremonial y `decode()` valida el CRC antes de aceptar un alma ajena. El transporte es el código
`YLC2:<base64url>`, que se copia con `Nahua.copy()` y sobrevive sin servidores.

## Añadir una especie o un bioma

- **Especie**: escribe un `build…()` con `Builder` (elipsoides y cajas en una rejilla de 16³) y
  regístrala en `SPECIES` con `palette`, `focus`, `radius`, `accScale` y sus `sockets`. El esqueleto
  de animación (`body`, `tail`, `gillL/R`, `legs`) lo reutiliza el motor.
- **Bioma**: añade una entrada a `SCENES` con `floor`, `particle` y `props()`, devolviendo
  `propOf(palette, build, dx, dz, q)`; el prop se mide con su propia paleta y se coloca en
  coordenadas de mundo, así no invade al animal.
- **Accesorio**: añade el modelo en `ACCESSORIES` con `anchor` y `pivot`; el `anchor` decide si
  sigue a la cabeza, el cuello, la espalda o el suelo.

## Límites conocidos

- El mosaico se exporta como código, no como archivo: el WebView no tiene permiso de escritura y
  agregar un lector óptico exigiría cámara nativa.
- Las animaciones son transformaciones por parte (respiración, cola, branquias, salto), no skinning;
  suficiente para vóxeles, insuficiente para mallas orgánicas.
- El respaldo 2D se ve bien pero cuesta más CPU que WebGL: es una red de seguridad, no el camino
  principal.

## Verificación

`ServiceCatalogTest.yolcatlBundleKeepsEngineAiAndMosaicCodec` es el candado: exige los cinco
archivos del bundle, que el id viejo no vuelva al catálogo ni al manifiesto, que el decaimiento no
recupere el umbral `0.02 h`, y que el mosaico conserve los 84 bytes y su CRC.
