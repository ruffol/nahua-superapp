package com.nahuanotoca.superapp.core.rag

/**
 * Fragmentación por palabras, como pide la guía de estudio: bloques de 400–500 palabras con 50 de
 * solapamiento para no cortar una idea a la mitad.
 *
 * Antes se cortaba por caracteres (~150 palabras), lo que producía fragmentos diminutos y una
 * búsqueda menos precisa.
 */
object TextChunker {

    data class Chunk(val page: Int, val text: String, val words: Int = 0)

    private val WHITESPACE = Regex("\\s+")

    fun chunk(
        pages: List<String>,
        maxWords: Int = 450,
        overlapWords: Int = 50,
    ): List<Chunk> {
        require(maxWords in 50..2_000) { "maxWords debe estar entre 50 y 2000" }
        require(overlapWords in 0 until maxWords) { "overlapWords debe ser menor que maxWords" }

        val chunks = mutableListOf<Chunk>()

        pages.forEachIndexed { pageIndex, raw ->
            val words = raw.replace(WHITESPACE, " ").trim().split(" ").filter { it.isNotEmpty() }
            if (words.isEmpty()) return@forEachIndexed

            if (words.size <= maxWords) {
                chunks += Chunk(pageIndex + 1, words.joinToString(" "), words.size)
                return@forEachIndexed
            }

            var start = 0
            while (start < words.size) {
                val end = (start + maxWords).coerceAtMost(words.size)
                val text = words.subList(start, end).joinToString(" ")
                chunks += Chunk(pageIndex + 1, text, end - start)
                if (end >= words.size) break
                start = (end - overlapWords).coerceAtLeast(start + 1)
            }
        }

        return chunks
    }
}