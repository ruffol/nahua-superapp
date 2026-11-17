package com.nahuanotoca.superapp.core.rag

import android.content.Context
import android.graphics.Bitmap
import androidx.room.withTransaction
import com.nahuanotoca.superapp.core.data.library.ChunkEntity
import com.nahuanotoca.superapp.core.data.library.DocumentEntity
import com.nahuanotoca.superapp.core.data.library.EmbeddingEntity
import com.nahuanotoca.superapp.core.data.library.FigureEntity
import com.nahuanotoca.superapp.core.data.library.LibraryDatabase
import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.RagDocument
import com.nahuanotoca.superapp.core.model.RagFigure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Biblioteca local en Room, separada por mesa.
 *
 * Cada mesa guarda sus documentos, fragmentos y figuras en la base `nahua-library.db`; los JPEG de
 * las figuras siguen en `filesDir/rag/<mesa>/`. La API pública es la misma que la versión JSON, así
 * que el ViewModel y el motor no cambiaron.
 *
 * Al abrir una mesa se migra, una sola vez, cualquier índice JSON anterior.
 */
data class StoredEmbeddings(
    val model: String,
    val vectors: Map<String, FloatArray>,
) {
    val isEmpty: Boolean get() = vectors.isEmpty()
}

class RagRepository(private val context: Context) {

    private val database: LibraryDatabase = LibraryDatabase.get(context)
    private val dao = database.libraryDao()

    private val root: File = File(context.filesDir, "rag")
    private val legacyRoot: File = File(context.filesDir, "rag-legacy")
    private val migrated = mutableSetOf<String>()
    private val mutex = Mutex()

    private fun folder(mesaId: String): File = File(root, mesaId).apply { mkdirs() }

    private fun safeId(mesaId: String): String = mesaId.ifBlank { "default" }

    suspend fun documents(mesaId: String): List<RagDocument> {
        val scope = safeId(mesaId)
        migrateIfNeeded(scope)
        return withContext(Dispatchers.IO) {
            dao.documents(scope).map { it.toModel() }
        }
    }

    suspend fun import(
        mesaId: String,
        name: String,
        chunks: List<TextChunker.Chunk>,
        figures: List<ExtractedFigure> = emptyList(),
        sha256: String = "",
    ): RagDocument = withContext(Dispatchers.IO) {
        require(chunks.isNotEmpty()) { "El documento no produjo fragmentos de texto" }

        val scope = safeId(mesaId)
        val id = "doc_" + System.currentTimeMillis().toString(36) + "_" + (0..0xFFFF).random().toString(16)
        val addedAt = System.currentTimeMillis()
        val characters = chunks.sumOf { it.text.length }
        val target = folder(scope)

        val savedFigures = mutableListOf<Pair<String, Int>>()
        figures.forEachIndexed { index, figure ->
            val fileName = "fig_" + id + "_" + index + ".jpg"
            val saved = runCatching {
                FileOutputStream(File(target, fileName)).use { out ->
                    figure.bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
            }.getOrDefault(false)
            if (saved) savedFigures += fileName to figure.page
            figure.bitmap.recycle()
        }

        val previousVersions = dao.documents(scope).count { it.name == name }

        val document = RagDocument(
            id = id,
            mesaId = scope,
            name = name,
            pages = chunks.maxOf { it.page },
            chunks = chunks.size,
            characters = characters,
            addedAt = addedAt,
            figures = savedFigures.size,
            sha256 = sha256,
            version = previousVersions + 1,
        )

        val chunkEntities = chunks.mapIndexed { index, chunk ->
            ChunkEntity(
                id = id + ":" + chunk.page + ":" + CacheKey.sha256(chunk.text).take(12),
                documentId = id,
                mesaId = scope,
                documentName = name,
                page = chunk.page,
                position = index,
                text = chunk.text,
            )
        }

        database.withTransaction {
            dao.insertDocument(document.toEntity())
            dao.insertChunks(chunkEntities)
            if (savedFigures.isNotEmpty()) {
                dao.insertFigures(
                    savedFigures.map { (fileName, page) ->
                        FigureEntity(id = id + ":" + fileName, documentId = id, page = page, fileName = fileName)
                    },
                )
            }
        }

        document
    }

    suspend fun chunks(mesaId: String): List<RagChunk> {
        val scope = safeId(mesaId)
        migrateIfNeeded(scope)
        return withContext(Dispatchers.IO) {
            dao.chunks(scope).map { chunk ->
                RagChunk(
                    id = chunk.id,
                    documentId = chunk.documentId,
                    documentName = chunk.documentName,
                    page = chunk.page,
                    text = chunk.text,
                )
            }
        }
    }

    suspend fun chunksOf(documentId: String): List<RagChunk> = withContext(Dispatchers.IO) {
        dao.chunksOf(documentId).map { chunk ->
            RagChunk(
                id = chunk.id,
                documentId = chunk.documentId,
                documentName = chunk.documentName,
                page = chunk.page,
                text = chunk.text,
            )
        }
    }

    /** Guarda los vectores de un documento (uno por fragmento). */
    suspend fun saveEmbeddings(
        mesaId: String,
        model: String,
        items: List<Pair<String, FloatArray>>,
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val scope = safeId(mesaId)
        dao.insertEmbeddings(
            items.map { (chunkId, vector) ->
                EmbeddingEntity(
                    chunkId = chunkId,
                    mesaId = scope,
                    model = model,
                    dim = vector.size,
                    vector = VectorCodec.encode(vector),
                )
            },
        )
        Unit
    }

    /** Vectores de la mesa, si ya se generaron. */
    suspend fun embeddings(mesaId: String): StoredEmbeddings = withContext(Dispatchers.IO) {
        val rows = dao.embeddings(safeId(mesaId))
        StoredEmbeddings(
            model = rows.firstOrNull()?.model.orEmpty(),
            vectors = rows.associate { it.chunkId to VectorCodec.decode(it.vector) },
        )
    }

    suspend fun figures(mesaId: String): List<RagFigure> {
        val scope = safeId(mesaId)
        migrateIfNeeded(scope)
        return withContext(Dispatchers.IO) {
            val names = dao.documents(scope).associate { it.id to it.name }
            val target = folder(scope)
            dao.figures(scope).mapNotNull { figure ->
                val file = File(target, figure.fileName)
                if (!file.exists()) return@mapNotNull null
                RagFigure(
                    documentId = figure.documentId,
                    documentName = names[figure.documentId] ?: "Documento",
                    page = figure.page,
                    path = file.absolutePath,
                )
            }
        }
    }

    suspend fun delete(mesaId: String, id: String) = withContext(Dispatchers.IO) {
        val scope = safeId(mesaId)
        val target = folder(scope)
        dao.figuresOf(id).forEach { figure ->
            runCatching { File(target, figure.fileName).delete() }
        }
        dao.deleteDocument(id)
        Unit
    }

    suspend fun deleteAll(mesaId: String) = withContext(Dispatchers.IO) {
        val scope = safeId(mesaId)
        dao.deleteMesa(scope)
        folder(scope).deleteRecursively()
        migrated += scope
        Unit
    }

    suspend fun findByHash(mesaId: String, sha256: String): RagDocument? {
        if (sha256.isBlank()) return null
        val scope = safeId(mesaId)
        migrateIfNeeded(scope)
        return withContext(Dispatchers.IO) { dao.findByHash(scope, sha256)?.toModel() }
    }

    /** ¿Quedan documentos del formato anterior (sin mesas) en la raíz? */
    fun hasLegacyDocuments(): Boolean =
        root.listFiles { file -> file.name.endsWith(META_SUFFIX) }?.isNotEmpty() == true

    /** Mueve los documentos previos a la primera mesa para no perderlos al actualizar. */
    suspend fun migrateLegacy(mesaId: String): Int = withContext(Dispatchers.IO) {
        val target = folder(safeId(mesaId))
        val legacy = root.listFiles { file ->
            file.isFile && (file.name.endsWith(META_SUFFIX) || file.name.endsWith(CHUNKS_SUFFIX))
        } ?: return@withContext 0

        var moved = 0
        legacy.forEach { file ->
            val destination = File(target, file.name)
            if (destination.exists()) destination.delete()
            if (file.renameTo(destination)) moved++
        }
        if (moved > 0) {
            migrated -= safeId(mesaId)
            migrateIfNeeded(safeId(mesaId))
        }
        moved
    }

    /**
     * Migra el índice JSON de una mesa a Room una sola vez. Los JSON se apartan en
     * `filesDir/rag-legacy`; las figuras se quedan donde están para no romper rutas.
     */
    private suspend fun migrateIfNeeded(mesaId: String) = mutex.withLock {
        if (migrated.contains(mesaId)) return
        val folder = File(root, mesaId)
        val metas: Array<File> = folder.listFiles { file -> file.name.endsWith(META_SUFFIX) } ?: emptyArray()
        if (metas.isEmpty()) {
            migrated += mesaId
            return
        }

        withContext(Dispatchers.IO) {
            for (metaFile in metas) {
                val legacy = runCatching { LegacyLibrary.parseMeta(metaFile.readText()) }.getOrNull()
                if (legacy == null) continue
                if (dao.document(legacy.id) != null) continue

                val chunkFile = File(folder, legacy.id + CHUNKS_SUFFIX)
                val chunks = if (chunkFile.exists()) {
                    runCatching { LegacyLibrary.parseChunks(chunkFile.readText(), legacy.id) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                runCatching {
                    database.withTransaction {
                        dao.insertDocument(
                            DocumentEntity(
                                id = legacy.id,
                                mesaId = mesaId,
                                name = legacy.name,
                                sha256 = legacy.sha256,
                                version = legacy.version,
                                pages = legacy.pages,
                                chunkCount = if (chunks.isNotEmpty()) chunks.size else legacy.chunks,
                                characters = legacy.characters,
                                addedAt = legacy.addedAt,
                                figureCount = legacy.figures.size,
                            ),
                        )
                        if (chunks.isNotEmpty()) {
                            dao.insertChunks(
                                chunks.mapIndexed { index, chunk ->
                                    ChunkEntity(
                                        id = chunk.id,
                                        documentId = legacy.id,
                                        mesaId = mesaId,
                                        documentName = legacy.name,
                                        page = chunk.page,
                                        position = index,
                                        text = chunk.text,
                                    )
                                },
                            )
                        }
                        if (legacy.figures.isNotEmpty()) {
                            dao.insertFigures(
                                legacy.figures.map { figure ->
                                    FigureEntity(
                                        id = legacy.id + ":" + figure.fileName,
                                        documentId = legacy.id,
                                        page = figure.page,
                                        fileName = figure.fileName,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Apartar los JSON migrados (las figuras se quedan).
            val archive = File(legacyRoot, mesaId)
            archive.mkdirs()
            metas.forEach { metaFile ->
                runCatching { metaFile.renameTo(File(archive, metaFile.name)) }
                val chunkFile = File(folder, metaFile.name.removeSuffix(META_SUFFIX) + CHUNKS_SUFFIX)
                if (chunkFile.exists()) {
                    runCatching { chunkFile.renameTo(File(archive, chunkFile.name)) }
                }
            }
        }
        migrated += mesaId
    }

    private fun RagDocument.toEntity(): DocumentEntity = DocumentEntity(
        id = id,
        mesaId = mesaId,
        name = name,
        sha256 = sha256,
        version = version,
        pages = pages,
        chunkCount = chunks,
        characters = characters,
        addedAt = addedAt,
        figureCount = figures,
    )

    private fun DocumentEntity.toModel(): RagDocument = RagDocument(
        id = id,
        mesaId = mesaId,
        name = name,
        pages = pages,
        chunks = chunkCount,
        characters = characters,
        addedAt = addedAt,
        figures = figureCount,
        sha256 = sha256,
        version = version,
    )

    private companion object {
        const val META_SUFFIX = ".meta.json"
        const val CHUNKS_SUFFIX = ".chunks.json"
    }
}