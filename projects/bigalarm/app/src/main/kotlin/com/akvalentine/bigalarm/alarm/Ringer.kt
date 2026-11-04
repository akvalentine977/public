package com.akvalentine.bigalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Vibration and sound for a ringing alarm. Everything here is best-effort and never throws. */
class Ringer(private val context: Context, private val scope: CoroutineScope) {

    private var mediaPlayer: MediaPlayer? = null
    private var toneJob: Job? = null
    private var vibrating = false

    fun start(vibrate: Boolean, sound: Boolean) {
        stop()
        if (vibrate) startVibration()
        if (sound) startSound()
    }

    fun stop() {
        if (vibrating) {
            runCatching { vibrator().cancel() }
            vibrating = false
        }
        toneJob?.cancel()
        toneJob = null
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    private fun vibrator() = context.getSystemService(VibratorManager::class.java).defaultVibrator

    private fun startVibration() {
        runCatching {
            // 0 ms delay, then buzz/pause pairs; repeat from index 0 until cancelled.
            val pattern = longArrayOf(0, 700, 300, 700, 300, 700, 1000)
            val effect = VibrationEffect.createWaveform(pattern, 0)
            val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
            vibrator().vibrate(effect, attributes)
            vibrating = true
        }.onFailure { Log.w(TAG, "Vibration failed", it) }
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val started = uri != null && runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
                mediaPlayer = this
            }
        }.onFailure { Log.w(TAG, "Ringtone playback failed, using tone generator", it) }.isSuccess

        if (!started) startToneLoop()
    }

    /** Fallback when the watch has no alarm ringtone: synthesised beeps on the alarm stream. */
    private fun startToneLoop() {
        toneJob = scope.launch {
            val generator = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }
                .getOrNull() ?: return@launch
            try {
                while (isActive) {
                    generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                    delay(1500)
                }
            } finally {
                runCatching { generator.release() }
            }
        }
    }

    private companion object {
        const val TAG = "Ringer"
    }
}
