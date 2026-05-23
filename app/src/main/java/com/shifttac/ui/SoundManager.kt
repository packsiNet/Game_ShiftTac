package com.shifttac.ui

import android.content.Context
import android.media.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.*

enum class FeedbackMode { SOUND, VIBRATION, SILENT }

class SoundManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("shifttac", Context.MODE_PRIVATE)
    private val sampleRate = 22050

    var mode: FeedbackMode
        get() = try {
            FeedbackMode.valueOf(prefs.getString("feedback", FeedbackMode.SOUND.name)!!)
        } catch (_: Exception) { FeedbackMode.SOUND }
        set(value) { prefs.edit().putString("feedback", value.name).apply() }

    // ─── Vibration ──────────────────────────────────────────────────────────

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun vibrate(ms: Long) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    // ─── PCM synthesis ──────────────────────────────────────────────────────

    private fun makePcm(freqHz: Float, durationMs: Int, endFreqHz: Float = freqHz): ShortArray {
        val n = sampleRate * durationMs / 1000
        val buf = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val freq = freqHz + (endFreqHz - freqHz) * t
            val env = when {
                t < 0.04f -> t / 0.04f
                t > 0.55f -> (1f - t) / 0.45f
                else -> 1f
            }.coerceIn(0f, 1f)
            phase += 2.0 * PI * freq / sampleRate
            buf[i] = (sin(phase) * 0.28 * Short.MAX_VALUE * env).toInt().toShort()
        }
        return buf
    }

    private fun playPcm(buf: ShortArray) {
        kotlin.concurrent.thread(isDaemon = true) {
            runCatching {
                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    maxOf(buf.size * 2, 2048),
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                track.write(buf, 0, buf.size)
                track.play()
                Thread.sleep(buf.size * 1000L / sampleRate + 40)
                track.stop()
                track.release()
            }
        }
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    fun onPlaceX() = when (mode) {
        FeedbackMode.SOUND     -> playPcm(makePcm(880f, 65))
        FeedbackMode.VIBRATION -> vibrate(16)
        FeedbackMode.SILENT    -> Unit
    }

    fun onPlaceO() = when (mode) {
        FeedbackMode.SOUND     -> playPcm(makePcm(523f, 65))
        FeedbackMode.VIBRATION -> vibrate(16)
        FeedbackMode.SILENT    -> Unit
    }

    fun onShift() = when (mode) {
        FeedbackMode.SOUND     -> playPcm(makePcm(350f, 120, 700f))
        FeedbackMode.VIBRATION -> vibratePattern(longArrayOf(0, 22, 28, 22))
        FeedbackMode.SILENT    -> Unit
    }

    fun onWin() = when (mode) {
        FeedbackMode.SOUND -> {
            val gap = ShortArray(sampleRate * 45 / 1000)
            playPcm(makePcm(523f, 90) + gap + makePcm(659f, 90) + gap + makePcm(784f, 130))
        }
        FeedbackMode.VIBRATION -> vibratePattern(longArrayOf(0, 30, 40, 30, 40, 80))
        FeedbackMode.SILENT    -> Unit
    }
}
