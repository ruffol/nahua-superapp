package com.nahuanotoca.superapp.core.data.library

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Biblioteca local en Room: documentos, fragmentos y figuras.
 *
 * Sustituye el índice JSON manteniendo la misma API pública en `RagRepository`. Las figuras siguen
 * siendo archivos JPEG; aquí sólo se guarda su metadato.
 */
@Entity(
    tableName = "documents",
    indices = [Index("mesaId"), Index(value = ["mesaId", "sha256"])],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val mesaId: String,
    val name: String,
    val sha256: String,
    val version: Int,
    val pages: Int,
    val chunkCount: Int,
    val characters: Int,
    val addedAt: Long,
    val figureCount: Int,
)

@Entity(
    tableName = "chunks",
    primaryKeys = ["id"],
    indices = [Index("mesaId"), Index("documentId")],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChunkEntity(
    val id: String,
    val documentId: String,
    val mesaId: String,
    val documentName: String,
    val page: Int,
    val position: Int,
    val text: String,
)

@Entity(
    tableName = "figures",
    primaryKeys = ["id"],
    indices = [Index("documentId")],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FigureEntity(
    val id: String,
    val documentId: String,
    val page: Int,
    val fileName: String,
)

@Entity(
    tableName = "chunk_embeddings",
    primaryKeys = ["chunkId"],
    indices = [Index("mesaId"), Index("chunkId")],
    foreignKeys = [
        ForeignKey(
            entity = ChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EmbeddingEntity(
    val chunkId: String,
    val mesaId: String,
    val model: String,
    val dim: Int,
    val vector: ByteArray,
)

@Dao
interface LibraryDao {

    @Query("SELECT * FROM documents WHERE mesaId = :mesaId ORDER BY addedAt DESC")
    suspend fun documents(mesaId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun document(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE mesaId = :mesaId AND sha256 = :sha256 LIMIT 1")
    suspend fun findByHash(mesaId: String, sha256: String): DocumentEntity?

    @Query("SELECT * FROM chunks WHERE mesaId = :mesaId ORDER BY documentId, position")
    suspend fun chunks(mesaId: String): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY position")
    suspend fun chunksOf(documentId: String): List<ChunkEntity>

    @Query("SELECT * FROM figures WHERE documentId = :documentId ORDER BY page")
    suspend fun figuresOf(documentId: String): List<FigureEntity>

    @Query(
        "SELECT * FROM figures WHERE documentId IN (SELECT id FROM documents WHERE mesaId = :mesaId) " +
            "ORDER BY documentId, page",
    )
    suspend fun figures(mesaId: String): List<FigureEntity>

    @Query("SELECT * FROM chunk_embeddings WHERE mesaId = :mesaId")
    suspend fun embeddings(mesaId: String): List<EmbeddingEntity>

    @Query("SELECT COUNT(*) FROM chunk_embeddings WHERE mesaId = :mesaId")
    suspend fun embeddingCount(mesaId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<EmbeddingEntity>)

    @Query("DELETE FROM chunk_embeddings WHERE mesaId = :mesaId")
    suspend fun deleteEmbeddings(mesaId: String)

    @Query("SELECT COUNT(*) FROM documents WHERE mesaId = :mesaId")
    suspend fun count(mesaId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFigures(figures: List<FigureEntity>)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM documents WHERE mesaId = :mesaId")
    suspend fun deleteMesa(mesaId: String)
}

@Database(
    entities = [DocumentEntity::class, ChunkEntity::class, FigureEntity::class, EmbeddingEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    companion object {
        /** Añade la tabla de embeddings sin perder documentos ni fragmentos. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS chunk_embeddings (" +
                        "chunkId TEXT NOT NULL, mesaId TEXT NOT NULL, model TEXT NOT NULL, " +
                        "dim INTEGER NOT NULL, vector BLOB NOT NULL, PRIMARY KEY(chunkId), " +
                        "FOREIGN KEY(chunkId) REFERENCES chunks(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chunk_embeddings_mesaId ON chunk_embeddings(mesaId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chunk_embeddings_chunkId ON chunk_embeddings(chunkId)")
            }
        }

        @Volatile
        private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                "nahua-library.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}