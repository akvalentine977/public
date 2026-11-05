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
import java.util.Locale

/**
 * Listens for "stop" / "snooze" with the watch's own speech recogniser while the ringing screen is
 * visible. Must be driven from the main thread (a SpeechRecognizer requirement). The recogniser
 * stops after each utterance or silence, so it is restarted until a command is heard or [stop] runs.
 *
 * Recognisers are tried in order: on-device, the default service preferring offline, then the
 * default service allowing the network. A language or repeated service error moves to the next.
 */
class VoiceCommandListener(
    private val context: Context,
    private val onCommand: (VoiceCommands.Command) -> Unit,
    private val onStatus: (String) -> Unit,
    /** True on the ringing screen (one command ends the session); false on the test screen. */
    private val stopOnCommand: Boolean = true,
) {
    private enum class Mode(val label: String) { ON_DEVICE("on-device"), DEFAULT_OFFLINE("default, offline"), DEFAULT_ONLINE("default") }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var mode = Mode.ON_DEVICE
    private var consecutiveErrors = 0
    private var serviceErrorsInMode = 0

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatus("Voice: no speech recognition service on this watch")
            return
        }
        running = true
        consecutiveErrors = 0
        mode = Mode.ON_DEVICE
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) mode = Mode.DEFAULT_OFFLINE
        createRecognizer()
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

    private fun createRecognizer() {
        recognizer?.let { runCatching { it.destroy() } }
        serviceErrorsInMode = 0
        recognizer = runCatching {
            if (mode == Mode.ON_DEVICE) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrElse {
            Log.w(TAG, "Could not create recogniser in mode $mode", it)
            null
        }?.also { it.setRecognitionListener(listener) }
        Log.i(TAG, "Recogniser created, mode=$mode, ok=${recognizer != null}")
    }

    /** Moves to the next recogniser mode; returns false when there is none left. */
    private fun nextMode(): Boolean {
        val next = Mode.entries.getOrNull(mode.ordinal + 1) ?: return false
        mode = next
        createRecognizer()
        return recognizer != null
    }

    private fun listen() {
        if (!running) return
        val target = recognizer ?: run {
            if (nextMode()) {
                listen()
            } else {
                onStatus("Voice: could not start any speech recogniser")
                running = false
            }
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, mode != Mode.DEFAULT_ONLINE)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        runCatching { target.startListening(intent) }
            .onSuccess { onStatus("Voice: listening (${mode.label}), say \"stop\" or \"snooze\"") }
            .onFailure {
                Log.w(TAG, "startListening failed in mode $mode", it)
                if (nextMode()) listen() else {
                    onStatus("Voice: failed to start: ${it.message}")
                    running = false
                }
            }
    }

    private fun restart(delayMillis: Long) {
        if (!running) return
        handler.postDelayed({ listen() }, delayMillis)
    }

    private fun handle(bundle: Bundle?, final: Boolean) {
        if (!running) return
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        Log.i(TAG, "${if (final) "results" else "partial"}: $texts")
        val command = VoiceCommands.classify(texts)
        if (command != null) {
            onStatus("Voice: heard \"${texts.firstOrNull().orEmpty()}\" -> ${command.name.lowercase()}")
            if (stopOnCommand) {
                stop()
                onCommand(command)
            } else {
                runCatching { recognizer?.cancel() }
                onCommand(command)
                restart(800)
            }
            return
        }
        if (final) {
            texts.firstOrNull()?.let { onStatus("Voice: heard \"$it\" (not a command)") }
            restart(200)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveErrors = 0
            serviceErrorsInMode = 0
            Log.i(TAG, "ready for speech (${mode.label})")
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) = handle(results, final = true)

        override fun onPartialResults(partialResults: Bundle?) = handle(partialResults, final = false)

        override fun onError(error: Int) {
            if (!running) return
            consecutiveErrors++
            Log.w(TAG, "error $error (${errorName(error)}) in mode $mode, consecutive=$consecutiveErrors")
            if (consecutiveErrors > MAX_CONSECUTIVE_ERRORS) {
                onStatus("Voice: gave up after repeated errors (${errorName(error)})")
                stop()
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onStatus("Voice: microphone permission missing")
                    stop()
                }

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal "heard nothing" outcomes: keep listening.
                    onStatus("Voice: listening (${mode.label}), say \"stop\" or \"snooze\"")
                    restart(300)
                }

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> restart(1500)

                else -> {
                    // Language, client, server, audio or network trouble: give this mode a couple of
                    // tries, then fall back to the next recogniser.
                    serviceErrorsInMode++
                    val languageProblem = error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                    if (languageProblem || serviceErrorsInMode >= 3) {
                        if (nextMode()) {
                            onStatus("Voice: ${errorName(error)}, trying ${mode.label} recogniser")
                            restart(300)
                        } else {
                            onStatus("Voice: ${errorName(error)}, no recogniser left to try")
                            stop()
                        }
                    } else {
                        if (error == SpeechRecognizer.ERROR_CLIENT) createRecognizer()
                        onStatus("Voice: ${errorName(error)}, retrying")
                        restart(600)
                    }
                }
            }
        }
    }

    private fun errorName(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_AUDIO -> "audio error"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recogniser busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "no permission"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "too many requests"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "server disconnected"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language model not downloaded"
        else -> "error $code"
    }

    private companion object {
        const val TAG = "VoiceCommandListener"
        const val MAX_CONSECUTIVE_ERRORS = 40
    }
}
