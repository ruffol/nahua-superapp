# AUDIT.md · Nahua AI — Auditoría del repositorio (Fase 0)

**Commit auditado:** `dac1e01` · **Versión de la app:** 0.8.0 (versionCode 800) · **Fecha:** 17 de noviembre de 2026
**Alcance:** motor RAG (`core/rag`, `feature/rag`, `core/model`), persistencia, credenciales, pruebas y build.
**Regla respetada:** esta fase no cambia comportamiento, no migra datos y no añade dependencias.

---

## 1. Resumen ejecutivo

El RAG existente **funciona y está probado**: 95 tests JVM en verde, build reproducible y un flujo completo
importar → fragmentar → recuperar → generar → exportar. La arquitectura real es más simple que la del plan:
un motor léxico (BM25 por raíz) sobre archivos JSON, con la orquestación dentro de un ViewModel.

Las diferencias con el plan no son de calidad del código, sino de **infraestructura de producto**:

| Área | Estado real | Lo que pide el plan |
| --- | --- | --- |
| Persistencia | JSON por mesa en `filesDir` | Room/SQLite con versiones de documento y migraciones |
| Procesamiento | Corrutina del ViewModel (primer plano) | WorkManager reanudable con estados por etapa |
| Evidencia | `RagSource(documento, página, snippet)` | `EvidenceReference` con `chunkId`, `documentVersionId`, tipo y cita textual |
| Recuperación | BM25 + cobertura + balanceo 3/libro | `RetrievalEngine` con `RetrievalResult`/score/estrategia y híbrido opcional |
| Contexto | Recorte fijo a 1 200 caracteres | Presupuesto de tokens, vecinos y deduplicación de solapes |
| Caché | Clave con historial incluido | Clave normalizada con alcance, versión de índice y configuración |
| Credenciales | DataStore en texto plano | Keystore local + recomendación de proxy |
| Herramientas | 6 plantillas dentro del ViewModel | `ToolRegistry` + `AssistantTool` + orquestador con presupuesto |
| UI | Mesas (materia/exposición) | Biblioteca, documento, asistente, herramientas, trabajos, configuración |
| Exportación | PDF/MD/TXT por respuesta | Artefactos versionados + DOCX |

**Conclusión de la Fase 0:** el plan es compatible con el código si se respeta el orden. No hace falta reescribir
el motor; hace falta (a) arreglar el contexto y la evidencia, (b) mover la ingesta a segundo plano y (c) recién
después introducir Room, herramientas y embeddings.

---

## 2. Mapa de clases actuales

### 2.1 Motor (`core/rag`, 16 archivos)

| Archivo | Rol | Entradas → salidas | Notas |
| --- | --- | --- | --- |
| `PdfExtractor.kt` | Texto y figuras de PDF | `Uri` → `ExtractedPdf(pages, totalPages, truncated, figures)` | PDFBox-Android 2.0.27; máx. 300 páginas; figuras ≥160 px; aplica `TextCleaner` |
| `EpubExtractor.kt` | EPUB → capítulos | `Uri` → `ExtractedPdf` (páginas = capítulos) | Capa fina sobre `EpubParser` |
| `EpubParser.kt` | ZIP + OPF + spine | `InputStream` → `List<Chapter(index, title, text)>` | Sin dependencias; tope 24 MB; HTML→texto y entidades |
| `TextCleaner.kt` | Limpieza | `List<String>` → `List<String>` | Encabezados/pies repetidos, números de página, guiones, párrafos |
| `TextChunker.kt` | Fragmentación | `List<String>` → `List<Chunk(page, text, words)>` | 450 palabras, 50 de solape |
| `RagRepository.kt` | Índice local por mesa | `import/documents/chunks/figures/delete` | `filesDir/rag/<mesa>/` |
| `Retriever.kt` | BM25 ligero | `query, chunks, limit` → `List<ScoredChunk>` | Raíz/plurales, nombre del libro, frase exacta |
| `ContextBuilder.kt` | Selección de contexto | `query, chunks, …` → `Context(blocks, sources, mode, weak, documentCount)` | Modos: `busqueda`, `cobertura`, `balanceado`; recorte fijo 1 200 |
| `StudioPrompts.kt` | Prompt y tareas | `Mesa, Context` → system/user; historial | 7 reglas duras + tareas de estudio |
| `ChatClient.kt` | Cliente compatible OpenAI | `RagSettings, messages` → `ChatReply(text, usage, model)` | POST + SSE; timeouts 20/180 s; 1 reintento ante 400 |
| `CacheKey.kt` | Clave de caché | campos → SHA-256 | Incluye historial |
| `AnswerCache.kt` | Respuestas reutilizables | `get/put/stats/clear` | 40 entradas, 14 días, `filesDir/studio/cache` |
| `StudioRepository.kt` | Mesas e historial | CRUD + `recordUsage`/`recordCacheHit` + turnos | `mesas.json`, `chats/<mesa>.json`, 80 turnos |
| `ReportWriter.kt` | PDF A4 | `ReportRequest` → `File` | `android.graphics.pdf`; portada, tablas, fuentes, pie |
| `StudyExporter.kt` | Export por respuesta | `texto, formato` → `File` | PDF/MD/TXT + Sharesheet |
| `RagSettingsRepository.kt` | Ajustes | DataStore | base URL, modelo, key, topK |

### 2.2 Presentación y estado (`feature/rag`, 5 archivos)

| Archivo | Responsabilidad |
| --- | --- |
| `StudioViewModel.kt` | Orquesta importación, progreso, caché, generación con streaming, tokens, exportación y CRUD de mesas. ~600 líneas; es el punto más acoplado del sistema. |
| `StudioScreen.kt` | Lista de mesas, detalle, documentos, figuras, estadísticas y progreso. |
| `StudioChat.kt` | Tarjetas de chat, lector a pantalla completa, herramientas, compositor y dictado. |
| `StudioChrome.kt` | Barra superior, diálogos de mesa y de API, errores y utilidades. |
| `VoiceInput.kt` | `SpeechRecognizer` con preferencia offline. |

### 2.3 Modelo (`core/model`)

`Mesa`, `ChatTurn`, `TokenUsage`, `StudioTask`/`StudioTasks`, `RagSettings`, `RagDocument`, `RagChunk`,
`RagSource`, `RagFigure`. **No existe** `Evidence`, `Artifact`, `Collection`, `DocumentVersion`,
`ProcessingJob`, `UsageRecord` ni `ToolRun`.

---

## 3. Flujo actual de importación

1. **UI** — `OpenMultipleDocuments` con tipos `application/pdf` y `application/epub+zip` (`StudioScreen`).
2. **ViewModel** — `importPdfs(uris)`: activa `isImporting`, publica `ImportProgress(libro, total, páginas, nombre)`.
3. **Por archivo** — detecta EPUB por extensión o MIME; ejecuta `PdfExtractor.extract` o `EpubExtractor.extract` en `Dispatchers.IO`.
4. **Texto** — `PdfExtractor` extrae página por página, aplica `TextCleaner.clean` y devuelve páginas limpias + figuras.
5. **Fragmentación** — `TextChunker.chunk(pages, 450, 50)`.
6. **Persistencia** — `RagRepository.import` escribe `doc_<id>.chunks.json`, los JPEG de figuras y `doc_<id>.meta.json`.
7. **Estado** — recarga `documents()` y `figures()`, actualiza contadores y lanza un toast con páginas/fragmentos/figuras.

**Puntos débiles observados**

- Todo ocurre en una corrutina del ViewModel: si el usuario abandona la pantalla, el trabajo se cancela (no hay reanudación).
- No hay hash del archivo: reimportar el mismo PDF crea un documento nuevo (no hay deduplicación).
- No hay estado por documento (`IMPORTED → … → READY`), solo un booleano global de importación.
- Los EPUB no guardan su tipo: la cita dice “p. N” aunque N sea el capítulo.

---

## 4. Flujo actual de consulta

1. **UI** → `ask(prompt)` o `runTask(taskId)` → `generate(task, prompt)`.
2. **Guardas** — mesa activa, no estar generando, `settings.isConfigured` (si no, abre ajustes y explica).
3. **Candidatos** — `rag.chunks(mesaId)`: lee todos los `.chunks.json` de la mesa en cada consulta.
4. **Contexto** — `ContextBuilder.build(query, chunks, maxBlocks = topK, broad = task.broad, documentIds = selección, perDocument = 3 si hay 2+ libros, blockChars = 1200)`.
   - `broad` (resumen, ideas, guion, glosario, preguntas) → cobertura.
   - consulta puntual con 2+ libros → balanceo 3/libro; con 1 libro → top-K BM25.
   - sin coincidencias → cobertura y `weak = true`.
5. **Caché** — `CacheKey.of(modelo, tarea, pregunta, documentos, bloques, historial)` → `AnswerCache.get`.
6. **Generación** — acierto: `ChatReply` con `TokenUsage.Empty`. Fallo de caché: `ChatClient.completeStreaming` con `onDelta` que actualiza `streamingText` cada ~120 ms.
7. **Prompt** — `StudioPrompts.system(mesa, context)` + historial (4 turnos, 1 200 caracteres) + `user(prompt, bloques)`.
8. **Cierre** — guarda dos turnos, `recordUsage` o `recordCacheHit`, recalcula estadísticas de caché y actualiza la mesa.

**Puntos débiles observados**

- El bloque de 450 palabras se **recorta a 1 200 caracteres** con `take(1200)`: la segunda mitad nunca llega al modelo.
- No se incluyen fragmentos vecinos ni se deduplican solapes (el solape de 50 palabras puede aparecer dos veces).
- `RagSource` no lleva puntaje, estrategia ni `chunkId`: no se puede auditar por qué se eligió un pasaje.
- La clave de caché incluye el historial: tras un turno nuevo, una repetición ya no acierta.
- No hay cancelación de la generación en curso (timeout de 180 s sin botón de detener).
- El estado “no hay evidencia suficiente” solo existe como `weak` en el prompt; la UI no lo muestra.

---

## 5. Persistencia y formatos

```
filesDir/rag/<mesaId>/
  doc_<id>.meta.json      { id, mesa, name, pages, chunks, characters, addedAt, figures[] }
  doc_<id>.chunks.json    [ { doc, page, text }, ... ]
  fig_<id>_<n>.jpg

filesDir/studio/
  mesas.json              [ { id, name, subject, context, accent, createdAt, updatedAt,
                              promptTokens, completionTokens, requests, cacheHits, cachedTokensSaved } ]
  chats/<mesaId>.json     [ { id, role, text, taskId, model, promptTokens, completionTokens,
                              elapsed, createdAt, cacheHit, sources[] } ]
  cache/<mesaId>.json     [ { key, text, model, promptTokens, completionTokens, createdAt, sources[] } ]

cacheDir/reports|exports/ PDF, MD, TXT generados
```

**Riesgos de esta persistencia**

- Sin `sha256`: no hay deduplicación ni detección de cambios; reimportar duplica.
- Sin versiones de documento: si el PDF cambia, las citas viejas apuntan a un contenido que ya no existe.
- Sin transacciones: una escritura interrumpida puede dejar un `.meta.json` sin `.chunks.json`.
- `chats/` crece hasta 80 turnos y descarta el resto sin avisar.
- Todo el índice se relee del disco en cada consulta (`rag.chunks`): aceptable hoy, no escala a decenas de libros.

---

## 6. Manejo de credenciales

- `RagSettingsRepository` guarda `rag_api_key` como `stringPreferencesKey` en **Preferences DataStore sin cifrar**.
- La key viaja en `Authorization: Bearer` por HTTPS; las cabeceras de sesión usan un UUID, nunca material de la key.
- **No hay logs de la key**: 0 apariciones de `Log.` en `core/rag`.
- El manifiesto declara `android:allowBackup="true"`, y no hay `backup_rules` que excluya `rag/` ni `studio/`.
- La cadena de la key vive en memoria y en el estado de Compose del diálogo de ajustes.

**Recomendación inmediata (sin backend):** cifrar el valor con una llave de Android Keystore
(`EncryptedSharedPreferences` o AES/GCM con `KeyGenParameterSpec`) y excluir `rag/` y `studio/` del respaldo.
**Recomendación de producto:** si se distribuye fuera del círculo cercano, proxy propio para no repartir claves
de proveedor en el APK ni en cada teléfono.

---

## 7. Pruebas existentes

`95 tests JVM · 0 fallos` (16 suites). Del motor RAG: `CacheKey` 7, `ContextBuilder` 8, `Retriever` 7,
`TextChunker` 6, `TextCleaner` 5, `StudyScenario` 4, `EpubParser` 3, `RagSettings` 4 = **44 tests**.

| Suite | Qué cubre | Qué no cubre |
| --- | --- | --- |
| `RetrieverTest` | ranking, raíces, stopwords, acentos, límite | paráfrasis, sinónimos, puntaje mínimo |
| `ContextBuilderTest` | cobertura, balanceo, presupuesto de caracteres, selección | recorte de bloque, vecinos, deduplicación |
| `TextChunkerTest` | 450 palabras, solape 50, páginas | encabezados de libro, notas al pie |
| `TextCleanerTest` | repetidos, números, guiones, párrafos | libros de 1–2 páginas, columnas dobles |
| `EpubParserTest` | spine, títulos, HTML, entidades | EPUB con `nav`, `NCX`, imágenes, fuentes |
| `CacheKeyTest` | determinismo, normalización, invalidaciones | alcance, versión de índice |
| `StudyScenarioTest` | caso de 8 páginas, respaldo débil, citas | varios libros reales, evidencia tipada |
| Ausentes | — | DAOs/Room, WorkManager, OCR, SSE con proveedor simulado, instrumentadas |

---

## 8. Riesgos priorizados

| # | Riesgo | Evidencia | Mitigación propuesta |
| --- | --- | --- | --- |
| R1 | Contexto incompleto por recorte fijo | `ContextBuilder` `take(blockChars)` | Ventana por oración + presupuesto por tokens (NA-004) |
| R2 | Evidencia no auditable | `RagSource` sin score/chunkId/estrategia | `RetrievedEvidence` + `RetrievalResult` (NA-005) |
| R3 | Trabajo perdido al salir de la pantalla | `viewModelScope.launch` en `importPdfs` | WorkManager reanudable (NA-009) |
| R4 | Key en texto plano y respaldo abierto | `stringPreferencesKey`, `allowBackup=true` | Keystore + reglas de backup |
| R5 | Caché poco efectiva | `CacheKey` incluye historial | Clave normalizada por alcance (NA-006) |
| R6 | Duplicados y citas huérfanas | sin `sha256` ni versión de documento | `document_versions` (NA-007/NA-008) |
| R7 | Sin cancelación de generación | `generate` sin `Job` | `CancellationToken` + botón detener (NA-006) |
| R8 | Prompt inyectable por el documento | texto del libro en el mensaje de usuario | regla explícita + saneo en `StudioPrompts` |
| R9 | Sin deduplicación semántica | solape 50 palabras repetido | dedupe por `contentHash` (NA-010) |
| R10 | Sin OCR | PDFs escaneados quedan fuera | `OcrExtractor` con diagnóstico por página (fase posterior) |

---

## 9. Mapeo actual → arquitectura objetivo

| Objetivo del plan | Hoy | Acción |
| --- | --- | --- |
| `PdfExtractor`, `EpubExtractor` | Existen | **Conservar**; añadir `OcrExtractor` como adaptador |
| `TextCleaner`, `Chunker` | Existen | **Conservar**; envolver en casos de uso |
| `retrieval/BM25` | `Retriever` | **Adaptar** a `RetrievalEngine` con `RetrievalResult` |
| `retrieval/ContextBuilder` | Existe con recorte fijo | **Reescribir** el presupuesto y añadir vecinos/dedupe |
| `ai/ModelGateway` | `ChatClient` directo | **Envolver**: interfaz + adaptador OpenAI-compatible |
| `evidence/CitationResolver` | `RagSource` | **Crear** `EvidenceReference` y validación |
| `database/*` | JSON | **Crear** Room con migraciones (NA-007) |
| `workers/*` | Corrutina en VM | **Crear** WorkManager (NA-009) |
| `agent/*`, `tools/*` | 6 plantillas en el VM | **Crear** contratos y mover las plantillas a herramientas (NA-012/013) |
| `export/*` | `ReportWriter`, `StudyExporter` | **Conservar**; unificar en `Artifact` + DOCX |
| `feature/library`, `document`, `artifacts` | Mesas | **Crear** pantallas; las mesas pasan a `Collection` |
| `di/` | `AppContainer` | **Conservar** (manual) hasta que el grafo crezca |

---

## 10. Estado de los cambios propuestos

| Ticket | Estado | Evidencia |
| --- | --- | --- |
| NA-003 · corpus | **Hecho** | `docs/eval/fixtures` (5 obras, 535 págs, 6.1 MB) + `docs/eval/cases.json` (7 casos) |
| NA-004 · contexto por ventana | **Hecho** | `ContextBuilder.windows`: hasta 2 ventanas por fragmento con peso IDF, vecino al borde y dedupe por n-gramas |
| NA-005 · contrato de evidencia | **Hecho** | `RetrievedEvidence`/`RetrievalResult`, `chunkId` estable, chip “EVIDENCIA DÉBIL” y estrategia en el turno |
| NA-006 · caché y streaming | **Hecho** | Clave con alcance/versión, sin historial en tareas; `REGENERAR` y `DETENER` |
| NA-007 en adelante | Parcial | Huella SHA-256 y versión por documento hechas sin Room; WorkManager y OCR pendientes |

## 10.1 Siguiente conjunto mínimo de cambios

Ordenado para poder cerrar cada ticket con build y pruebas:

1. **NA-003 · Corpus de evaluación.** Seis casos (literal, paráfrasis, libro largo, varios libros, cita impresa ≠ PDF, sin evidencia) con respuesta y páginas esperadas. Sin esto no se puede medir si algo mejora. Especificación en `docs/eval/`.
2. **NA-004 · Contexto por presupuesto.** Guardar el texto completo del bloque, elegir una ventana por oración con mayor densidad de términos, incluir 1 vecino cuando aporte continuidad y deduplicar solapes. Pruebas: libro largo sin pérdida arbitraria; consulta con la respuesta en la segunda mitad; varios libros con distribución equilibrada.
3. **NA-005 · Contrato de evidencia.** `RetrievalResult` con `chunkId`, `documentId`, `title`, `pageStart`, `pageEnd`, `score`, `strategy`; `Context` expone `items`; la UI muestra estrategia y “evidencia débil”. Pruebas: toda cita resuelve a un fragmento existente.
4. **NA-006 · Caché y streaming.** Clave con consulta normalizada, alcance y versión de índice (sin historial para tareas), botón “regenerar” y cancelación del `Job` con cierre de conexión.

Criterio de salida de la Fase 1: los 6 casos del corpus pasan sin empeorar el caso literal, y ninguna cita apunta a un fragmento inexistente.

---

## 11. Decisiones que requieren confirmación

1. **Room vs JSON.** Room resuelve versiones, transacciones y consultas; cuesta migraciones y ~300 KB. El plan lo propone como base; la alternativa es mantener JSON y añadir `sha256` + versiones a mano.
2. **BYOK vs proxy.** Hoy cada usuario aporta su key. Un proxy propio elimina el riesgo de key en el APK, pero exige backend, cuentas y cuotas.
3. **Embeddings.** Modelo multilingüe cuantizado (~25–90 MB) y si se permite su descarga en el primer uso o se empaqueta.
4. **OCR.** ML Kit (on-device, sin costo) o servicio remoto opcional con consentimiento.
5. **DOCX.** Añadir exportador propio o limitar la primera entrega a PDF/Markdown.

---

## 12. Archivos examinados

`core/rag`: AnswerCache, CacheKey, ChatClient, ContextBuilder, EpubExtractor, EpubParser, PdfExtractor,
RagRepository, RagSettingsRepository, ReportWriter, Retriever, StudioPrompts, StudioRepository, StudyExporter,
TextChunker, TextCleaner.
`feature/rag`: StudioChat, StudioChrome, StudioScreen, StudioViewModel, VoiceInput.
`core/model`: Rag.kt, Studio.kt.
Otros: `AppContainer.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`,
`gradle/wrapper/gradle-wrapper.properties`, `.github/workflows/*.yml`, `docs/RAG.md`, `README.md`.