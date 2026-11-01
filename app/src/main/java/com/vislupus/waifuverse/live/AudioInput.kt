package com.vislupus.waifuverse.live

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.vislupus.waifuverse.config.AudioMode
import com.vislupus.waifuverse.config.ModelConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Microphone capture: PCM16 mono at [sampleRate], delivered
 * in fixed chunks to [onChunk] from a dedicated thread.
 *
 * RECORD_AUDIO permission must already be granted.
 */
class AudioInput(
    private val mode: AudioMode,
    private val sampleRate: Int = ModelConfig.INPUT_SAMPLE_RATE,
    private val onChunk: (ByteArray, Int) -> Unit,
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private val effects = ArrayList<android.media.audiofx.AudioEffect>()

    /** While true, chunks are read (to keep the pipeline flowing) but not delivered. */
    @Volatile
    var muted: Boolean = false

    val isRunning: Boolean get() = running.get()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running.get()) return true
        val chunkBytes = sampleRate * ModelConfig.INPUT_CHUNK_MS / 1000 * 2
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val bufferSize = maxOf(minBuffer * 2, chunkBytes * 4)
        val source = if (mode == AudioMode.VOICE_CALL) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } catch (_: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        attachEffects(rec.audioSessionId)
        record = rec
        running.set(true)
        rec.startRecording()
        thread = Thread({
            val buffer = ByteArray(chunkBytes)
            while (running.get()) {
                var filled = 0
                while (filled < chunkBytes && running.get()) {
                    val n = rec.read(buffer, filled, chunkBytes - filled)
                    if (n <= 0) break
                    filled += n
                }
                if (filled > 0 && !muted) onChunk(buffer, filled)
            }
        }, "waifu-mic").also { it.start() }
        return true
    }

    private fun attachEffects(sessionId: Int) {
        try {
            if (ModelConfig.USE_ACOUSTIC_ECHO_CANCELER && AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.let { it.enabled = true; effects.add(it) }
            }
            if (ModelConfig.USE_NOISE_SUPPRESSOR && NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.let { it.enabled = true; effects.add(it) }
            }
            if (ModelConfig.USE_AUTOMATIC_GAIN_CONTROL && AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.let { it.enabled = true; effects.add(it) }
            }
        } catch (_: Exception) {
            // Effects are best-effort; recording works without them.
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try {
            thread?.join(500)
        } catch (_: InterruptedException) {
        }
        thread = null
        for (e in effects) {
            try {
                e.release()
            } catch (_: Exception) {
            }
        }
        effects.clear()
        record?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        record = null
    }
}
