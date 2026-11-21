package com.cosmith.lulu.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Soft, musical interface sounds, synthesised on the fly: a marimba-like note whose
 * pitch climbs with each success in a row, and a gentle neutral sound instead of a
 * buzzer. No audio files needed.
 */
class Tones {
    private val rate = 44_100
    private val handler = Handler(Looper.getMainLooper())

    /** Pentatonic notes (C major), so any sequence sounds good. */
    private val scale = listOf(523.25, 587.33, 659.25, 783.99, 880.00, 1046.50, 1174.66, 1318.51)

    private val cache = HashMap<String, ShortArray>()

    fun success(streak: Int) {
        val i = min(streak.coerceAtLeast(0), scale.lastIndex)
        play("ok$i") { marimba(scale[i], 0.55, 0.9) }
    }

    /** Neutral, low and short: "let's listen again", never "wrong". */
    fun soft() = play("soft") { mix(marimba(392.0, 0.22, 0.35), 0, marimba(329.63, 0.35, 0.3), (0.13 * rate).toInt()) }

    fun tap() = play("tap") { marimba(1567.98, 0.06, 0.25) }

    fun fanfare() = play("fanfare") {
        var out = ShortArray(0)
        listOf(523.25, 659.25, 783.99, 1046.50).forEachIndexed { i, f ->
            out = mix(out, 0, marimba(f, 0.6, 0.7), (i * 0.11 * rate).toInt())
        }
        out
    }

    /** A missing interface sound is never worth a crash in the middle of a child's session. */
    private fun play(key: String, make: () -> ShortArray) {
        val pcm = cache.getOrPut(key, make)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "no audio track for $key", e)
            return
        }
        try {
            track.write(pcm, 0, pcm.size)
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return
            }
            track.play()
            handler.postDelayed({ track.release() }, pcm.size * 1000L / rate + 200)
        } catch (e: Exception) {
            Log.w(TAG, "could not play $key", e)
            track.release()
        }
    }

    /** Fundamental plus the bar's 4th harmonic, quick attack, exponential decay. */
    private fun marimba(freq: Double, seconds: Double, volume: Double): ShortArray {
        val n = (seconds * rate).toInt()
        val attack = 0.004 * rate
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val env = min(1.0, i / attack) * exp(-t * 7.5)
            val tone = sin(2 * PI * freq * t) + 0.35 * sin(2 * PI * freq * 4 * t) * exp(-t * 30) +
                0.12 * sin(2 * PI * freq * 9.8 * t) * exp(-t * 60)
            (tone * env * volume * 0.55 * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun mix(a: ShortArray, aOffset: Int, b: ShortArray, bOffset: Int): ShortArray {
        val out = IntArray(maxOf(a.size + aOffset, b.size + bOffset))
        a.forEachIndexed { i, v -> out[i + aOffset] += v.toInt() }
        b.forEachIndexed { i, v -> out[i + bOffset] += v.toInt() }
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1)
        val k = if (peak > 30000) 30000.0 / peak else 1.0
        return ShortArray(out.size) { (out[it] * k).toInt().toShort() }
    }

    private companion object {
        const val TAG = "Tones"
    }
}
