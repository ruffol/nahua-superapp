package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.StudioTasks
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoPassPromptsTest {

    private val mesa = Mesa(id = "m1", name = "Feminismos", subject = "Exposición")

    @Test
    fun digestPassAsksForOneWorkAndCitations() {
        val system = StudioPrompts.digestSystem()
        val user = StudioPrompts.digestUser(
            mesa = mesa,
            documentName = "Cabnal 2010",
            topic = "territorio y comunidad",
            blocks = listOf("[Cabnal 2010, p. 7] El territorio cuerpo-tierra se defiende desde la comunidad."),
        )

        assertTrue(system.contains("UNA sola obra"))
        assertTrue(system.contains("No se pronuncia sobre el tema"))
        assertTrue(user.contains("Obra: Cabnal 2010"))
        assertTrue(user.contains("territorio y comunidad"))
        assertTrue(user.contains("[Fragmento 1]"))
    }

    @Test
    fun comparisonPassReceivesOneDigestPerWork() {
        val digests = listOf(
            "Cabnal 2010" to "- El territorio cuerpo-tierra es central [Cabnal 2010, p. 7]",
            "Paredes" to "- La comunidad como cuerpo político [Paredes, p. 5]",
        )
        val instruction = StudioTasks.find(StudioTasks.CORRELATE)!!.instruction
        val user = StudioPrompts.comparisonUser(StudioTasks.find(StudioTasks.CORRELATE)!!, digests)
        val system = StudioPrompts.comparisonSystem(mesa)

        assertTrue(user.contains("## Cabnal 2010"))
        assertTrue(user.contains("## Paredes"))
        assertTrue(user.contains(instruction.take(40)))
        assertTrue(system.contains("tabla por eje y por obra"))
        assertTrue(system.contains("no se pronuncia"))
    }

    @Test
    fun comparisonKeepsStudentContext() {
        val withContext = Mesa(id = "m2", name = "Historia", subject = "Clase", context = "Público: compañeros; 10 minutos")

        val system = StudioPrompts.comparisonSystem(withContext)

        assertTrue(system.contains("Público: compañeros"))
    }
}
