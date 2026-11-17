package com.nahuanotoca.superapp.feature.rag

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.model.ChatRole
import com.nahuanotoca.superapp.core.model.ChatTurn
import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.RagDocument
import com.nahuanotoca.superapp.core.model.RagFigure
import com.nahuanotoca.superapp.core.model.RagSource
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
import com.nahuanotoca.superapp.core.work.ImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val stage: String = "",
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

    private var generationJob: Job? = null
    private var importJob: Job? = null
    @Volatile private var cancelling: Boolean = false

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
            observeImports(mesaId)
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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(container.appContext).cancelUniqueWork(ImportWorker.uniqueName(mesaId))
            }
        }
        viewModelScope.launch {
            container.rag.deleteAll(mesaId)
            container.studio.delete(mesaId)
            _state.update { it.copy(documentCounts = it.documentCounts - mesaId) }
            if (_state.value.activeMesaId == mesaId) closeMesa()
            _events.emit(StudioEvent.Toast("Mesa eliminada"))
        }
    }

    // --- documentos -------------------------------------------------------

    /**
     * Importa uno o varios archivos encolando un worker por archivo en WorkManager. El trabajo
     * sobrevive a cerrar la app, se reintenta si el sistema lo interrumpe y muestra progreso por
     * libro.
     */
    fun importPdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val mesaId = _state.value.activeMesaId
        if (mesaId == null) {
            _state.update { it.copy(error = "Crea una mesa antes de agregar PDFs.") }
            return
        }

        val workManager = WorkManager.getInstance(container.appContext)
        val requests = uris.mapIndexed { index, uri ->
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(
                    workDataOf(
                        ImportWorker.KEY_MESA to mesaId,
                        ImportWorker.KEY_URI to uri.toString(),
                        ImportWorker.KEY_NAME to resolveDisplayName(uri),
                        ImportWorker.KEY_INDEX to (index + 1),
                        ImportWorker.KEY_TOTAL to uris.size,
                    ),
                )
                .addTag("nahua-import")
                .build()
        }

        // El permiso temporal del selector se pierde si el proceso muere mientras el worker corre:
        // se toma permiso persistente para que WorkManager pueda reabrir el archivo después.
        uris.forEach { uri ->
            runCatching {
                container.appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        _state.update {
            it.copy(
                isImporting = true,
                error = null,
                importProgress = ImportProgress(1, uris.size, 0, "En cola…"),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                var continuation = workManager.beginUniqueWork(
                    ImportWorker.uniqueName(mesaId),
                    ExistingWorkPolicy.REPLACE,
                    requests.first(),
                )
                requests.drop(1).forEach { request ->
                    continuation = continuation.then(request)
                }
                continuation.enqueue()
            }.onFailure { failure ->
                _state.update {
                    it.copy(
                        isImporting = false,
                        importProgress = null,
                        error = "No pudimos encolar la importación: " + (failure.message ?: "error"),
                    )
                }
            }
        }
        observeImports(mesaId)
    }

    /** Observa la cadena de importación de una mesa y refleja el progreso en la UI. */
    fun observeImports(mesaId: String) {
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            var reported = false
            WorkManager.getInstance(container.appContext)
                .getWorkInfosForUniqueWorkFlow(ImportWorker.uniqueName(mesaId))
                .collect { infos ->
                    if (infos.isEmpty()) {
                        _state.update { it.copy(isImporting = false, importProgress = null) }
                        return@collect
                    }

                    val total = infos.size
                    val finished = infos.count { it.state.isFinished }
                    val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    val progress = running?.progress
                    val currentName = progress?.getString(ImportWorker.KEY_NAME).orEmpty()
                    val currentIndex = progress?.getInt(ImportWorker.KEY_INDEX, finished + 1) ?: (finished + 1)
                    val currentStage = progress?.getString(ImportWorker.KEY_STAGE).orEmpty()
                    val pages = infos.sumOf { it.outputData.getInt(ImportWorker.KEY_PAGES, 0) }

                    _state.update {
                        it.copy(
                            isImporting = finished < total,
                            importProgress = if (finished < total) {
                                ImportProgress(
                                    book = currentIndex.coerceIn(1, total),
                                    books = total,
                                    pages = pages,
                                    name = currentName.ifBlank { "Procesando…" },
                                    stage = currentStage,
                                )
                            } else {
                                null
                            },
                        )
                    }

                    if (finished < total) {
                        reported = false
                        return@collect
                    }
                    if (reported) return@collect
                    reported = true

                    val indexed = infos.count { it.outputData.getBoolean(ImportWorker.KEY_INDEXED, false) }
                    val duplicates = infos.count { it.outputData.getBoolean(ImportWorker.KEY_DUPLICATE, false) }
                    val chunks = infos.sumOf { it.outputData.getInt(ImportWorker.KEY_CHUNKS, 0) }
                    val figures = infos.sumOf { it.outputData.getInt(ImportWorker.KEY_FIGURES, 0) }
                    val failure = infos
                        .asSequence()
                        .mapNotNull { it.outputData.getString(ImportWorker.KEY_ERROR) }
                        .firstOrNull()

                    val documents = container.rag.documents(mesaId)
                    val mesaFigures = container.rag.figures(mesaId)
                    _state.update {
                        it.copy(
                            documents = documents,
                            figures = mesaFigures,
                            isImporting = false,
                            importProgress = null,
                            documentCounts = it.documentCounts + (mesaId to documents.size),
                            error = if (indexed == 0 && failure != null) failure else it.error,
                        )
                    }

                    val summary = buildString {
                        if (indexed > 0) {
                            append(indexed).append(if (indexed == 1) " archivo indexado" else " archivos indexados")
                            append(": ").append(pages).append(" páginas, ").append(chunks).append(" fragmentos")
                            if (figures > 0) append(", ").append(figures).append(" figuras")
                        }
                        if (duplicates > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(duplicates).append(if (duplicates == 1) " ya estaba" else " ya estaban")
                        }
                        if (indexed == 0 && duplicates == 0 && failure != null) {
                            if (isNotEmpty()) append(" · ")
                            append("falló la importación")
                        }
                    }
                    if (summary.isNotBlank()) _events.emit(StudioEvent.Toast(summary))
                }
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

    private fun generate(task: StudioTask?, prompt: String, ignoreCache: Boolean = false) {
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
        cancelling = false
        _state.update {
            it.copy(
                isWorking = true,
                error = null,
                streamingText = "",
                pendingTurn = userTurn,
                workingLabel = if (task != null) "Preparando " + task.label.lowercase() else "Leyendo y respondiendo",
            )
        }

        generationJob = viewModelScope.launch {
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

                // Comparación en dos pasadas: una ficha por obra y luego el cruce.
                if (correlation && task != null) {
                    val documentsInScope = current.documents.filter { scope.isEmpty() || it.id in scope }
                    if (documentsInScope.size >= 2) {
                        runTwoPass(
                            mesa = mesa,
                            task = task,
                            prompt = prompt,
                            current = current,
                            chunks = chunks,
                            documents = documentsInScope,
                            userTurn = userTurn,
                            startedAt = startedAt,
                            ignoreCache = ignoreCache,
                        )
                        return@launch
                    }
                }

                val documentCount = current.documents.size
                val context = ContextBuilder.build(
                    query = prompt,
                    chunks = chunks,
                    maxBlocks = current.settings.topK.coerceIn(3, 14),
                    broad = task?.broad ?: false,
                    documentIds = scope,
                    // Varios libros: al menos 2 fragmentos por obra; con 5 obras son 10 bloques.
                    perDocument = if (documentCount >= 2) {
                        (12 / documentCount).coerceIn(2, 3)
                    } else {
                        0
                    },
                    blockChars = 1_000,
                    maxChars = if (task?.id == StudioTasks.CORRELATE) 16_000 else 14_000,
                )
                if (context.isEmpty) {
                    _state.update { it.copy(error = "No hay texto suficiente en esta mesa para responder.") }
                    return@launch
                }

                val messages = buildList {
                    add(ChatMessage.system(StudioPrompts.system(mesa, context, task?.id)))
                    addAll(StudioPrompts.history(current.turns))
                    add(ChatMessage.user(StudioPrompts.user(task, prompt, context)))
                }

                // Acierto de caché. En tareas de estudio el historial no forma parte de la clave:
                // repetir "resumen" debe acertar aunque después se hayan hecho otras consultas.
                val historyDigest = if (task == null) {
                    StudioPrompts.history(current.turns).map { it.role + ":" + it.content }
                } else {
                    emptyList()
                }
                val indexVersion = current.documents.size.toString() + ":" + chunks.size
                val cacheKey = CacheKey.of(
                    model = current.settings.model,
                    taskId = task?.id,
                    prompt = prompt,
                    documentIds = scope.ifEmpty { current.documents.map { it.id }.toSet() },
                    contextBlocks = context.blocks,
                    history = historyDigest,
                    indexVersion = indexVersion,
                    scope = scope.sorted().joinToString(","),
                )
                val cached = if (ignoreCache) null else container.answerCache.get(mesa.id, cacheKey)

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
                        if (cancelling) return@launch
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
                if (!cancelling) {
                    _state.update {
                        it.copy(error = e.message ?: "Ocurrió un error al procesar")
                    }
                }
            } finally {
                cancelling = false
                generationJob = null
                _state.update {
                    it.copy(
                        isWorking = false,
                        workingLabel = "",
                        pendingTurn = null,
                        streamingText = "",
                    )
                }
            }
        }
    }

    /** Detiene la generación en curso y corta la conexión con el proveedor. */
    fun cancelGeneration() {
        if (!_state.value.isWorking) return
        cancelling = true
        container.chatClient.cancelActive()
        generationJob?.cancel()
        generationJob = null
        _state.update {
            it.copy(isWorking = false, workingLabel = "", pendingTurn = null, streamingText = "")
        }
        viewModelScope.launch { _events.emit(StudioEvent.Toast("Generación detenida")) }
    }

    /** Repite la última petición ignorando la caché. */
    fun regenerateLast() {
        val lastUser = _state.value.turns.lastOrNull { it.role == ChatRole.USER } ?: return
        if (_state.value.isWorking) return
        generate(StudioTasks.find(lastUser.taskId), lastUser.text, ignoreCache = true)
    }

    /**
     * Comparación en dos pasadas: primero una ficha por obra (una llamada barata por libro) y
     * después una sola llamada que cruza las fichas. Cuesta más tokens que una consulta normal,
     * pero evita que el modelo resuma cada libro en lugar de compararlos.
     */
    private suspend fun runTwoPass(
        mesa: Mesa,
        task: StudioTask,
        prompt: String,
        current: StudioUiState,
        chunks: List<RagChunk>,
        documents: List<RagDocument>,
        userTurn: ChatTurn,
        startedAt: Long,
        ignoreCache: Boolean,
    ) {
        val indexVersion = documents.size.toString() + ":" + chunks.size
        val cacheKey = CacheKey.of(
            model = current.settings.model,
            taskId = task.id,
            prompt = prompt,
            documentIds = documents.map { it.id }.toSet(),
            contextBlocks = listOf("dos-pasadas", documents.joinToString(",") { it.id }),
            history = emptyList(),
            indexVersion = indexVersion,
            scope = "twopass",
        )

        val cached = if (ignoreCache) null else container.answerCache.get(mesa.id, cacheKey)
        if (cached != null) {
            val turn = ChatTurn(
                id = newId("turn"),
                role = ChatRole.ASSISTANT,
                text = cached.text,
                taskId = task.id,
                model = cached.model.ifBlank { current.settings.model },
                usage = TokenUsage.Empty,
                sources = cached.sources,
                elapsedMillis = System.currentTimeMillis() - startedAt,
                createdAt = System.currentTimeMillis(),
                cacheHit = true,
                strategy = "dos-pasadas",
                weak = false,
            )
            container.studio.appendTurn(mesa.id, userTurn)
            container.studio.appendTurn(mesa.id, turn)
            container.studio.recordCacheHit(mesa.id, cached.totalTokens)
            val stats = container.answerCache.stats(mesa.id)
            _state.update {
                it.copy(
                    turns = it.turns + userTurn + turn,
                    streamingText = "",
                    cacheStats = stats,
                )
            }
            _events.emit(StudioEvent.Toast("Acierto de caché: 0 tokens (ahorraste " + cached.totalTokens + ")"))
            return
        }

        val digests = mutableListOf<Pair<String, String>>()
        val sources = mutableListOf<RagSource>()
        var usage = TokenUsage.Empty

        for ((index, document) in documents.withIndex()) {
            if (cancelling) return
            _state.update {
                it.copy(
                    workingLabel = "Comparando " + (index + 1) + "/" + documents.size + " · " +
                        ContextBuilder.labelOf(document.name),
                )
            }

            val documentChunks = chunks.filter { it.documentId == document.id }
            val context = if (documentChunks.isEmpty()) {
                null
            } else {
                ContextBuilder.build(
                    query = prompt,
                    chunks = documentChunks,
                    maxBlocks = 4,
                    broad = true,
                    blockChars = 1_200,
                    maxChars = 6_000,
                )
            }

            if (context == null || context.isEmpty) {
                digests += document.name to "No se pronuncia sobre el tema."
                continue
            }

            sources += context.sources
            val messages = listOf(
                ChatMessage.system(StudioPrompts.digestSystem()),
                ChatMessage.user(StudioPrompts.digestUser(mesa, document.name, prompt, context.blocks)),
            )
            val result = container.chatClient.complete(current.settings, messages)
            val reply = result.getOrNull()
            if (reply == null) {
                if (cancelling) return
                _state.update { it.copy(error = apiErrorMessage(result.exceptionOrNull())) }
                return
            }
            usage = usage.plus(reply.usage)
            digests += document.name to reply.text
        }

        if (digests.size < 2) {
            _state.update { it.copy(error = "No hay fichas suficientes para comparar: faltan fragmentos.") }
            return
        }

        _state.update { it.copy(workingLabel = "Redactando la comparación") }
        val messages = listOf(
            ChatMessage.system(StudioPrompts.comparisonSystem(mesa)),
            ChatMessage.user(StudioPrompts.comparisonUser(task, digests)),
        )

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

        val reply = result.getOrNull()
        if (reply == null) {
            if (!cancelling) {
                _state.update { it.copy(error = apiErrorMessage(result.exceptionOrNull())) }
            }
            return
        }
        usage = usage.plus(reply.usage)

        val turn = ChatTurn(
            id = newId("turn"),
            role = ChatRole.ASSISTANT,
            text = reply.text,
            taskId = task.id,
            model = reply.model,
            usage = usage,
            sources = sources.distinctBy { it.documentName to it.page },
            elapsedMillis = System.currentTimeMillis() - startedAt,
            createdAt = System.currentTimeMillis(),
            strategy = "dos-pasadas",
            weak = false,
        )

        container.studio.appendTurn(mesa.id, userTurn)
        container.studio.appendTurn(mesa.id, turn)
        container.studio.recordUsage(mesa.id, usage)
        container.answerCache.put(
            mesa.id,
            CachedAnswer(
                key = cacheKey,
                text = reply.text,
                model = reply.model,
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                sources = turn.sources,
            ),
        )

        val stats = container.answerCache.stats(mesa.id)
        _state.update {
            it.copy(
                turns = it.turns + userTurn + turn,
                pendingTurn = null,
                streamingText = "",
                cacheStats = stats,
            )
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