# Nahuanotoka · RAG local sobre PDFs

El objetivo es responder preguntas sobre documentos personales sin subir los documentos a ningún
servidor. El PDF se procesa en el teléfono; **solo la pregunta y los fragmentos recuperados** viajan
a la API configurada.

## Pipeline

```
SAF (OpenDocument)
      │
      ▼
PdfExtractor (PDFBox-Android)     texto por página
      │
      ▼
TextChunker                        fragmentos de ~900 caracteres con traslape
      │
      ▼
filesDir/rag/<id>.chunks.json      índice local
      │
      ▼  (al preguntar)
Retriever (BM25 ligero)            top-k fragmentos
      │
      ▼
ChatClient (POST /chat/completions)
      │
      ▼
Respuesta + fuentes [Documento, p. N]
```

## Componentes

| Clase | Responsabilidad |
| --- | --- |
| `PdfExtractor` | Abre el PDF vía `ContentResolver`, extrae texto página por página, limita a 300 páginas |
| `TextChunker` | Normaliza espacios y parte en fragmentos de 900 caracteres con 140 de traslape, cortando en límites de palabra |
| `Retriever` | Tokeniza (sin acentos ni stopwords), calcula `tf/(tf+1.2) × ln(1 + N/df)` y normaliza por longitud |
| `RagRepository` | Guarda metadatos y fragmentos en `filesDir/rag`, lista y elimina documentos |
| `ChatClient` | Cliente HTTP mínimo compatible con OpenAI; errores 401/404/429 traducidos a mensajes accionables |
| `RagViewModel` | Orquesta importación, retrieval, llamada y estado de la UI |

## Almacenamiento

```
filesDir/rag/
├─ doc_<id>.meta.json     { id, name, pages, chunks, characters, addedAt }
└─ doc_<id>.chunks.json   [ { doc, page, text }, ... ]
```

El nombre visible se obtiene de `OpenableColumns.DISPLAY_NAME`. Borrar un documento elimina ambos
archivos; no se conserva copia del PDF.

## Configuración de la API

Valores por defecto (pantalla de engrane dentro de Nahua IA):

| Campo | Valor |
| --- | --- |
| Base URL | `https://opencode.ai/zen/go/v1` |
| Modelo | `deepseek-v4-pro` |
| Endpoint | `{baseUrl}/chat/completions` |
| Auth | `Authorization: Bearer <api key>` |

Cualquier endpoint compatible funciona (OpenAI, OpenRouter, Ollama, vLLM). Si la base ya termina en
`/chat/completions` no se duplica.

Cuerpo de la petición:

```json
{
  "model": "deepseek-v4-pro",
  "temperature": 0.2,
  "stream": false,
  "messages": [
    { "role": "system", "content": "Eres Nahuanotoka…" },
    { "role": "user", "content": "Contexto extraído… Pregunta: …" }
  ]
}
```

## Privacidad y seguridad

- El PDF, el texto extraído y los fragmentos viven solo en el almacenamiento privado de la app.
- No se envían documentos completos: máximo 4 fragmentos por pregunta.
- La API key se guarda en Preferences DataStore (privado). Para producción se recomienda
  `EncryptedSharedPreferences`/Keystore y rotación de llaves.
- Los errores de red no reintentan automáticamente para no duplicar consumo.

## Límites conocidos

| Límite | Motivo |
| --- | --- |
| 300 páginas por PDF | Tiempo de extracción en dispositivos de gama baja |
| PDFs con texto | Los escaneados requieren OCR previo |
| 4 fragmentos | Coste y ventana de contexto del proveedor |
| Sin historial | Cada pregunta es independiente (v0.2) |

## Diagnóstico

| Síntoma | Causa probable |
| --- | --- |
| "El PDF no tiene texto seleccionable" | Documento escaneado; aplicar OCR |
| 401/403 | API key inválida o sin permisos |
| 404 | Base URL o modelo incorrectos |
| 429 | Límite del proveedor; esperar o cambiar de modelo |

## Tests

- `TextChunkerTest`: tamaños, traslape, páginas y documentos vacíos.
- `RetrieverTest`: ranking, límite, acentos y consultas sin señal.
- `RagSettingsTest`: construcción del endpoint y validación de configuración.