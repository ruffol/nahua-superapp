package com.nahuanotoca.superapp.core.rag

/**
 * Limpieza del texto extraído antes de indexarlo. Los PDFs y EPUBs reales traen ruido que arruina
 * la búsqueda: encabezados repetidos en cada página, pies con el número de página, guiones de
 * corte de línea y saltos que parten frases.
 *
 * Todo es Kotlin puro para poder probarlo sin Android.
 */
object TextCleaner {

    private val WHITESPACE = Regex("[ \t\u00A0]+")
    private val PAGE_NUMBER = Regex("^[-–—\\s]*(p[áa]gina\\s+)?\\d{1,4}[-–—\\s]*$", RegexOption.IGNORE_CASE)
    private val HYPHEN_BREAK = Regex("([\\p{L}])-\\n([\\p{L}])")

    /**
     * Limpia todas las páginas de un libro. Los encabezados y pies se detectan comparando las
     * páginas entre sí: una línea corta que aparece en la mitad o más de las páginas es ruido.
     */
    fun clean(pages: List<String>): List<String> {
        if (pages.isEmpty()) return emptyList()

        val normalized = pages.map { page ->
            page.replace("\r\n", "\n").replace('\r', '\n')
        }

        val repeated = repeatedLines(normalized)

        return normalized.map { page ->
            // Se conservan las líneas vacías: marcan el corte entre párrafos.
            val kept = page.lines()
                .map { it.trim() }
                .filterNot { line -> line.isNotEmpty() && isNoise(line, repeated) }
                .joinToString("\n")

            val joined = kept
                .replace(HYPHEN_BREAK, "$1$2")
                .replace(WHITESPACE, " ")
                .replace(Regex(" ?\n ?"), "\n")

            // Un salto simple suele ser un corte de línea del PDF; dos saltos sí son párrafo.
            joined.replace(Regex("(?<!\\n)\\n(?!\\n)"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .replace(WHITESPACE, " ")
                .trim()
        }
    }

    private fun isNoise(line: String, repeated: Set<String>): Boolean {
        if (PAGE_NUMBER.matches(line)) return true
        return repeated.contains(normalizeKey(line))
    }

    private fun normalizeKey(line: String): String =
        line.lowercase().replace(Regex("[^a-záéíóúñü0-9]+"), " ").trim()

    /**
     * Líneas cortas que se repiten en al menos la mitad de las páginas (con mínimo 3 páginas):
     * típico encabezado de capítulo o pie de editorial.
     */
    private fun repeatedLines(pages: List<String>): Set<String> {
        if (pages.size < 3) return emptySet()
        val threshold = maxOf(3, (pages.size + 1) / 2)
        val counts = mutableMapOf<String, Int>()

        pages.forEach { page ->
            page.lines()
                .map { it.trim() }
                .filter { it.length in 3..90 }
                .map { normalizeKey(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { key -> counts[key] = (counts[key] ?: 0) + 1 }
        }

        return counts.filterValues { it >= threshold }.keys
    }
}