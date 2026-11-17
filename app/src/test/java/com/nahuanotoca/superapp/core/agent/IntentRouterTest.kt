package com.nahuanotoca.superapp.core.agent

import com.nahuanotoca.superapp.core.model.StudioTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {

    @Test
    fun routesComparisonCommands() {
        listOf(
            "Compara las cinco obras",
            "compárame los libros sobre el cuerpo",
            "correlaciona estos textos",
            "contrasta las posturas",
            "¿en qué coinciden y en qué se contradicen?",
        ).forEach { question ->
            assertEquals(question, StudioTasks.CORRELATE, IntentRouter.route(question)?.toolId)
        }
    }

    @Test
    fun routesOtherStudyCommands() {
        assertEquals(StudioTasks.SUMMARY, IntentRouter.route("hazme un resumen")?.toolId)
        assertEquals(StudioTasks.SUMMARY, IntentRouter.route("¿de qué trata el libro?")?.toolId)
        assertEquals(StudioTasks.IDEAS, IntentRouter.route("dame ideas nuevas")?.toolId)
        assertEquals(StudioTasks.PRESENTATION, IntentRouter.route("prepara el guion de mi exposición")?.toolId)
        assertEquals(StudioTasks.GLOSSARY, IntentRouter.route("arma un glosario")?.toolId)
        assertEquals(StudioTasks.QUESTIONS, IntentRouter.route("genera preguntas de repaso")?.toolId)
    }

    @Test
    fun leavesPlainQuestionsAlone() {
        assertNull(IntentRouter.route("¿por qué cayó el porfiriato?"))
        assertNull(IntentRouter.route("explica el artículo 27"))
        assertNull(IntentRouter.route(""))
    }

    @Test
    fun registryDeclaresBudgetAndTwoPass() {
        val registry = ToolRegistry.default()
        val correlate = registry.find(StudioTasks.CORRELATE)

        assertTrue(correlate != null)
        assertTrue(registry.isTwoPass(StudioTasks.CORRELATE))
        assertTrue(registry.budgetOf(StudioTasks.CORRELATE).maxCalls >= 2)
        assertEquals(2_400, registry.budgetOf(StudioTasks.SUMMARY).maxTokens)
        assertEquals(6, registry.all.size)
    }
}
