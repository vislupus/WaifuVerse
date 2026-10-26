package com.vislupus.waifuverse.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.vislupus.waifuverse.config.AudioMode
import com.vislupus.waifuverse.config.ModelConfig
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Plays the waifu's voice: PCM16 mono at [ModelConfig.OUTPUT_SAMPLE_RATE].
 *
 * Chunks are queued and written from a dedicated thread. For lip sync the
 * loudness (RMS) of every chunk is recorded together with the frame position
 * at which it ends, so [currentLevel] returns the loudness of what is being
 * heard RIGHT NOW (not what was just queued).
 */
class AudioOutput(private val mode: AudioMode) {

    private class Chunk(val generation: Int, val data: ByteArray)
    private class LevelMark(val endFrame: Long, val level: Float)

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private val queue = LinkedBlockingQueue<Chunk>()
    private val running = AtomicBoolean(false)

    @Volatile
    private var generation = 0

    private val lock = Any()
    private var framesWritten = 0L
    private var headBase = 0L
    private val marks = ArrayDeque<LevelMark>()

    fun start(): Boolean {
        if (running.get()) return true
        val sampleRate = ModelConfig.OUTPUT_SAMPLE_RATE
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val wanted = sampleRate * ModelConfig.OUTPUT_BUFFER_MS / 1000 * 2
        val bufferSize = maxOf(minBuffer * 2, wanted)
        val usage = if (mode == AudioMode.VOICE_CALL) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            return false
        }
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            return false
        }
        track = t
        running.set(true)
        t.play()
        thread = Thread({
            while (running.get()) {
                val chunk = try {
                    queue.take()
                } catch (_: InterruptedException) {
                    break
                }
                if (chunk.generation != generation) continue // dropped by an interruption
                val tr = track ?: break
                var offset = 0
                while (offset < chunk.data.size && running.get() && chunk.generation == generation) {
                    val n = tr.write(chunk.data, offset, chunk.data.size - offset)
                    if (n < 0) break
                    offset += n
                }
            }
        }, "waifu-speaker").also { it.start() }
        return true
    }

    /** Queues a PCM16 chunk for playback. */
    fun enqueue(pcm: ByteArray) {
        if (!running.get() || pcm.isEmpty()) return
        val frames = pcm.size / 2
        val level = rms(pcm)
        synchronized(lock) {
            framesWritten += frames
            marks.addLast(LevelMark(framesWritten, level))
        }
        queue.offer(Chunk(generation, pcm))
    }

    /** Drops everything queued and playing (used when the user interrupts her). */
    fun flush() {
        generation++
        queue.clear()
        val t = track ?: return
        try {
            t.pause()
            t.flush()
            t.play()
        } catch (_: Exception) {
        }
        synchronized(lock) {
            headBase = safeHead(t)
            framesWritten = 0
            marks.clear()
        }
    }

    /** Loudness (0..1) of the audio being heard right now; 0 when silent. */
    fun currentLevel(): Float {
        val t = track ?: return 0f
        val head = safeHead(t)
        synchronized(lock) {
            val pos = head - headBase
            if (pos >= framesWritten) {
                marks.clear()
                return 0f
            }
            while (marks.size > 1 && marks.first().endFrame <= pos) marks.removeFirst()
            return marks.firstOrNull()?.level ?: 0f
        }
    }

    /** True while queued audio is still being played. */
    fun isPlaying(): Boolean {
        val t = track ?: return false
        val head = safeHead(t)
        synchronized(lock) { return head - headBase < framesWritten }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        generation++
        queue.clear()
        thread?.interrupt()
        thread = null
        track?.let {
            try {
                it.pause()
                it.flush()
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        track = null
        synchronized(lock) {
            framesWritten = 0
            headBase = 0
            marks.clear()
        }
    }

    private fun safeHead(t: AudioTrack): Long = try {
        // The head position is an unsigned 32-bit counter exposed as Int.
        t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
    } catch (_: Exception) {
        0L
    }

    private fun rms(pcm: ByteArray): Float {
        val samples = pcm.size / 2
        if (samples == 0) return 0f
        var sum = 0.0
        var i = 0
        while (i + 1 < pcm.size) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt() / 32768.0
            sum += s * s
            i += 2
        }
        return sqrt(sum / samples).toFloat()
    }

    companion object {
        /** Stream type used for volume keys while a session is active. */
        fun volumeStream(mode: AudioMode): Int =
            if (mode == AudioMode.VOICE_CALL) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
    }
}
