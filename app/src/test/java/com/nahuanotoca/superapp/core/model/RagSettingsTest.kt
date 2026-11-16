package com.nahuanotoca.superapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagSettingsTest {

    @Test
    fun buildsChatEndpointFromBaseUrl() {
        val settings = RagSettings(baseUrl = "https://opencode.ai/zen/go/v1", apiKey = "key")

        assertEquals("https://opencode.ai/zen/go/v1/chat/completions", settings.chatEndpoint)
    }

    @Test
    fun toleratesTrailingSlash() {
        val settings = RagSettings(baseUrl = "https://api.example.com/v1/", apiKey = "key")

        assertEquals("https://api.example.com/v1/chat/completions", settings.chatEndpoint)
    }

    @Test
    fun doesNotDuplicateChatCompletions() {
        val settings = RagSettings(baseUrl = "https://api.example.com/v1/chat/completions", apiKey = "key")

        assertEquals("https://api.example.com/v1/chat/completions", settings.chatEndpoint)
    }

    @Test
    fun isConfiguredRequiresKeyUrlAndModel() {
        assertFalse(RagSettings(apiKey = "").isConfigured)
        assertTrue(RagSettings(apiKey = "sk-test").isConfigured)
    }
}
