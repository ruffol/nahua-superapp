# Nahuanotoka · mesas de trabajo con tus PDFs

## El problema que resuelve

Eres estudiante. Tienes una exposición en tres días y doce PDFs: tres capítulos del libro, dos
artículos, las diapositivas de la clase, un par de lecturas escaneadas y tus apuntes exportados.
Lo normal es abrir cada archivo, buscar con Ctrl+F, copiar y pegar, resumir a mano y perder la
página de la cita. Al final expones con datos que no puedes respaldar.

Nahuanotoka convierte ese montón en una **mesa de trabajo por materia**:

| Sin la app | Con Nahuanotoka |
| --- | --- |
| Abres 12 PDFs y pierdes 2 horas resumiendo | Pides *Resumen* y salen los puntos clave con página |
| Copias citas y olvidas de dónde salieron | Cada afirmación viaja con `[Documento, p. N]` |
| No sabes si dos lecturas se contradicen | *Correlación* cruza los documentos y marca coincidencias, choques e intersección |
| Las ideas propias se quedan en la cabeza | *Ideas nuevas* separa lo textual de lo inferido |
| El resumen vive en una nota que no puedes imprimir | *PDF* genera un documento paginado y lo comparte |
| No sabes cuánto gastas en la API | Cada mesa y cada respuesta muestran sus tokens |

La app **no inventa**: si el dato no está en los documentos, lo dice y te señala qué archivo revisar.

## Qué es una mesa de trabajo

Una mesa es una materia o una exposición. Guarda:

- **Nombre y materia** (“Historia de México · bloque 3”).
- **Contexto**: instrucciones tuyas para el modelo (público, duración, tono, enfoque). Se manda en
  cada consulta, así que no repites “es para exponer en 10 minutos” cada vez.
- **Documentos**: los PDFs de esa materia, con sus páginas, fragmentos y figuras.
- **Conversación**: lo que pediste y lo que respondió, con modelo, tokens y fuentes.
- **Consumo**: tokens de entrada, de salida y total, más el número de consultas.

Puedes tener tantas mesas como materias curses. Todo se procesa en el teléfono; sólo la pregunta y
los fragmentos elegidos viajan a tu API.

## Flujo de uso

1. **Servicios → Nahuanotoka → Nueva mesa.** Ponle nombre, materia y contexto.
2. **Agregar PDF** (texto seleccionable, hasta 300 páginas). Puedes **seleccionar varios a la vez**:
   se indexan en el dispositivo y, si tienen imágenes incrustadas, aparecen como **figuras** listas
   para tus diapositivas. Al final se resume cuántos entraron, cuántas páginas y cuántas figuras.
3. **Herramientas de estudio** (una consulta cada una):

   | Herramienta | Qué devuelve |
   | --- | --- |
   | Resumen | Idea central, 5–8 puntos con página y 3 conceptos para explicar con tus palabras |
   | Ideas nuevas | 6 ideas con etiqueta *apoyo textual* o *inferencia propia* |
   | Correlación | Coincidencias, diferencias/contradicciones, exclusivo de cada documento, intersección y huecos |
   | Guion de exposición | Apertura, bloques con diapositiva + qué decir + cita, cierre y preguntas del público |
   | Glosario | 10–15 términos definidos con el texto y su página |
   | Preguntas de repaso | 8 preguntas con respuesta modelo y cita |

4. **Pregunta libre** en el compositor (“¿por qué cayó el porfiriato?”, “compárame las dos
   lecturas”).
5. **PDF**: exporta el último análisis (o toda la conversación de la mesa) a un PDF paginado con
   portada, tabla, fuentes y pie de página, y compártelo con WhatsApp, Drive o la impresora.
6. **Correlaciona sólo una parte**: toca los PDFs que quieras incluir; si no seleccionas ninguno, se
   usan todos.

## Cómo decide qué leer (y por qué antes fallaba)

El motor anterior sólo comparaba palabras exactas contra el texto. Con un PDF de 8 páginas y la
pregunta “Dame un resumen”, los términos útiles eran `dame` y `resumen`: ninguna aparece en el
documento, así que devolvía *“No encontré pasajes relevantes”*. Lo mismo con “¿De qué trata?”.

Ahora hay tres capas:

1. **Búsqueda léxica por raíz (BM25 ligero).** “obligacion” encuentra “obligaciones”; “contamina”,
   “contaminación”. El nombre del documento también puntúa.
2. **Cobertura por documento.** Las tareas globales (resumen, ideas, correlación, guion, glosario,
   preguntas) no buscan palabras: reparten el presupuesto de fragmentos entre **todos** los
   documentos y los intercalan, para que la mesa completa entre al contexto.
3. **Respaldo honesto.** Si una pregunta puntual no tiene ninguna coincidencia, no se rinde: manda
   los fragmentos más representativos y marca la consulta como *débil*, de modo que el modelo
   empiece aclarando qué sí cubre el material y qué no.

Presupuesto por consulta: `topK` fragmentos (8 por defecto, ajustable 3–14) y un tope de ~14 000
caracteres, para no reventar la ventana ni la cartera.

## Tokens y costo

- El campo `usage` de la respuesta del proveedor se guarda por turno y se acumula por mesa.
- Si el proveedor no lo devuelve, se estima con ~4 caracteres por token y se indica igual.
- Se muestran en la tarjeta de la mesa (entrada / salida / total), en la lista de mesas y en cada
  respuesta.
- Con `deepseek-v4.1-flash` (0.30 USD por millón de entrada y 1.20 por millón de salida) una mesa de
  estudio típica cuesta centavos: una consulta con 8 fragmentos ronda 2 500–4 000 tokens de entrada.

## Modelo por defecto

| Campo | Valor |
| --- | --- |
| Base URL | `https://opencode.ai/zen/go/v1` |
| Modelo | `deepseek-v4.1-flash` |
| Endpoint | `{baseUrl}/chat/completions` |
| Auth | `Authorization: Bearer <api key>` |

Presets en la hoja de ajustes: `deepseek-v4.1-flash` (recomendado), `deepseek-flash`,
`deepseek-v4-pro`, `kimi-k2.7-code`, `glm-5.1`, `mimo-v2.5-pro`. El campo es editable: cualquier
endpoint compatible con OpenAI (OpenAI, OpenRouter, Ollama, vLLM) funciona. Si tu proveedor no
acepta `max_tokens`, baja el ajuste o usa otro modelo.

## Arquitectura

```
SAF (OpenDocument)
      │
      ▼
PdfExtractor (PDFBox-Android)      texto por página + figuras incrustadas
      │
      ├── TextChunker              fragmentos de ~900 caracteres con traslape
      │        └── filesDir/rag/<mesa>/doc_<id>.chunks.json
      └── Bitmap JPEG              filesDir/rag/<mesa>/fig_<id>_<n>.jpg
      │
      ▼  (al consultar)
ContextBuilder                    búsqueda por raíz o cobertura por documento
      │
      ▼
ChatClient (POST /chat/completions)  → texto + usage
      │
      ├── filesDir/studio/chats/<mesa>.json   historial
      ├── filesDir/studio/mesas.json          mesas + tokens
      └── ReportWriter → PDF en caché → FileProvider → compartir
```

| Clase | Responsabilidad |
| --- | --- |
| `PdfExtractor` | Texto por página y figuras (≥160 px, reescaladas a 900 px) en una sola pasada |
| `TextChunker` | Normaliza espacios y parte en fragmentos con traslape, cortando en palabras |
| `Retriever` | Tokeniza sin acentos ni muletillas, puntúa por raíz y nombre de documento |
| `ContextBuilder` | Elige fragmentos: búsqueda puntual, cobertura global o respaldo débil |
| `StudioPrompts` | System prompt con el contexto de la mesa + formato de cada herramienta |
| `ChatClient` | HTTP mínimo compatible con OpenAI; devuelve `usage` y errores accionables |
| `StudioRepository` | Mesas, contexto, historial y contadores de tokens en JSON |
| `RagRepository` | Documentos y figuras por mesa |
| `ReportWriter` | PDF A4 con Canvas: portada, encabezados, viñetas, tablas, fuentes y paginación |

## Almacenamiento

```
filesDir/rag/<mesaId>/
├─ doc_<id>.meta.json     { id, mesa, name, pages, chunks, characters, addedAt, figures[] }
├─ doc_<id>.chunks.json   [ { doc, page, text }, ... ]
└─ fig_<id>_<n>.jpg       figuras incrustadas

filesDir/studio/
├─ mesas.json             [ { id, name, subject, context, accent, usage, requests } ]
└─ chats/<mesaId>.json    [ { role, text, taskId, model, tokens, sources[] } ]

cacheDir/reports/*.pdf    PDF generado (compartido con FileProvider)
```

Los documentos del formato anterior (sin mesas) se migran solos a la mesa *“Mis documentos”* la
primera vez que abres la app.

## Privacidad

- El PDF nunca se sube: se extrae en el teléfono y sólo vive su texto y sus figuras en el
  almacenamiento privado.
- No se envían documentos completos: un máximo de `topK` fragmentos por consulta.
- La API key se guarda en Preferences DataStore privado; para producción conviene Keystore.
- Los errores de red no reintentan solos, para no duplicar consumo.

## Límites conocidos

| Límite | Motivo |
| --- | --- |
| 300 páginas por PDF | Tiempo de extracción en gama baja |
| PDFs con texto | Los escaneados necesitan OCR previo |
| 8 fragmentos / ~14 000 caracteres | Costo y ventana del proveedor |
| 12 figuras por PDF | Memoria del teléfono |
| 80 turnos por mesa | Tamaño del historial |

## Diagnóstico

| Síntoma | Causa probable |
| --- | --- |
| “no tiene texto seleccionable” | PDF escaneado; aplica OCR |
| “La búsqueda no encontró coincidencias” | La mesa responde con lo más representativo; revisa si el tema está en tus PDFs |
| 401/403 | API key inválida o sin permisos |
| 404 | Base URL o modelo incorrectos |
| 413/400 | Contexto demasiado grande o `max_tokens` no soportado: usa flash |
| 429 | Límite del proveedor; espera o cambia de modelo |

## Tests

- `ContextBuilderTest`: cobertura entre documentos, respaldo débil, presupuesto de caracteres,
  selección para correlación.
- `RetrieverTest`: ranking, raíces y plurales, muletillas (“dame un resumen”), acentos, límite.
- `TextChunkerTest`: tamaños, traslape y páginas.
- `RagSettingsTest`: endpoint y validación.

```bash
./gradlew testDebugUnitTest
```