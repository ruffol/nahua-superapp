package com.nahuanotoca.superapp.core.rag

import java.security.MessageDigest

/**
 * Clave de caché de una respuesta. Es determinista: mismo modelo, misma tarea, misma pregunta,
 * mismos documentos y mismo historial producen la misma clave. Si cambia cualquier cosa, se pide de
 * nuevo a la API.
 *
 * Puro Kotlin para poder probarlo sin Android.
 */
object CacheKey {

    fun of(
        model: String,
        taskId: String?,
        prompt: String,
        documentIds: Set<String>,
        contextBlocks: List<String>,
        history: List<String> = emptyList(),
    ): String {
        val canonical = buildString {
            append(model.trim().lowercase()).append('|')
            append(taskId.orEmpty()).append('|')
            append(prompt.trim().lowercase()).append('|')
            append(documentIds.sorted().joinToString(",")).append('|')
            append(contextBlocks.joinToString("\u001E")).append('|')
            append(history.joinToString("\u001E"))
        }
        return sha256(canonical)
    }

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
            "%02x".format(byte)
        }
    }
}
