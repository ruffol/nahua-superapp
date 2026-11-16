package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.StudioTask

/**
 * Prompts del estudio. El objetivo pedagógico manda: el modelo no es un oráculo, es un asistente
 * que debe citar la página, separar lo que dice el documento de lo que infiere, y dejar al
 * estudiante con material que pueda defender en una exposición.
 */
object StudioPrompts {

    private const val BASE = "Eres Nahuanotoka, el asistente de estudio de un estudiante mexicano. " +
        "Respondes SIEMPRE en español claro, directo y sin relleno. " +
        "Reglas que no puedes romper: (1) usas únicamente el contexto entregado; " +
        "(2) cada afirmación tomada del contexto lleva su cita [Documento, p. N]; " +
        "(3) si algo no está en el contexto lo dices con claridad y propones qué documento revisar; " +
        "(4) nunca inventas cifras, fechas, autores ni citas; " +
        "(5) separas explícitamente lo que dice la fuente de lo que es inferencia tuya; " +
        "(6) escribes en Markdown con encabezados, listas y tablas cuando ayudan a estudiar."

    fun system(mesa: Mesa, context: ContextBuilder.Context): String = buildString {
        appendLine(BASE)
        appendLine()
        appendLine("Mesa de trabajo: " + mesa.name + if (mesa.subject.isBlank()) "" else " (" + mesa.subject + ")")
        if (mesa.context.isNotBlank()) {
            appendLine("Contexto que dio el estudiante para esta mesa: " + mesa.context)
        }
        appendLine("Documentos disponibles en esta consulta: " + context.documentCount + ".")
        if (context.weak) {
            appendLine(
                "Aviso: la búsqueda no encontró coincidencias literales con la pregunta. El contexto " +
                    "son los fragmentos más representativos de los documentos. Empieza tu respuesta " +
                    "diciendo qué sí cubre el material y qué no, y no inventes respuestas.",
            )
        }
    }

    fun user(
        task: StudioTask?,
        question: String,
        context: ContextBuilder.Context,
    ): String = buildString {
        appendLine("Contexto extraído de mis documentos:")
        if (context.blocks.isEmpty()) {
            appendLine("(sin fragmentos disponibles)")
        } else {
            context.blocks.forEachIndexed { index, block ->
                appendLine()
                appendLine("[Fragmento " + (index + 1) + "]")
                appendLine(block)
            }
        }
        appendLine()
        if (task != null) {
            appendLine("Tarea: " + question)
            appendLine()
            appendLine("Formato requerido: " + task.instruction)
        } else {
            appendLine("Pregunta: " + question)
            appendLine()
            append("Responde citando [Documento, p. N] cuando uses el contexto.")
        }
    }

    /**
     * Historial que se reenvía al modelo. Se recortan turnos largos para no inflar el costo: cada
     * turno del asistente entra como máximo con [maxChars] caracteres.
     */
    fun history(
        turns: List<com.nahuanotoca.superapp.core.model.ChatTurn>,
        maxTurns: Int = 4,
        maxChars: Int = 1200,
    ): List<ChatMessage> {
        if (turns.isEmpty()) return emptyList()
        return turns
            .filter { it.text.isNotBlank() }
            .takeLast(maxTurns)
            .map { turn ->
                val role = if (turn.role == com.nahuanotoca.superapp.core.model.ChatRole.USER) "user" else "assistant"
                val body = if (turn.text.length > maxChars) turn.text.take(maxChars) + "…" else turn.text
                ChatMessage(role, body)
            }
    }
}
