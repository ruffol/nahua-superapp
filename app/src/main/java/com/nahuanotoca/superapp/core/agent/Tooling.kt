package com.nahuanotoca.superapp.core.agent

import com.nahuanotoca.superapp.core.model.RagSettings
import com.nahuanotoca.superapp.core.model.StudioTask
import com.nahuanotoca.superapp.core.model.StudioTasks
import java.text.Normalizer
import com.nahuanotoca.superapp.core.rag.ChatClient
import com.nahuanotoca.superapp.core.rag.ChatMessage
import com.nahuanotoca.superapp.core.rag.ChatReply

/**
 * Contratos del orquestador: herramientas registradas, presupuesto por tarea, pasarela al modelo y
 * enrutador determinista.
 *
 * La idea es que la UI nunca construya prompts ni decida qué herramienta usar: pide una intención y
 * el registro resuelve presupuesto, formato y número de llamadas.
 */

/** Límites de una ejecución. Se declaran por herramienta para que el costo sea predecible. */
data class ToolBudget(
    val maxCalls: Int = 1,
    val maxTokens: Int = 2_400,
    val maxSteps: Int = 1,
)

/** Pasarela al proveedor: hoy un adaptador de [ChatClient]; mañana otro proveedor. */
interface ModelGateway {
    suspend fun complete(
        settings: RagSettings,
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): Result<ChatReply>

    suspend fun stream(
        settings: RagSettings,
        messages: List<ChatMessage>,
        maxTokens: Int,
        onDelta: (String) -> Unit,
    ): Result<ChatReply>
}

class ChatClientGateway(private val client: ChatClient) : ModelGateway {

    override suspend fun complete(
        settings: RagSettings,
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): Result<ChatReply> = client.complete(settings, messages, maxTokens = maxTokens)

    override suspend fun stream(
        settings: RagSettings,
        messages: List<ChatMessage>,
        maxTokens: Int,
        onDelta: (String) -> Unit,
    ): Result<ChatReply> = client.completeStreaming(
        settings = settings,
        messages = messages,
        maxTokens = maxTokens,
        onDelta = onDelta,
    )
}

/**
 * Una herramienta de estudio. [task] es la plantilla de formato y estrategia de contexto.
 * [twoPass] marca las que necesitan una pasada por obra antes de cruzar.
 */
class AssistantTool(
    val task: StudioTask,
    val budget: ToolBudget,
    val twoPass: Boolean = false,
) {
    val id: String get() = task.id
    val label: String get() = task.label
    val description: String get() = task.instruction
}

/** Registro único de herramientas. La UI sólo puede ejecutar lo que esté aquí. */
class ToolRegistry(tools: List<AssistantTool>) {

    private val byId: Map<String, AssistantTool> = tools.associateBy { it.id }

    val all: List<AssistantTool> = tools

    fun find(id: String?): AssistantTool? = id?.let { byId[it] }

    fun budgetOf(id: String?): ToolBudget = find(id)?.budget ?: ToolBudget()

    fun isTwoPass(id: String?): Boolean = find(id)?.twoPass == true

    companion object {
        /** Presupuesto por herramienta. Correlación cuesta más porque hace una pasada por obra. */
        fun default(): ToolRegistry = ToolRegistry(
            StudioTasks.all.map { task ->
                when (task.id) {
                    StudioTasks.CORRELATE -> AssistantTool(
                        task = task,
                        budget = ToolBudget(maxCalls = 6, maxTokens = 3_000, maxSteps = 2),
                        twoPass = true,
                    )

                    StudioTasks.PRESENTATION, StudioTasks.QUESTIONS -> AssistantTool(
                        task = task,
                        budget = ToolBudget(maxCalls = 1, maxTokens = 2_800, maxSteps = 1),
                    )

                    else -> AssistantTool(
                        task = task,
                        budget = ToolBudget(maxCalls = 1, maxTokens = 2_400, maxSteps = 1),
                    )
                }
            },
        )
    }
}

/**
 * Enrutador determinista: convierte una frase del estudiante en una herramienta registrada.
 * No usa el modelo, así que es gratis, instantáneo y explicable.
 */
object IntentRouter {

    data class Route(val toolId: String, val confidence: Double)

    private val RULES: List<Pair<String, List<String>>> = listOf(
        StudioTasks.CORRELATE to listOf(
            "compara", "comparar", "comparame", "correlaciona", "correlacion", "contrasta",
            "cruza", "cruzar", "diferencias entre", "coinciden", "contradicen", "interseccion",
        ),
        StudioTasks.SUMMARY to listOf(
            "resumen", "resume", "resumeme", "sintetiza", "de que trata", "de que habla",
        ),
        StudioTasks.IDEAS to listOf(
            "ideas nuevas", "dame ideas", "genera ideas", "propuestas", "hipotesis", "ideas propias",
        ),
        StudioTasks.PRESENTATION to listOf(
            "exposicion", "presentacion", "guion", "diapositivas", "exponer",
        ),
        StudioTasks.GLOSSARY to listOf(
            "glosario", "terminos clave", "define los terminos", "vocabulario",
        ),
        StudioTasks.QUESTIONS to listOf(
            "preguntas de repaso", "preguntas de examen", "hazme preguntas", "genera preguntas",
            "cuestionario", "banco de preguntas",
        ),
    )

    /** Devuelve la herramienta si la frase parece una orden; si no, null (charla normal). */
    fun route(question: String): Route? {
        val text = fold(question)
        if (text.length < 3) return null

        RULES.forEach { (toolId, keys) ->
            if (keys.any { key -> text.contains(key) }) {
                return Route(toolId, confidence = 0.85)
            }
        }
        return null
    }

    /** Minúsculas y sin acentos: "compárame" y "correlación" también deben enrutar. */
    fun fold(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}