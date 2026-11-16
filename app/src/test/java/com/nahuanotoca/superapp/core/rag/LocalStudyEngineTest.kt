package com.nahuanotoca.superapp.core.rag

import com.nahuanotoca.superapp.core.model.Mesa
import com.nahuanotoca.superapp.core.model.RagChunk
import com.nahuanotoca.superapp.core.model.StudioTasks
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStudyEngineTest {

    private val sampleMesa = Mesa(
        id = "mesa_test",
        name = "Historia de Colima",
        subject = "Ciencias Sociales",
        context = "Estudio general para examen",
    )

    private val sampleChunks = listOf(
        RagChunk(
            documentId = "d1",
            documentName = "Colima_Geografia.pdf",
            page = 1,
            text = "El estado de Colima se ubica en el occidente mexicano con una extensión de 5,627 kilómetros cuadrados y gran riqueza volcánica.",
        ),
        RagChunk(
            documentId = "d1",
            documentName = "Colima_Geografia.pdf",
            page = 2,
            text = "El Volcán de Colima y el Nevado forman parte del eje neovolcánico transversal de México, proveyendo suelos fértiles para café en Comala.",
        ),
        RagChunk(
            documentId = "d2",
            documentName = "Comercio_Tianguis.pdf",
            page = 1,
            text = "Los tianguis de Colima y Villa de Álvarez preservan productos regionales de gran valor como la tuba, tamales de ceniza y limón de Tecomán.",
        ),
    )

    private val sampleContext = ContextBuilder.build(
        query = "Colima volcanes y tianguis",
        chunks = sampleChunks,
    )

    @Test
    fun testGenerateSummary() {
        val reply = LocalStudyEngine.analyze(
            mesa = sampleMesa,
            task = StudioTasks.find(StudioTasks.SUMMARY),
            prompt = "resumen",
            context = sampleContext,
            allChunks = sampleChunks,
        )

        assertNotNull(reply)
        assertTrue(reply.text.contains("Resumen Ejecutivo"))
        assertTrue(reply.text.contains("Colima_Geografia.pdf"))
        assertTrue(reply.usage.totalTokens > 0)
    }

    @Test
    fun testGenerateIdeas() {
        val reply = LocalStudyEngine.analyze(
            mesa = sampleMesa,
            task = StudioTasks.find(StudioTasks.IDEAS),
            prompt = "ideas",
            context = sampleContext,
            allChunks = sampleChunks,
        )

        assertNotNull(reply)
        assertTrue(reply.text.contains("Ideas y Perspectivas Nuevas"))
    }

    @Test
    fun testGenerateCorrelation() {
        val reply = LocalStudyEngine.analyze(
            mesa = sampleMesa,
            task = StudioTasks.find(StudioTasks.CORRELATE),
            prompt = "correlacion",
            context = sampleContext,
            allChunks = sampleChunks,
        )

        assertNotNull(reply)
        assertTrue(reply.text.contains("Correlación Documental"))
        assertTrue(reply.text.contains("Colima_Geografia.pdf"))
        assertTrue(reply.text.contains("Comercio_Tianguis.pdf"))
    }

    @Test
    fun testAnswerFreeQuestion() {
        val reply = LocalStudyEngine.analyze(
            mesa = sampleMesa,
            task = null,
            prompt = "¿Qué productos se venden en los tianguis?",
            context = sampleContext,
            allChunks = sampleChunks,
        )

        assertNotNull(reply)
        assertTrue(reply.text.contains("Consulta:"))
        assertTrue(reply.text.contains("tianguis") || reply.text.contains("Comercio_Tianguis.pdf"))
    }
}
