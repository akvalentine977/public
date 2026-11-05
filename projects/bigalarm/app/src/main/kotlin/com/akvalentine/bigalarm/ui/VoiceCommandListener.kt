package com.akvalentine.bigalarm.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.akvalentine.bigalarm.core.VoiceCommands

/**
 * Listens for "stop" / "snooze" with the watch's own speech recogniser while the ringing screen is
 * visible. Must be driven from the main thread (a SpeechRecognizer requirement). The recogniser
 * stops after each utterance or silence, so it is restarted until a command is heard or [stop] runs.
 */
class VoiceCommandListener(
    private val context: Context,
    private val onCommand: (VoiceCommands.Command) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var onDevice = false
    private var consecutiveErrors = 0

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatus("Voice control: no speech service on this watch")
            return
        }
        running = true
        consecutiveErrors = 0
        createRecognizer(preferOnDevice = true)
        listen()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.let { r ->
            runCatching { r.cancel() }
            runCatching { r.destroy() }
        }
        recognizer = null
    }

    private fun createRecognizer(preferOnDevice: Boolean) {
        recognizer?.let { runCatching { it.destroy() } }
        onDevice = preferOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        recognizer = runCatching {
            if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrElse {
            Log.w(TAG, "Could not create recogniser", it)
            null
        }?.also { it.setRecognitionListener(listener) }
    }

    private fun listen() {
        if (!running) return
        val target = recognizer ?: run {
            onStatus("Voice control unavailable")
            running = false
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        runCatching { target.startListening(intent) }
            .onSuccess { onStatus("Listening: say \"stop\" or \"snooze\"") }
            .onFailure {
                Log.w(TAG, "startListening failed", it)
                onStatus("Voice control failed to start")
                running = false
            }
    }

    private fun restart(delayMillis: Long) {
        if (!running) return
        handler.postDelayed({ listen() }, delayMillis)
    }

    private fun handle(bundle: Bundle?, final: Boolean) {
        if (!running) return
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val command = VoiceCommands.classify(texts)
        if (command != null) {
            onStatus("Heard: ${texts.firstOrNull().orEmpty()}")
            stop()
            onCommand(command)
            return
        }
        if (final) {
            texts.firstOrNull()?.let { onStatus("Heard: $it") }
            restart(200)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) = handle(results, final = true)

        override fun onPartialResults(partialResults: Bundle?) = handle(partialResults, final = false)

        override fun onError(error: Int) {
            if (!running) return
            consecutiveErrors++
            if (consecutiveErrors > MAX_CONSECUTIVE_ERRORS) {
                onStatus("Voice control stopped (recogniser keeps failing)")
                stop()
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onStatus("Voice control: microphone permission missing")
                    stop()
                }

                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                    if (onDevice) {
                        // On-device model not downloaded: fall back to the default service.
                        createRecognizer(preferOnDevice = false)
                        restart(300)
                    } else {
                        onStatus("Voice control: language not available")
                        stop()
                    }
                }

                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    createRecognizer(preferOnDevice = onDevice)
                    restart(500)
                }

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> restart(1500)

                // ERROR_NO_MATCH and ERROR_SPEECH_TIMEOUT are the normal "heard nothing" cases.
                else -> restart(400)
            }
        }
    }

    private companion object {
        const val TAG = "VoiceCommandListener"
        const val MAX_CONSECUTIVE_ERRORS = 40
    }
}
