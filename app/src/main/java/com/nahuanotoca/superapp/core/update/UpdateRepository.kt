package com.nahuanotoca.superapp.core.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateRelease(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val release: UpdateRelease) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/**
 * Checks GitHub Releases for a newer APK and downloads it. The release must contain an .apk asset;
 * the digest is verified when GitHub exposes it.
 */
class UpdateRepository(private val context: Context) {

    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0.0.0"

    @Suppress("DEPRECATION")
    fun currentVersionCode(): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0L)

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Nahua-App-Updater")
            }
            try {
                val status = connection.responseCode
                val body = when {
                    status in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                    else -> connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                if (status == 404) {
                    return@withContext UpdateCheck.Failed("Aún no hay versiones publicadas en GitHub.")
                }
                if (status !in 200..299) {
                    return@withContext UpdateCheck.Failed("GitHub respondió " + status + ". Intenta más tarde.")
                }

                val json = JSONObject(body)
                val tag = json.optString("tag_name").removePrefix("v").ifBlank { "0.0.0" }
                val notes = json.optString("body").trim()
                val assets = json.optJSONArray("assets")

                var apkUrl: String? = null
                var size = 0L
                var sha: String? = null
                if (assets != null) {
                    for (index in 0 until assets.length()) {
                        val asset = assets.optJSONObject(index) ?: continue
                        if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                        apkUrl = asset.optString("browser_download_url")
                        size = asset.optLong("size", 0L)
                        sha = asset.optString("digest")
                            .takeIf { it.startsWith("sha256:") }
                            ?.removePrefix("sha256:")
                        break
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    return@withContext UpdateCheck.Failed("La última versión no incluye un APK.")
                }

                if (Version.compare(tag, currentVersionName()) > 0) {
                    UpdateCheck.Available(
                        UpdateRelease(
                            versionName = tag,
                            notes = notes,
                            apkUrl = apkUrl,
                            sizeBytes = size,
                            sha256 = sha,
                        ),
                    )
                } else {
                    UpdateCheck.UpToDate
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            UpdateCheck.Failed("No pudimos consultar GitHub: " + (error.message ?: "sin conexión"))
        }
    }

    suspend fun download(
        release: UpdateRelease,
        onProgress: (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "nahua-" + release.versionName + ".apk")

        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Nahua-App-Updater")
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("La descarga falló con HTTP " + status)

            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress(((written * 100) / total).toInt())
                    }
                }
            }
            onProgress(100)

            val expected = release.sha256
            if (!expected.isNullOrBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    target.delete()
                    throw IOException("El APK descargado no coincide con la firma publicada.")
                }
            }
            target
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/ruffol/nahua-superapp/releases/latest"
    }
}