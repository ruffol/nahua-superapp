package com.nahuanotoca.superapp.core.miniapp

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP del catálogo del Bazar/Trueque.
 *
 * Vive en el host a propósito: la WebView responde 404 a cualquier petición externa
 * (`shouldInterceptRequest`), así que la página pide los datos por el puente y nunca toca la red.
 *
 * El servidor corre en el teléfono del dueño (tienda.tlalchichi.xyz) y sigue el contrato de
 * `docs/CATALOGO_API.md`: foto completa una sola vez, parches después.
 */
class CatalogApi(private val baseUrl: String = DEFAULT_BASE_URL) {

    fun head(): JSONObject = request("GET", "/v1/catalog/head", null)

    fun full(): JSONObject = request("GET", "/v1/catalog?full=1", null)

    fun delta(since: Long): JSONObject = request("GET", "/v1/catalog/delta?since=$since", null)

    fun order(payload: JSONObject): JSONObject = request("POST", "/v1/orders", payload)

    fun report(payload: JSONObject): JSONObject = request("POST", "/v1/reports", payload)

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }

            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val parsed = runCatching { JSONObject(text) }.getOrNull()
                ?: JSONObject().put("status", "error").put("error", "invalid_response").put("http", code)

            // El catálogo llega sin `status`; el puente del SDK sólo resuelve lo que dice ok.
            if (code in 200..299 && !parsed.has("status")) parsed.put("status", "ok")
            if (code !in 200..299) {
                if (!parsed.has("status")) parsed.put("status", "error")
                parsed.put("http", code)
            }
            parsed
        } catch (error: Exception) {
            JSONObject()
                .put("status", "error")
                .put("error", "offline")
                .put("message", error.message ?: "sin conexión")
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        /** Servidor del catálogo (el mismo que publica el bazar en la web). */
        const val DEFAULT_BASE_URL = "https://tienda.tlalchichi.xyz"
        private const val TIMEOUT_MS = 10_000
    }
}
