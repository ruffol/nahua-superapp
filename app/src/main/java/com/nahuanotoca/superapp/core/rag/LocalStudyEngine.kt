package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.StudioTask
import com.nahuanotoca.superapp.core.model.StudioTasks
import com.nahuanotoca.superapp.core.model.TokenUsage
import java.util.Locale

/**
 * Motor de análisis local y autónomo para Nahuanotoka.
 *
 * Permite que las mesas de estudio funcionen de forma completa, instantánea y sin conexión,
 * analizando los textos indexados de los PDFs directamente en el dispositivo.
 * Si no hay API key configurada o si el proveedor remoto falla, este motor genera
 * resúmenes, ideas, correlaciones, guiones, glosarios y respuestas estructuradas
 * con citas precisas de página [Documento, p. N].
 */
object LocalStudyEngine {

    fun analyze(
        mesa: Mesa,
        task: StudioTask?,
        prompt: String,
        context: ContextBuilder.Context,
        allChunks: List<RagChunk>,
    ): ChatReply {
        val text = when (task?.id) {
            StudioTasks.SUMMARY -> generateSummary(mesa, context, allChunks)
            StudioTasks.IDEAS -> generateIdeas(mesa, context, allChunks)
            StudioTasks.CORRELATE -> generateCorrelation(mesa, context, allChunks)
            StudioTasks.PRESENTATION -> generateOutline(mesa, context, allChunks)
            StudioTasks.GLOSSARY -> generateGlossary(mesa, context, allChunks)
            StudioTasks.QUESTIONS -> generateQuiz(mesa, context, allChunks)
            else -> answerQuestion(mesa, prompt, context, allChunks)
        }

        val promptTokens = (prompt.length + context.blocks.sumOf { it.length }) / 4
        val completionTokens = text.length / 4

        return ChatReply(
            text = text,
            usage = TokenUsage(
                promptTokens = promptTokens,
                completionTokens = completionTokens,
            ),
            model = "Motor Local Nahuanotoka (Offline)",
        )
    }

    private fun generateSummary(
        mesa: Mesa,
        context: ContextBuilder.Context,
        allChunks: List<RagChunk>,
    ): String = buildString {
        appendLine("## Resumen Ejecutivo · Mesa: ${mesa.name}")
        if (mesa.subject.isNotBlank()) appendLine("*Materia / Tema: ${mesa.subject}*")
        appendLine()

        val byDoc = allChunks.groupBy { it.documentName }
        appendLine("Se analizaron **${byDoc.size} documento(s)** con un total de **${allChunks.size} fragmentos** indexados.")
        appendLine()

        appendLine("### Puntos Clave Extraídos")
        byDoc.forEach { (docName, chunks) ->
            appendLine("#### 📄 $docName")
            val salient = extractSalientSentences(chunks.map { it.text }, limit = 4)
            salient.forEachIndexed { idx, sentence ->
                val page = chunks.firstOrNull { it.text.contains(sentence.take(30)) }?.page ?: 1
                appendLine("- **Punto ${idx + 1}:** $sentence [${docName}, p. $page]")
            }
            appendLine()
        }

        appendLine("### Síntesis y Conclusiones")
        val topConcepts = extractTopKeywords(allChunks.map { it.text }, limit = 6)
        appendLine("Los documentos se articulan en torno a conceptos clave como: **${topConcepts.joinToString(", ")}**.")
        appendLine("La información analizada proporciona bases sólidas para el estudio y la preparación de material académico o expositivo sin omisión de fuentes primarias.")
    }

    private fun generateIdeas(
        mesa: Mesa,
        context: ContextBuilder.Context,
        allChunks: List<RagChunk>,
    ): String = buildString {
        appendLine("## Ideas y Perspectivas Nuevas · ${mesa.name}")
        appendLine("*Reflexión y propuestas derivadas del análisis documental.*")
        appendLine()

        val keywords = extractTopKeywords(allChunks.map { it.text }, limit = 8)
        val sentences = extractSalientSentences(allChunks.map { it.text }, limit = 6)

        appendLine("### Hallazgos e Ideas de Estudio")
        sentences.forEachIndexed { index, sentence ->
            val chunk = allChunks.firstOrNull { it.text.contains(sentence.take(30)) }
            val citation = if (chunk != null) "[${chunk.documentName}, p. ${chunk.page}]" else "[Fuente documental]"
            appendLine("#### Idea ${index + 1}: Análisis Conceptual")
            appendLine("> \"$sentence\" $citation")
            appendLine("- **Implicación práctica:** Este planteamiento permite cuestionar enfoques tradicionales y desarrollar argumentos sólidos vinculados a *${keywords.getOrElse(index) { "la materia" }}*.")
            appendLine("- **Línea de profundización:** Comparar cómo este principio se aplica en situaciones reales o casos de estudio actuales.")
            appendLine()
        }

        appendLine("### Ejes de Discusión Sugeridos")
        appendLine("1. **Articulación temática:** ¿De qué manera interactúan ${keywords.take(3).joinToString(" y ")} en los textos?")
        appendLine("2. **Aplicabilidad en Colima y el entorno:** Conectar las premisas teóricas con problemáticas y realidades locales.")
    }

    private fun generateCorrelation(
        mesa: Mesa,
        context: ContextBuilder.Context,
        allChunks: List<RagChunk>,
    ): String = buildString {
        appendLine("## Correlación Documental · ${mesa.name}")
        val docs = allChunks.map { it.documentName }.distinct()

        if (docs.size <= 1) {
            appendLine("*Correlación interna entre secciones y temas de la mesa.*")
            appendLine()
            appendLine("### Puntos de Convergencia Interna")
            val salient = extractSalientSentences(allChunks.map { it.text }, limit = 4)
            salient.forEachIndexed { i, s ->
                val chunk = allChunks.firstOrNull { it.text.contains(s.take(30)) }
                appendLine("- **Eje temático ${i + 1}:** $s [${chunk?.documentName ?: "Doc"}, p. ${chunk?.page ?: 1}]")
            }
            appendLine()
            appendLine("💡 *Tip: Agrega un segundo PDF a la mesa para generar una matriz comparativa cruzada entre autores.*")
        } else {
            appendLine("### Matriz Comparativa entre Fuentes")
            appendLine("Documentos cotejados: **${docs.joinToString(", ")}**.")
            appendLine()
            appendLine("| Documento | Eje Principal | Evidencia Clave |")
            appendLine("| :--- | :--- | :--- |")
            docs.forEach { docName ->
                val docChunks = allChunks.filter { it.documentName == docName }
                val best = extractSalientSentences(docChunks.map { it.text }, limit = 1).firstOrNull() ?: "Contenido general"
                val page = docChunks.firstOrNull { it.text.contains(best.take(20)) }?.page ?: 1
                val clean = best.replace("|", "/").take(90)
                appendLine("| **$docName** | Temática general | \"$clean...\" [p. $page] |")
            }
            appendLine()
            appendLine("### Puntos de Encuentro y Divergencias")
            val commonTerms = extractTopKeywords(allChunks.map { it.text }, limit = 5)
            appendLine("- **Términos y enfoques compartidos:** Ambas fuentes abordan con rigor conceptos como *${commonTerms.joinToString(", ")}*.")
            appendLine("- **Complementariedad:** Las fuentes se enriquecen mutuamente, permitiendo construir un marco conceptual holístico.")
        }
    }

    private fun generateOutline(
        mesa: Mesa,
        context: ContextBuilder.Context,
        allChunks: List<RagChunk>,
    ): String = buildString {
        appendLine("## Guion de Exposición · ${mesa.name}")
        appendLine("*Estructura sugerida para presentación oral (15 a 20 minutos).*")
        appendLine()

        val sentences = extractSalientSentences(allChunks.map { it.text }, limit = 5)

        appendLine("### 1. Introducción y Marco General (3 min)")
        appendLine("- **Saludo y objetivo:** Presentar el tema *${mesa.name}* y su relevancia.")
        appendLine("- **Tesis central:** Qué se busca demostrar con el análisis de los documentos.")
        appendLine()

        appendLine("### 2. Desarrollo Temático (10 min)")
        sentences.forEachIndexed { idx, s ->
            val chunk = allChunks.firstOrNull { it.text.contains(s.take(30)) }
            val ref = if (chunk != null) "[${chunk.documentName}, p. ${chunk.page}]" else ""
            appendLine("#### Bloque 2.${idx + 1}: Aspecto Clave ${idx + 1}")
            appendLine("- **Concepto:** $s $ref")
            appendLine("- **Nota de orador:** Explicar con ejemplos prácticos y vincular con la materia ${mesa.subject}.")
        }
        appendLine()

        appendLine("### 3. Preguntas de Cierre y Debate (4 min)")
        appendLine("- ¿Qué implicaciones directas tiene esta postura en nuestro campo?")
        appendLine("- Abrir espacio para intervenciones de la audiencia.")
        appendLine()

        appendLine("### 4. Conclusión (2 min)")
        appendLine("- Síntesis de los puntos acordados y agradecimiento.")
    }

    private fun generateGlossary(
        mesa: Mesa,
        context: ContextBuilder.Context,
        allChunks: List<RagChunk>,
    ): String = buildString {
        appendLine("## Glosario de Términos Clave · ${mesa.name}")
        appendLine("*Conceptos esenciales extraídos del texto para estudio y consulta rápida.*")
        appendLine()

        val keywords = extractTopKeywords(allChunks.map { it.text }, limit = 10)
        keywords.forEach { term ->
            val matching = allChunks.firstOrNull { it.text.contains(term, ignoreCase = true) }
            val sentence = matching?.text?.split(Regex("[.!?]\\s+"))
                ?.firstOrNull { it.contains(term, ignoreCase = true) && it.length in 30..200 }
                ?: "Término relevante identificado en el corpus documental de la mesa."
            val citation = if (matching != null) "[${matching.documentName}, p. ${matching.page}]" else ""

            appendLine("- **${term.replaceFirstChar { it.titlecase(Locale.ROOT) }}**: $sentence $citation")
        }
    }

    private fun generateQuiz(
        mesa: Mesa,
        context: ContextBuilder.Context,
        allChunks: List<RagChunk>,
    ): String = buildString {
        appendLine("## Preguntas de Repaso · ${mesa.name}")
        appendLine("*Cuestionario de autoevaluación basado estrictamente en los textos.*")
        appendLine()

        val sentences = extractSalientSentences(allChunks.map { it.text }, limit = 5)
        sentences.forEachIndexed { idx, sentence ->
            val chunk = allChunks.firstOrNull { it.text.contains(sentence.take(30)) }
            val citation = if (chunk != null) "[${chunk.documentName}, p. ${chunk.page}]" else ""

            appendLine("### Pregunta ${idx + 1}")
            appendLine("¿Cuál es el significado o impacto principal de la siguiente afirmación en el documento?")
            appendLine("> \"$sentence\" $citation")
            appendLine()
            appendLine("**Respuesta fundamentada:** El texto sustenta que este punto es nodal para comprender el desarrollo temático $citation.")
            appendLine()
        }
    }

    private fun answerQuestion(
        mesa: Mesa,
        prompt: String,
        context: ContextBuilder.Context,
        allChunks: List<RagChunk>,
    ): String = buildString {
        appendLine("## Consulta: \"$prompt\"")
        appendLine()

        val queryTerms = prompt.lowercase(Locale.ROOT)
            .split(Regex("[\\s,;:.¿?¡!]+"))
            .filter { it.length > 2 }

        val ranked = allChunks.map { chunk ->
            val textLower = chunk.text.lowercase(Locale.ROOT)
            var score = 0
            queryTerms.forEach { term ->
                if (textLower.contains(term)) score += 2
            }
            chunk to score
        }.sortedByDescending { it.second }

        val bestHits = ranked.take(4).filter { it.second > 0 }.map { it.first }
        val sourcesToUse = if (bestHits.isNotEmpty()) bestHits else allChunks.take(3)

        appendLine("De acuerdo con los documentos indexados en la mesa **${mesa.name}**:")
        appendLine()

        sourcesToUse.forEach { chunk ->
            val sentences = chunk.text.split(Regex("[.!?]\\s+"))
                .filter { it.length in 35..350 }
            val bestSentence = sentences.maxByOrNull { s ->
                queryTerms.count { s.contains(it, ignoreCase = true) }
            } ?: sentences.firstOrNull() ?: chunk.text.take(160)

            appendLine("- \"${bestSentence.trim()}\" [${chunk.documentName}, p. ${chunk.page}]")
        }
        appendLine()
        appendLine("### Conclusión")
        appendLine("La evidencia en los textos sustenta que la respuesta a tu consulta se articula directamente en torno a los fragmentos citados.")
    }

    private fun extractSalientSentences(texts: List<String>, limit: Int): List<String> {
        val sentences = texts.flatMap { it.split(Regex("[.!?]\\s+")) }
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 45..220 && !it.startsWith("http") && !it.contains("©") }

        if (sentences.isEmpty()) return listOf("Contenido principal del documento registrado en el índice.")
        return sentences.distinct().take(limit)
    }

    private fun extractTopKeywords(texts: List<String>, limit: Int): List<String> {
        val stopWords = setOf(
            "para", "como", "este", "esta", "estos", "estas", "sobre", "entre", "hacia", "desde",
            "donde", "cuando", "quien", "porque", "todos", "todas", "cada", "unos", "unas", "cual",
            "cuales", "sido", "estar", "tener", "hacer", "puede", "pueden", "ser", "los", "las",
            "una", "uno", "del", "con", "por", "que", "más", "sus", "les", "nos", "sin",
        )
        val frequency = mutableMapOf<String, Int>()
        texts.forEach { text ->
            text.lowercase(Locale.ROOT)
                .split(Regex("[^a-záéíóúñ]+"))
                .filter { it.length in 4..18 && it !in stopWords }
                .forEach { word ->
                    frequency[word] = (frequency[word] ?: 0) + 1
                }
        }
        val sorted = frequency.entries.sortedByDescending { it.value }.map { it.key }
        return if (sorted.isNotEmpty()) sorted.take(limit) else listOf("estudio", "análisis", "contenido", "documento")
    }
}
