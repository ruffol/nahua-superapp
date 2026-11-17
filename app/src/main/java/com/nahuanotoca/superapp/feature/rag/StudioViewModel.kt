package com.nahuanotoca.superapp.feature.rag

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.model.ChatRole
import com.nahuanotoca.superapp.core.model.ChatTurn
import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagDocument
import com.nahuanotoca.superapp.core.model.RagFigure
import com.nahuanotoca.superapp.core.model.RagSettings
import com.nahuanotoca.superapp.core.model.StudioTask
import com.nahuanotoca.superapp.core.model.StudioTasks
import com.nahuanotoca.superapp.core.model.TokenUsage
import com.nahuanotoca.superapp.core.rag.AnswerCache
import com.nahuanotoca.superapp.core.rag.CacheKey
import com.nahuanotoca.superapp.core.rag.CacheStats
import com.nahuanotoca.superapp.core.rag.CachedAnswer
import com.nahuanotoca.superapp.core.rag.ChatException
import com.nahuanotoca.superapp.core.rag.ChatMessage
import com.nahuanotoca.superapp.core.rag.ChatReply
import com.nahuanotoca.superapp.core.rag.ExportFormat
import com.nahuanotoca.superapp.core.rag.ContextBuilder
import com.nahuanotoca.superapp.core.rag.ReportRequest
import com.nahuanotoca.superapp.core.rag.StudioPrompts
import com.nahuanotoca.superapp.core.rag.TextChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Eventos al borde de la app: snackbars y el intent de compartir el PDF. */
sealed class StudioEvent {
    data class Toast(val text: String) : StudioEvent()
    data class SharePdf(val path: String, val title: String) : StudioEvent()
    data class ShareFile(val path: String, val title: String, val format: ExportFormat) : StudioEvent()
}

/** Progreso visible mientras se indexan varios libros. */
data class ImportProgress(
    val book: Int,
    val books: Int,
    val pages: Int,
    val name: String,
)

data class StudioUiState(
    val settings: RagSettings = RagSettings(),
    val mesas: List<Mesa> = emptyList(),
    val activeMesaId: String? = null,
    val documents: List<RagDocument> = emptyList(),
    val figures: List<RagFigure> = emptyList(),
    val documentCounts: Map<String, Int> = emptyMap(),
    val cacheStats: CacheStats = CacheStats(),
    val streamingText: String = "",
    val importProgress: ImportProgress? = null,
    val turns: List<ChatTurn> = emptyList(),
    val selectedDocumentIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val isWorking: Boolean = false,
    val workingLabel: String = "",
    val pendingTurn: ChatTurn? = null,
    val error: String? = null,
    val settingsOpen: Boolean = false,
    val editorOpen: Boolean = false,
    val editingMesa: Mesa? = null,
) {
    val activeMesa: Mesa? get() = mesas.firstOrNull { it.id == activeMesaId }
    val isConfigured: Boolean get() = settings.isConfigured
}

/**
 * Orquesta las mesas de trabajo: importar PDFs, generar análisis con las acciones de estudio,
 * correlacionar documentos, medir tokens y exportar el PDF.
 */
class StudioViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StudioEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<StudioEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            container.ragSettings.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            container.studio.mesas.collect { mesas ->
                _state.update { current ->
                    val refreshed = mesas.firstOrNull { it.id == current.activeMesaId }
                    current.copy(mesas = mesas, editingMesa = refreshed ?: current.editingMesa)
                }
            }
        }
        bootstrap()
    }

    /**
     * Primera carga. Si existen documentos del formato anterior (sin mesas) se crea una mesa con
     * ellos. Si es la primera vez que abre la app, se crea una mesa de demostración para Colima.
     */
    private fun bootstrap() {
        viewModelScope.launch {
            val loaded = container.studio.load()
            if (loaded.isEmpty()) {
                if (container.rag.hasLegacyDocuments()) {
                    val mesa = container.studio.create(
                        name = "Mis documentos",
                        subject = "General",
                        context = "PDFs importados antes de que existieran las mesas de trabajo.",
                    )
                    container.rag.migrateLegacy(mesa.id)
                    _events.emit(StudioEvent.Toast("Tus PDFs anteriores ahora viven en la mesa \"Mis documentos\"."))
                } else {
                    val demoMesa = container.studio.create(
                        name = "Historia y Geografía de Colima",
                        subject = "Estudio Regional",
                        context = "Mesa demostrativa para estudiar la historia, volcanes, tianguis y geografía de Colima con Nahuanotoka.",
                    )
                    val demoChunks = listOf(
                        TextChunker.Chunk(
                            page = 1,
                            text = "El estado de Colima se ubica en la costa centro-occidental del Pacífico mexicano. Con una extensión de 5,627 km², destaca por su alta biodiversidad, zonas arqueológicas de El Chanal y La Campana, y su cercanía al complejo volcánico más activo de México.",
                        ),
                        TextChunker.Chunk(
                            page = 2,
                            text = "El Volcán de Colima o Volcán de Fuego (3,860 msnm) y el Nevado de Colima (4,260 msnm) configuran el paisaje geográfico del norte estatal. Sus suelos de ceniza volcánica favorecen la agricultura de café de altura en Comala y Suchitlán, así como la caña y frutales.",
                        ),
                        TextChunker.Chunk(
                            page = 3,
                            text = "Los tianguis tradicionales en Colima y Villa de Álvarez preservan el comercio comunitario desde la época virreinal. Se intercambian productos emblemáticos como la tuba con cacahuate, tamales de ceniza, quesos de montaña, limones de Tecomán y sal de mar de Cuyutlán.",
                        ),
                        TextChunker.Chunk(
                            page = 4,
                            text = "El puerto de Manzanillo es la capital económica y marítima del estado, liderando el movimiento de carga contenerizada en el Pacífico mexicano, conectando rutas comerciales internacionales y preservando importantes atractivos turísticos en Santiago y Miramar.",
                        ),
                    )
                    runCatching {
                        container.rag.import(demoMesa.id, "Guia_Colima_Patrimonio.pdf", demoChunks)
                    }
                }
            }
            val perMesa = counts(container.studio.mesas.value)
            _state.update { it.copy(isLoading = false, documentCounts = perMesa) }
        }
    }

    /** Cuántos PDFs tiene cada mesa, para mostrarlo en la lista sin abrirla. */
    private suspend fun counts(mesas: List<Mesa>): Map<String, Int> = withContext(Dispatchers.IO) {
        mesas.associate { mesa -> mesa.id to container.rag.documents(mesa.id).size }
    }

    // --- navegación entre mesas -------------------------------------------

    fun openMesa(mesaId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(activeMesaId = mesaId, error = null, selectedDocumentIds = emptySet(), turns = emptyList())
            }
            val documents = container.rag.documents(mesaId)
            val figures = container.rag.figures(mesaId)
            val turns = container.studio.turns(mesaId)
            val cache = container.answerCache.stats(mesaId)
            _state.update { it.copy(documents = documents, figures = figures, turns = turns, cacheStats = cache) }
        }
    }

    fun closeMesa() = _state.update {
        it.copy(
            activeMesaId = null,
            documents = emptyList(),
            figures = emptyList(),
            turns = emptyList(),
            selectedDocumentIds = emptySet(),
            cacheStats = CacheStats(),
        )
    }

    fun openEditor(mesa: Mesa? = null) = _state.update { it.copy(editorOpen = true, editingMesa = mesa, error = null) }

    fun closeEditor() = _state.update { it.copy(editorOpen = false, editingMesa = null) }

    fun saveMesa(name: String, subject: String, context: String) {
        val editing = _state.value.editingMesa
        viewModelScope.launch {
            if (editing == null) {
                val mesa = container.studio.create(name, subject, context)
                _state.update {
                    it.copy(editorOpen = false, editingMesa = null, documentCounts = it.documentCounts + (mesa.id to 0))
                }
                openMesa(mesa.id)
                _events.emit(StudioEvent.Toast("Mesa creada. Agrega tus PDFs."))
            } else {
                container.studio.update(editing.copy(name = name, subject = subject, context = context))
                _state.update { it.copy(editorOpen = false, editingMesa = null) }
                _events.emit(StudioEvent.Toast("Mesa actualizada"))
            }
        }
    }

    fun deleteMesa(mesaId: String) {
        viewModelScope.launch {
            container.rag.deleteAll(mesaId)
            container.studio.delete(mesaId)
            _state.update { it.copy(documentCounts = it.documentCounts - mesaId) }
            if (_state.value.activeMesaId == mesaId) closeMesa()
            _events.emit(StudioEvent.Toast("Mesa eliminada"))
        }
    }

    // --- documentos -------------------------------------------------------

    /** Importa uno o varios PDFs de golpe (el selector del sistema permite multi-selección). */
    fun importPdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val mesaId = _state.value.activeMesaId
        if (mesaId == null) {
            _state.update { it.copy(error = "Crea una mesa antes de agregar PDFs.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }

            var indexed = 0
            var failed = 0
            var pageCount = 0
            var chunkCount = 0
            var figureCount = 0
            var lastError: String? = null

            uris.forEachIndexed { index, uri ->
                val name = resolveDisplayName(uri)
                _state.update {
                    it.copy(
                        importProgress = ImportProgress(
                            book = index + 1,
                            books = uris.size,
                            pages = pageCount,
                            name = name,
                        ),
                    )
                }
                val isEpub = name.endsWith(".epub", ignoreCase = true) ||
                    container.appContext.contentResolver.getType(uri)?.contains("epub", ignoreCase = true) == true
                runCatching {
                    withContext(Dispatchers.IO) {
                        val document = if (isEpub) {
                            container.epubExtractor.extract(uri)
                        } else {
                            container.pdfExtractor.extract(uri)
                        }
                        Triple(document, TextChunker.chunk(document.pages), name)
                    }
                }.onSuccess { (pdf, chunks, displayName) ->
                    if (chunks.isEmpty()) {
                        failed++
                        lastError = "\"" + displayName + "\" no tiene texto seleccionable (parece escaneado). " +
                            "Pásalo por OCR y vuelve a intentarlo."
                    } else {
                        runCatching {
                            container.rag.import(mesaId, displayName, chunks, pdf.figures)
                        }.onSuccess { document ->
                            indexed++
                            pageCount += pdf.totalPages
                            chunkCount += chunks.size
                            figureCount += document.figures
                        }.onFailure { failure ->
                            failed++
                            lastError = "No pudimos guardar " + displayName + ": " +
                                (failure.message ?: "error desconocido")
                        }
                    }
                }.onFailure { failure ->
                    failed++
                    lastError = "No pudimos leer el PDF: " + (failure.message ?: "error desconocido")
                }
            }

            val documents = container.rag.documents(mesaId)
            val figures = container.rag.figures(mesaId)
            _state.update {
                it.copy(
                    documents = documents,
                    figures = figures,
                    isImporting = false,
                    importProgress = null,
                    documentCounts = it.documentCounts + (mesaId to documents.size),
                    error = if (indexed == 0 && lastError != null) lastError else null,
                )
            }

            val summary = buildString {
                if (indexed > 0) {
                    append(indexed).append(if (indexed == 1) " PDF indexado" else " PDFs indexados")
                    append(": ").append(pageCount).append(" páginas, ").append(chunkCount).append(" fragmentos")
                    if (figureCount > 0) append(", ").append(figureCount).append(" figuras")
                }
                if (failed > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(failed).append(if (failed == 1) " archivo falló" else " archivos fallaron")
                }
            }
            if (summary.isNotBlank()) _events.emit(StudioEvent.Toast(summary))
        }
    }

    fun deleteDocument(documentId: String) {
        val mesaId = _state.value.activeMesaId ?: return
        viewModelScope.launch {
            container.rag.delete(mesaId, documentId)
            val documents = container.rag.documents(mesaId)
            val figures = container.rag.figures(mesaId)
            _state.update {
                it.copy(
                    documents = documents,
                    figures = figures,
                    selectedDocumentIds = it.selectedDocumentIds - documentId,
                    documentCounts = it.documentCounts + (mesaId to documents.size),
                )
            }
            _events.emit(StudioEvent.Toast("Documento eliminado"))
        }
    }

    fun toggleDocumentSelection(documentId: String) = _state.update {
        val selected = if (documentId in it.selectedDocumentIds) {
            it.selectedDocumentIds - documentId
        } else {
            it.selectedDocumentIds + documentId
        }
        it.copy(selectedDocumentIds = selected)
    }

    fun clearSelection() = _state.update { it.copy(selectedDocumentIds = emptySet()) }

    // --- generación -------------------------------------------------------

    fun runTask(taskId: String) {
        val task = StudioTasks.find(taskId) ?: return
        generate(task, task.prompt)
    }

    fun ask(question: String) {
        val prompt = question.trim()
        if (prompt.length < 3) return
        generate(null, prompt)
    }

    /** Mensaje accionable cuando el proveedor falla: nunca se inventa una respuesta local. */
    private fun apiErrorMessage(failure: Throwable?): String = when {
        failure == null -> "La API no respondió. Revisa tu conexión y la configuración."
        failure is ChatException -> failure.message ?: "La API rechazó la solicitud."
        else -> failure.message ?: "No pudimos conectar con la API."
    }

    private fun generate(task: StudioTask?, prompt: String) {
        val mesa = _state.value.activeMesa
        if (mesa == null) {
            _state.update { it.copy(error = "Abre o crea una mesa de trabajo.") }
            return
        }
        val current = _state.value
        if (current.isWorking) return
        if (!current.settings.isConfigured) {
            _state.update {
                it.copy(
                    settingsOpen = true,
                    error = "Nahuanotoka analiza sólo con la API: configura la URL, el modelo y tu API key.",
                )
            }
            return
        }
        val startedAt = System.currentTimeMillis()
        val userTurn = ChatTurn(
            id = newId("turn"),
            role = ChatRole.USER,
            text = prompt,
            taskId = task?.id,
            createdAt = startedAt,
        )
        _state.update {
            it.copy(
                isWorking = true,
                error = null,
                streamingText = "",
                pendingTurn = userTurn,
                workingLabel = if (task != null) "Preparando " + task.label.lowercase() else "Leyendo y respondiendo",
            )
        }

        viewModelScope.launch {
            try {
                val chunks = container.rag.chunks(mesa.id)
                if (chunks.isEmpty()) {
                    _state.update { it.copy(error = "Esta mesa no tiene PDFs indexados todavía.") }
                    return@launch
                }

                val correlation = task?.id == StudioTasks.CORRELATE
                val scope = if (correlation) current.selectedDocumentIds else emptySet()
                if (correlation && scope.size == 1) {
                    _state.update {
                        it.copy(error = "Para correlacionar elige dos documentos o deja la selección vacía.")
                    }
                    return@launch
                }

                val context = ContextBuilder.build(
                    query = prompt,
                    chunks = chunks,
                    maxBlocks = current.settings.topK.coerceIn(3, 14),
                    broad = task?.broad ?: false,
                    documentIds = scope,
                    // Con varios libros: los 3 mejores fragmentos de cada uno (máx. 12 bloques).
                    perDocument = if (current.documents.size >= 2) 3 else 0,
                    blockChars = 1_200,
                )
                if (context.isEmpty) {
                    _state.update { it.copy(error = "No hay texto suficiente en esta mesa para responder.") }
                    return@launch
                }

                val messages = buildList {
                    add(ChatMessage.system(StudioPrompts.system(mesa, context)))
                    addAll(StudioPrompts.history(current.turns))
                    add(ChatMessage.user(StudioPrompts.user(task, prompt, context)))
                }

                // Acierto de caché: mismo modelo, tarea, pregunta, documentos e historial.
                val historyDigest = StudioPrompts.history(current.turns).map { it.role + ":" + it.content }
                val cacheKey = CacheKey.of(
                    model = current.settings.model,
                    taskId = task?.id,
                    prompt = prompt,
                    documentIds = scope.ifEmpty { current.documents.map { it.id }.toSet() },
                    contextBlocks = context.blocks,
                    history = historyDigest,
                )
                val cached = container.answerCache.get(mesa.id, cacheKey)

                val reply: ChatReply
                if (cached != null) {
                    reply = ChatReply(
                        text = cached.text,
                        usage = TokenUsage.Empty,
                        model = cached.model.ifBlank { current.settings.model },
                    )
                } else {
                    val buffer = StringBuilder()
                    var lastPush = 0L
                    val result = container.chatClient.completeStreaming(
                        settings = current.settings,
                        messages = messages,
                    ) { delta ->
                        buffer.append(delta)
                        val now = System.currentTimeMillis()
                        if (now - lastPush > 120) {
                            lastPush = now
                            val snapshot = buffer.toString()
                            _state.update { it.copy(streamingText = snapshot) }
                        }
                    }
                    val remote = result.getOrNull()
                    if (remote == null) {
                        val failure = result.exceptionOrNull()
                        val status = (failure as? ChatException)?.statusCode
                        _state.update {
                            it.copy(
                                error = apiErrorMessage(failure),
                                streamingText = "",
                                settingsOpen = status == 401 || status == 403,
                            )
                        }
                        return@launch
                    }
                    reply = remote
                }

                val cacheHit = cached != null
                val assistantTurn = ChatTurn(
                    id = newId("turn"),
                    role = ChatRole.ASSISTANT,
                    text = reply.text,
                    taskId = task?.id,
                    model = reply.model,
                    usage = if (cacheHit) TokenUsage.Empty else reply.usage,
                    sources = context.sources,
                    elapsedMillis = System.currentTimeMillis() - startedAt,
                    createdAt = System.currentTimeMillis(),
                    cacheHit = cacheHit,
                    strategy = context.mode,
                    weak = context.weak,
                )
                container.studio.appendTurn(mesa.id, userTurn)
                container.studio.appendTurn(mesa.id, assistantTurn)

                if (cacheHit && cached != null) {
                    container.studio.recordCacheHit(mesa.id, cached.totalTokens)
                    _events.emit(
                        StudioEvent.Toast(
                            "Acierto de caché: 0 tokens (ahorraste " + cached.totalTokens + ")",
                        ),
                    )
                } else {
                    container.studio.recordUsage(mesa.id, reply.usage)
                    container.answerCache.put(
                        mesa.id,
                        CachedAnswer(
                            key = cacheKey,
                            text = reply.text,
                            model = reply.model,
                            promptTokens = reply.usage.promptTokens,
                            completionTokens = reply.usage.completionTokens,
                            sources = context.sources,
                        ),
                    )
                }

                val cacheStats = container.answerCache.stats(mesa.id)
                _state.update {
                    it.copy(
                        turns = it.turns + userTurn + assistantTurn,
                        pendingTurn = null,
                        isWorking = false,
                        workingLabel = "",
                        streamingText = "",
                        cacheStats = cacheStats,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.message ?: "Ocurrió un error al procesar")
                }
            } finally {
                _state.update { it.copy(isWorking = false, workingLabel = "", pendingTurn = null) }
            }
        }
    }

    // --- exportación ------------------------------------------------------

    /** Baja una sola respuesta como PDF, Markdown o texto. */
    fun exportTurn(turnId: String, format: ExportFormat) {
        val mesa = _state.value.activeMesa ?: return
        val turn = _state.value.turns.firstOrNull { it.id == turnId }
        if (turn == null || turn.text.isBlank()) {
            viewModelScope.launch { _events.emit(StudioEvent.Toast("Esa respuesta está vacía")) }
            return
        }
        val task = StudioTasks.find(turn.taskId)
        val title = (task?.label ?: "Respuesta") + " · " + mesa.name
        viewModelScope.launch {
            runCatching {
                container.studyExporter.write(
                    title = title,
                    body = turn.text,
                    format = format,
                    model = turn.model,
                    mesaName = mesa.name,
                    sources = turn.sources,
                    usage = turn.usage,
                )
            }.onSuccess { file ->
                _events.emit(StudioEvent.ShareFile(file.absolutePath, title, format))
            }.onFailure { failure ->
                _state.update { it.copy(error = "No pudimos exportar: " + (failure.message ?: "error")) }
            }
        }
    }

    fun clearCache() {
        val mesaId = _state.value.activeMesaId ?: return
        viewModelScope.launch {
            container.answerCache.clear(mesaId)
            _state.update { it.copy(cacheStats = CacheStats()) }
            _events.emit(StudioEvent.Toast("Caché de la mesa vaciada"))
        }
    }

    fun exportPdf() {
        val mesa = _state.value.activeMesa ?: return
        val assistantTurns = _state.value.turns.filter { it.role == ChatRole.ASSISTANT && it.text.isNotBlank() }
        if (assistantTurns.isEmpty()) {
            viewModelScope.launch { _events.emit(StudioEvent.Toast("Genera un análisis antes de exportar el PDF")) }
            return
        }

        val last = assistantTurns.last()
        val lastTask = StudioTasks.find(last.taskId)
        val title = (lastTask?.label ?: "Análisis") + " · " + mesa.name
        val body = if (assistantTurns.size == 1) {
            last.text
        } else {
            buildString {
                assistantTurns.forEach { turn ->
                    val label = StudioTasks.find(turn.taskId)?.label ?: "Respuesta"
                    appendLine("## " + label)
                    appendLine()
                    appendLine(turn.text)
                    appendLine()
                }
            }
        }

        viewModelScope.launch {
            runCatching {
                container.reportWriter.write(
                    ReportRequest(
                        mesaName = mesa.name,
                        subject = mesa.subject,
                        mesaContext = mesa.context,
                        title = title,
                        body = body,
                        model = _state.value.settings.model,
                        usage = mesa.usage,
                        documents = _state.value.documents.map { it.name },
                        sources = assistantTurns.flatMap { it.sources }.distinctBy { it.documentName to it.page },
                    ),
                )
            }.onSuccess { file ->
                _events.emit(StudioEvent.SharePdf(file.absolutePath, title))
            }.onFailure { failure ->
                _state.update { it.copy(error = "No pudimos crear el PDF: " + (failure.message ?: "error")) }
            }
        }
    }

    // --- ajustes y utilidades --------------------------------------------

    fun openSettings() = _state.update { it.copy(settingsOpen = true, error = null) }

    fun closeSettings() = _state.update { it.copy(settingsOpen = false) }

    fun saveSettings(baseUrl: String, model: String, apiKey: String) {
        viewModelScope.launch {
            val current = _state.value.settings
            container.ragSettings.save(
                RagSettings(
                    baseUrl = baseUrl.ifBlank { RagSettings.DEFAULT_BASE_URL },
                    model = model.ifBlank { RagSettings.DEFAULT_MODEL },
                    apiKey = apiKey,
                    topK = current.topK,
                ),
            )
            _state.update { it.copy(settingsOpen = false) }
            _events.emit(
                StudioEvent.Toast(
                    if (apiKey.isBlank()) "Guardado, pero falta la API key" else "Configuración guardada",
                ),
            )
        }
    }

    fun clearChat() {
        val mesaId = _state.value.activeMesaId ?: return
        viewModelScope.launch {
            container.studio.clearTurns(mesaId)
            _state.update { it.copy(turns = emptyList()) }
            _events.emit(StudioEvent.Toast("Conversación borrada"))
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun resolveDisplayName(uri: Uri): String {
        val resolver = container.appContext.contentResolver
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: "documento.pdf"
    }

    private fun newId(prefix: String): String =
        prefix + "_" + System.currentTimeMillis().toString(36) + "_" + (0..0xFFFF).random().toString(16)
}