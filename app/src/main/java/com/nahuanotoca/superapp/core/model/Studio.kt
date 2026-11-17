package com.nahuanotoca.superapp.core.model

/**
 * Cuántos tokens consumió una petición. Se muestra por respuesta y acumulado por mesa, para que el
 * estudiante sepa cuánto contexto está pagando antes de quedarse sin cuota.
 */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
) {
    val totalTokens: Int get() = promptTokens + completionTokens

    fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
    )

    companion object {
        val Empty: TokenUsage = TokenUsage()
    }
}

/**
 * Una mesa de trabajo = una materia (o una exposición concreta). Agrupa sus PDFs, el contexto que
 * escribió el estudiante y el consumo de tokens. El estudiante crea tantas mesas como necesite.
 */
data class Mesa(
    val id: String,
    val name: String,
    val subject: String = "",
    val context: String = "",
    val accent: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val usage: TokenUsage = TokenUsage.Empty,
    val requests: Int = 0,
    val cacheHits: Int = 0,
    val cachedTokensSaved: Int = 0,
) {
    val title: String get() = if (subject.isBlank()) name else name + " · " + subject
}

enum class ChatRole { USER, ASSISTANT }

/** Un turno de la conversación de una mesa. Los turnos se guardan en disco por mesa. */
data class ChatTurn(
    val id: String,
    val role: ChatRole,
    val text: String,
    val taskId: String? = null,
    val model: String = "",
    val usage: TokenUsage = TokenUsage.Empty,
    val sources: List<RagSource> = emptyList(),
    val elapsedMillis: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val cacheHit: Boolean = false,
    val strategy: String = "",
    val weak: Boolean = false,
)

/**
 * Acciones de estudio predefinidas. Cada una cambia el contexto que se manda (cobertura completa
 * vs. búsqueda puntual) y el formato de salida esperado.
 */
data class StudioTask(
    val id: String,
    val label: String,
    val hint: String,
    val instruction: String,
    val prompt: String,
    val broad: Boolean = true,
)

object StudioTasks {

    const val SUMMARY = "resumen"
    const val IDEAS = "ideas"
    const val CORRELATE = "correlacion"
    const val PRESENTATION = "exposicion"
    const val GLOSSARY = "glosario"
    const val QUESTIONS = "preguntas"

    val all: List<StudioTask> = listOf(
        StudioTask(
            id = SUMMARY,
            label = "Resumen",
            hint = "Idea central y puntos clave con página",
            instruction = "Redacta un resumen de estudio en español, en Markdown. Empieza con la idea " +
                "central en 2 frases. Después lista de 5 a 8 puntos clave, cada uno con su cita " +
                "[Documento, p. N]. Cierra con 3 conceptos que el estudiante debe poder explicar " +
                "con sus propias palabras.",
            prompt = "Resume el material de esta mesa como si yo tuviera que exponerlo en 10 minutos.",
        ),
        StudioTask(
            id = IDEAS,
            label = "Ideas nuevas",
            hint = "Hipótesis, conexiones y preguntas abiertas",
            instruction = "Genera ideas propias del estudiante a partir del material. Devuelve 6 ideas " +
                "en Markdown, cada una con: título corto, la idea en 2-3 frases, y una etiqueta " +
                "[apoyo textual: Documento, p. N] o [inferencia propia]. Incluye al menos una conexión " +
                "con otra materia, una aplicación práctica y una pregunta que el material deja abierta. " +
                "No inventes datos: si algo es inferencia, dilo.",
            prompt = "Dame ideas nuevas que pueda defender en clase a partir de estos documentos.",
        ),
        StudioTask(
            id = CORRELATE,
            label = "Correlación",
            hint = "Tabla por eje y por obra, tensiones e intersección",
            instruction = "Compara las obras entre sí; NO resumas cada libro por separado. Empieza con " +
                "una tabla en Markdown: una fila por eje de comparación (3 a 5 ejes que salgan del " +
                "material) y una columna por obra. En cada celda escribe qué sostiene esa obra sobre " +
                "ese eje, con su cita [Documento, p. N]; si la obra no se pronuncia sobre el eje, " +
                "escribe literalmente 'no se pronuncia'. Después agrega: 1) coincidencias que se " +
                "refuerzan entre obras, 2) tensiones o contradicciones con las dos citas enfrentadas, " +
                "3) intersección: 3 ideas que solo aparecen al cruzar dos o más obras, 4) huecos y qué " +
                "falta leer. No atribuyas a una obra algo que no esté en su fragmento.",
            prompt = "Compara las obras de esta mesa: en qué coinciden, en qué se contradicen y qué " +
                "idea nueva surge al cruzarlas.",
        ),
        StudioTask(
            id = PRESENTATION,
            label = "Guion de exposición",
            hint = "Apertura, bloques y cierre con diapositivas",
            instruction = "Arma un guion de exposición en Markdown: apertura de 30 segundos, 3 o 4 " +
                "bloques con (a) qué diapositiva usar, (b) qué decir literalmente, (c) qué apoyo " +
                "textual citar; luego cierre con la idea que debe quedar, y 5 preguntas probables del " +
                "público con su respuesta corta.",
            prompt = "Prepara el guion de mi exposición con el material de esta mesa.",
        ),
        StudioTask(
            id = GLOSSARY,
            label = "Glosario",
            hint = "Términos técnicos definidos con página",
            instruction = "Extrae de 10 a 15 términos técnicos del material. Para cada uno: término en " +
                "negritas, definición tomada del contexto en una o dos frases, y cita " +
                "[Documento, p. N]. Ordena alfabéticamente y marca los que aparecen en varios documentos.",
            prompt = "Hazme el glosario de términos que tengo que dominar para la exposición.",
        ),
        StudioTask(
            id = QUESTIONS,
            label = "Preguntas de repaso",
            hint = "Conceptuales, de aplicación y de análisis",
            instruction = "Genera 8 preguntas de repaso en Markdown: 4 conceptuales, 2 de aplicación y 2 " +
                "de análisis crítico. Después de cada pregunta agrega la respuesta modelo en 2-3 " +
                "frases y la cita [Documento, p. N] que la respalda.",
            prompt = "Genera preguntas de repaso con respuesta para estudiar de estos documentos.",
        ),
    )

    fun find(id: String?): StudioTask? = all.firstOrNull { it.id == id }
}