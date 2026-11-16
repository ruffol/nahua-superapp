package com.nahuanotoca.superapp.feature.rag

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahuanotoca.superapp.core.AppContainer
import com.nahuanotoca.superapp.core.model.RagAnswer
import com.nahuanotoca.superapp.core.model.RagDocument
import com.nahuanotoca.superapp.core.model.RagSettings
import com.nahuanotoca.superapp.core.model.RagSource
import com.nahuanotoca.superapp.core.rag.Retriever
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

data class RagUiState(
    val settings: RagSettings = RagSettings(),
    val documents: List<RagDocument> = emptyList(),
    val isLoadingDocuments: Boolean = true,
    val isImporting: Boolean = false,
    val isAsking: Boolean = false,
    val answer: RagAnswer? = null,
    val error: String? = null,
    val settingsOpen: Boolean = false,
)

class RagViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(RagUiState())
    val state: StateFlow<RagUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            container.ragSettings.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        refreshDocuments()
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            val documents = container.rag.documents()
            _state.update { it.copy(documents = documents, isLoadingDocuments = false) }
        }
    }

    fun openSettings() = _state.update { it.copy(settingsOpen = true, error = null) }

    fun closeSettings() = _state.update { it.copy(settingsOpen = false) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun saveSettings(baseUrl: String, model: String, apiKey: String) {
        viewModelScope.launch {
            container.ragSettings.save(
                RagSettings(
                    baseUrl = baseUrl.ifBlank { RagSettings.DEFAULT_BASE_URL },
                    model = model.ifBlank { RagSettings.DEFAULT_MODEL },
                    apiKey = apiKey,
                ),
            )
            _state.update { it.copy(settingsOpen = false) }
            if (apiKey.isBlank()) {
                _messages.emit("Guardado, pero falta la API key")
            } else {
                _messages.emit("Configuración guardada")
            }
        }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            val name = resolveDisplayName(uri)
            runCatching {
                withContext(Dispatchers.IO) {
                    val pdf = container.pdfExtractor.extract(uri)
                    pdf to TextChunker.chunk(pdf.pages)
                }
            }
                .onSuccess { (pdf, chunks) ->
                    if (chunks.isEmpty()) {
                        _state.update {
                            it.copy(
                                error = "El PDF no tiene texto seleccionable (parece escaneado). " +
                                    "Sube una versión con OCR.",
                            )
                        }
                    } else {
                        container.rag.import(name, chunks)
                        _state.update { it.copy(documents = container.rag.documents()) }
                        val extra = if (pdf.truncated) " (primeras 300 páginas)" else ""
                        _messages.emit("PDF indexado: " + name + extra)
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(error = "No pudimos leer el PDF: " + (failure.message ?: "error desconocido"))
                    }
                }
            _state.update { it.copy(isImporting = false) }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            container.rag.delete(id)
            _state.update { it.copy(documents = container.rag.documents()) }
            _messages.emit("Documento eliminado")
        }
    }

    fun ask(question: String) {
        val prompt = question.trim()
        if (prompt.length < 3) return

        val current = _state.value
        if (!current.settings.isConfigured) {
            _state.update {
                it.copy(settingsOpen = true, error = "Configura tu endpoint, modelo y API key para preguntar.")
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isAsking = true, error = null, answer = null) }
            val startedAt = System.currentTimeMillis()
            try {
                val chunks = container.rag.chunks()
                if (chunks.isEmpty()) {
                    _state.update { it.copy(error = "Primero importa un PDF para poder consultarlo.") }
                    return@launch
                }

                val selected = Retriever.select(prompt, chunks, current.settings.topK)
                if (selected.isEmpty()) {
                    _state.update {
                        it.copy(error = "No encontré pasajes relevantes en tus documentos para esa pregunta.")
                    }
                    return@launch
                }

                val contextBlocks = selected.map { scored ->
                    "[" + scored.chunk.documentName + ", p. " + scored.chunk.page + "] " + scored.chunk.text
                }

                container.chatClient.ask(current.settings, prompt, contextBlocks)
                    .onSuccess { text ->
                        _state.update {
                            it.copy(
                                answer = RagAnswer(
                                    text = text,
                                    sources = selected.map { scored ->
                                        RagSource(
                                            documentName = scored.chunk.documentName,
                                            page = scored.chunk.page,
                                            snippet = scored.chunk.text.take(160),
                                        )
                                    },
                                    model = current.settings.model,
                                    elapsedMillis = System.currentTimeMillis() - startedAt,
                                ),
                            )
                        }
                    }
                    .onFailure { failure ->
                        _state.update { it.copy(error = failure.message ?: "La API no respondió") }
                    }
            } finally {
                _state.update { it.copy(isAsking = false) }
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val resolver = container.appContext.contentResolver
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: "documento.pdf"
    }
}