package com.triangle.app.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Two short synthesized chimes (no audio assets): a bright rising two-note "ding-ding" for tasks
 * and a softer three-note arpeggio for habits, so the two completions sound distinct.
 */
object CompletionSounds {
    private const val RATE = 22050

    // (frequency Hz, start offset seconds)
    private val TASK = listOf(880.0 to 0.0, 1318.5 to 0.11)
    private val HABIT = listOf(523.25 to 0.0, 659.25 to 0.09, 783.99 to 0.18, 1046.5 to 0.27)

    fun playTask() = play(TASK, 0.55)
    fun playHabit() = play(HABIT, 0.7)

    private fun play(notes: List<Pair<Double, Double>>, tail: Double) {
        Thread {
            runCatching {
                val total = ((notes.last().second + tail) * RATE).toInt()
                val buf = ShortArray(total)
                for ((freq, start) in notes) {
                    val s0 = (start * RATE).toInt()
                    var i = 0
                    while (s0 + i < total) {
                        val t = i.toDouble() / RATE
                        val env = (1.0 - exp(-t * 400.0)) * exp(-t * 6.0)
                        val v = (sin(2 * PI * freq * t) + 0.25 * sin(4 * PI * freq * t)) * env * 0.35
                        buf[s0 + i] = (buf[s0 + i] + (v * Short.MAX_VALUE).toInt()).coerceIn(-32768, 32767).toShort()
                        i++
                    }
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.setNotificationMarkerPosition(buf.size - 1)
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) { t.release() }
                    override fun onPeriodicNotification(t: AudioTrack) {}
                })
                track.play()
            }
        }.start()
    }
}
