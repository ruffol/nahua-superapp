package com.nahuanotoca.superapp.feature.rag

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Dictado por voz con el servicio de reconocimiento del teléfono. Pide el modo offline
 * (EXTRA_PREFER_OFFLINE) para que, si el dispositivo tiene el paquete de idioma descargado, el audio
 * no salga de él. Si no hay servicio de reconocimiento, la app lo dice y ofrece el teclado.
 */
class VoiceDictation(private val context: Context) {

    var listening by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    fun start(onText: (String) -> Unit) {
        if (!isAvailable()) {
            error = "Este teléfono no tiene reconocimiento de voz instalado"
            return
        }
        stop()
        error = null
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (created == null) {
            error = "No pudimos iniciar el dictado"
            return
        }
        recognizer = created

        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                error = null
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(errorCode: Int) {
                listening = false
                error = describe(errorCode)
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) onText(text.trim())
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        runCatching { created.startListening(intent) }.onFailure {
            listening = false
            error = "No pudimos abrir el micrófono: " + (it.message ?: "permiso denegado")
        }
    }

    fun stop() {
        listening = false
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun describe(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "No te entendí, intenta otra vez"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuché nada"
        SpeechRecognizer.ERROR_AUDIO -> "Problema con el micrófono"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "El dictado necesita el paquete de idioma offline; descárgalo en Ajustes de voz"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor está ocupado"
        else -> "No pudimos dictar (código " + code + ")"
    }
}

@Composable
fun rememberVoiceDictation(): VoiceDictation {
    val context = LocalContext.current
    val dictation = remember { VoiceDictation(context.applicationContext) }
    DisposableEffect(dictation) {
        onDispose { dictation.stop() }
    }
    return dictation
}
