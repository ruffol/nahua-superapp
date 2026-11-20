package com.nahuanotoca.superapp.core.miniapp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Una ROM importada al almacenamiento privado de la app. */
data class RomFile(
    val name: String,
    val displayName: String,
    val sizeBytes: Long,
)

/**
 * Almacenamiento privado de ROMs (filesDir/roms). La mini-app nunca toca archivos externos: el
 * usuario elige la ROM con el selector del sistema (SAF), el host la copia aqui y se la sirve
 * por el handler /rom/ del WebViewAssetLoader con una URL https, sin file:// ni permisos.
 *
 * La copia es idempotente: el nombre final incluye un sha8 del contenido, asi reimportar la
 * misma ROM no duplica el archivo ni pierde las partidas asociadas en IndexedDB (que EmulatorJS
 * keyfica por nombre de juego).
 *
 * Dos familias de consola conviven en la misma carpeta y las distingue la extension del archivo:
 * Game Boy Advance (.gba/.agb/.bin) y Game Boy / Color (.gbc/.gb). La mini-app elige el nucleo
 * con esa misma extension, asi que el nombre final SIEMPRE conserva una extension conocida.
 */
class RomImportException(val code: String) : Exception(code) {

    companion object {
        const val UNSUPPORTED = "unsupported_rom"
        const val TOO_LARGE = "too_large"
        const val READ_FAILED = "read_failed"
        const val WRITE_FAILED = "write_failed"
        const val CANCELLED = "cancelled"
    }
}

object RomStore {

    /** Tope de tamano: la ROM de cartucho GBA mas grande ronda los 32 MB; dejamos 64 por holgura. */
    const val MAX_ROM_BYTES: Long = 64L * 1024 * 1024
    const val ROM_DIR = "roms"

    /** Extension canonica de Game Boy Advance, la que se usa cuando el origen trae un alias. */
    const val EXTENSION = ".gba"

    private val ALLOWED_EXTENSIONS = setOf("gba", "agb", "bin", "gbc", "gb")

    /**
     * Alias de extension: .agb y .bin son Game Boy Advance con otro nombre; .gbc y .gb son la
     * misma consola (gambatte detecta el modo por la cabecera del cartucho) y se conservan tal
     * cual, porque el nombre del archivo es lo que elige el nucleo en la mini-app.
     */
    private fun canonicalExtension(extension: String): String? = when (extension) {
        "gba", "agb", "bin" -> "gba"
        "gbc" -> "gbc"
        "gb" -> "gb"
        else -> null
    }

    fun directory(context: Context): File = File(context.filesDir, ROM_DIR)

    /** URL https que el WebViewAssetLoader responde con la ROM ya copiada. */
    fun url(name: String): String = MiniAppManifest.ASSET_ORIGIN + "rom/" + name

    suspend fun import(context: Context, resolver: ContentResolver, uri: Uri, displayName: String? = null): RomFile =
        withContext(Dispatchers.IO) {
            val rawName = displayName ?: queryDisplayName(resolver, uri)
                ?: uri.lastPathSegment ?: "rom"
            if (!isAllowedExtension(extensionOf(rawName))) {
                throw RomImportException(RomImportException.UNSUPPORTED)
            }

            val temp = File(directory(context), "import.tmp")
            val sha256 = try {
                resolver.openInputStream(uri)?.use { input ->
                    // Streaming + sha256 en una sola pasada: nunca queda un archivo parcial servible.
                    copyWithDigest(input, temp)
                } ?: throw RomImportException(RomImportException.READ_FAILED)
            } catch (importFailure: RomImportException) {
                temp.delete()
                throw importFailure
            } catch (ioFailure: IOException) {
                temp.delete()
                throw RomImportException(RomImportException.READ_FAILED)
            } catch (securityFailure: SecurityException) {
                temp.delete()
                throw RomImportException(RomImportException.CANCELLED)
            }

            val finalName = romFileName(rawName, sha256)
            val target = File(directory(context), finalName)
            // Rename in-place: idempotente (reimportar la misma ROM pisa el mismo nombre).
            if (!temp.renameTo(target)) {
                if (target.isFile) {
                    temp.delete()
                } else {
                    temp.delete()
                    throw RomImportException(RomImportException.WRITE_FAILED)
                }
            }

            RomFile(
                name = finalName,
                displayName = displayOf(rawName),
                sizeBytes = target.length(),
            )
        }

    fun list(context: Context): List<RomFile> {
        val root = directory(context)
        val files = root.listFiles { file -> file.isFile && isAllowedExtension(extensionOf(file.name)) }
            ?: return emptyList()
        return files
            .sortedByDescending { file -> file.lastModified() }
            .map { file -> RomFile(name = file.name, displayName = displayOf(file.name), sizeBytes = file.length()) }
    }

    fun find(context: Context, name: String): RomFile? {
        if (!isSafeName(name)) return null
        val file = File(directory(context), name)
        if (!file.isFile) return null
        return RomFile(name = file.name, displayName = displayOf(file.name), sizeBytes = file.length())
    }

    fun delete(context: Context, name: String): Boolean {
        if (!isSafeName(name)) return false
        val file = File(directory(context), name)
        return file.isFile && file.delete()
    }

    /**
     * Nombre final: <slug>-<sha8>.<extension canonica>. La extension se quita ANTES de sanitizar
     * (mayusculas incluidas) y se vuelve a poner al final, ya normalizada.
     */
    fun romFileName(rawName: String, sha256Hex: String): String {
        val extension = canonicalExtension(extensionOf(rawName)) ?: "gba"
        return sanitizeName(rawName.substringBeforeLast('.')) + "-" + hashSuffix(sha256Hex) + "." + extension
    }

    /**
     * Slug de solo [a-z0-9-]. NFD + quitar marcas combinantes deja "pokemon" de "Pokemon";
     * lo demas se colapsa a "-" y las rutas nunca sobreviven.
     */
    fun sanitizeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val stripped = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        val slug = stripped
            .replace(Regex("[^\\p{L}\\p{Nd}]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .lowercase()
        return slug.ifEmpty { "rom" }
    }

    fun extensionOf(rawName: String): String =
        rawName.substringAfterLast('.', "").lowercase()

    fun isAllowedExtension(extension: String): Boolean = extension in ALLOWED_EXTENSIONS

    fun isAllowedSize(sizeBytes: Long): Boolean = sizeBytes in 1..MAX_ROM_BYTES

    fun hashSuffix(sha256Hex: String): String = sha256Hex.take(8).lowercase()

    fun isSafeName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\\') &&
            !name.contains("..") && isAllowedExtension(extensionOf(name))

    /** Titulo legible a partir del nombre de archivo, sin hash ni extension. */
    private fun displayOf(name: String): String {
        val stem = name
            .replace(Regex("\\.(gba|gbc|gb)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("-[0-9a-f]{8}$"), "")
        val spaced = stem.replace('-', ' ').trim()
        return spaced.replaceFirstChar { it.uppercase() }.ifEmpty { "ROM" }
    }

    /** Nombre legible del documento SAF; null si el provider no lo informa. */
    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /**
     * Copia por bloques con tope de tamano y huella SHA-256 en la misma pasada. La huella se
     * calcula inline (y no con [com.nahuanotoca.superapp.core.util.Digest]) porque el tope de
     * 64 MiB debe abortar la copia a mitad de streaming, no despues de leer todo.
     */
    private fun copyWithDigest(input: InputStream, target: File): String {
        target.parentFile?.mkdirs()
        val output: OutputStream = target.outputStream()
        val hex = StringBuilder()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_ROM_BYTES) {
                    throw RomImportException(RomImportException.TOO_LARGE)
                }
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
            output.flush()
            if (total == 0L) throw RomImportException(RomImportException.READ_FAILED)
            digest.digest().forEach { byte -> hex.append("%02x".format(byte)) }
        } finally {
            output.close()
        }
        return hex.toString()
    }
}
