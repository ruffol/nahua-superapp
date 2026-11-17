package com.nahuanotoca.superapp.core.model

/**
 * Contrato de evidencia de una consulta: qué fragmento exacto respaldó la respuesta, con su
 * documento, página y estrategia de recuperación. Sustituye a [RagSource] como fuente de verdad;
 * [RagSource] se conserva para el historial ya guardado.
 */
data class RetrievedEvidence(
    val chunkId: String,
    val documentId: String,
    val documentTitle: String,
    val page: Int,
    val text: String,
    val score: Double,
    val strategy: String,
)

data class RetrievalResult(
    val query: String,
    val items: List<RetrievedEvidence>,
    val mode: String,
    val weak: Boolean,
    val indexVersion: String = "",
)
