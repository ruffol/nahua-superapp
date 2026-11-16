package com.nahuanotoca.superapp.core.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CacheKeyTest {

    private fun key(
        model: String = "deepseek-v4.1-flash",
        task: String? = "resumen",
        prompt: String = "Dame un resumen",
        documents: Set<String> = setOf("doc_1", "doc_2"),
        context: List<String> = listOf("[A, p. 1] hola", "[B, p. 2] mundo"),
        history: List<String> = listOf("user: antes"),
    ) = CacheKey.of(model, task, prompt, documents, context, history)

    @Test
    fun sameInputsProduceSameKey() {
        assertEquals(key(), key())
        assertEquals(64, key().length)
    }

    @Test
    fun casingAndSpacingDoNotMatter() {
        assertEquals(key(prompt = "Dame un resumen"), key(prompt = "  dame un RESUMEN  "))
    }

    @Test
    fun changingModelInvalidatesCache() {
        assertNotEquals(key(), key(model = "deepseek-v4-pro"))
    }

    @Test
    fun changingTaskInvalidatesCache() {
        assertNotEquals(key(), key(task = "ideas"))
        assertNotEquals(key(task = null), key(task = "resumen"))
    }

    @Test
    fun changingContextOrHistoryInvalidatesCache() {
        assertNotEquals(key(), key(context = listOf("[A, p. 1] otro texto")))
        assertNotEquals(key(), key(history = listOf("user: otra cosa")))
    }

    @Test
    fun documentOrderDoesNotChangeTheKey() {
        assertEquals(
            key(documents = setOf("doc_1", "doc_2")),
            key(documents = setOf("doc_2", "doc_1")),
        )
    }

    @Test
    fun differentDocumentsInvalidateCache() {
        assertNotEquals(key(), key(documents = setOf("doc_1")))
    }
}
