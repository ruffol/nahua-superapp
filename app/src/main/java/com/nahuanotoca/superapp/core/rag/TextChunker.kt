package com.nahuanotoca.superapp.core.rag

/**
 * Splits extracted PDF pages into overlapping, word-boundary aware chunks. Pure Kotlin so the
 * chunking rules can be unit tested without Android.
 */
object TextChunker {

    data class Chunk(val page: Int, val text: String)

    private val WHITESPACE = Regex("\\s+")

    fun chunk(
        pages: List<String>,
        maxChars: Int = 900,
        overlapChars: Int = 140,
    ): List<Chunk> {
        require(maxChars > 200) { "maxChars must be greater than 200" }
        require(overlapChars in 0 until maxChars) { "overlapChars must be smaller than maxChars" }

        val chunks = mutableListOf<Chunk>()

        pages.forEachIndexed { pageIndex, raw ->
            val text = raw.replace(WHITESPACE, " ").trim()
            if (text.isEmpty()) return@forEachIndexed

            if (text.length <= maxChars) {
                chunks += Chunk(pageIndex + 1, text)
                return@forEachIndexed
            }

            var start = 0
            while (start < text.length) {
                var end = (start + maxChars).coerceAtMost(text.length)
                if (end < text.length) {
                    val boundary = text.lastIndexOfAny(
                        chars = charArrayOf('.', '!', '?', ';', ',', ' '),
                        startIndex = end,
                    )
                    if (boundary > start + maxChars / 2) {
                        end = boundary + 1
                    }
                }

                val piece = text.substring(start, end).trim()
                if (piece.isNotEmpty()) {
                    chunks += Chunk(pageIndex + 1, piece)
                }

                if (end >= text.length) break
                start = (end - overlapChars).coerceAtLeast(start + 1)
            }
        }

        return chunks
    }
}
