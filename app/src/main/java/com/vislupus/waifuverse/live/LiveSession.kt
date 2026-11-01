package com.vislupus.waifuverse.live

/**
 * One websocket voice session with an AI provider (Gemini Live or OpenAI
 * Realtime). The manager only talks to this interface.
 */
interface LiveSession {
    /** Model name this session was opened with. */
    val model: String

    /** True once the server accepted the setup and audio can flow. */
    val setupDone: Boolean

    /** Sample rate the microphone must deliver for this provider (PCM16 mono). */
    val inputSampleRate: Int

    fun open()

    /** Sends raw PCM16 mono audio recorded at [inputSampleRate]. */
    fun sendAudio(pcm: ByteArray, length: Int = pcm.size)

    /** Tells the provider the microphone stream stopped (if it needs to know). */
    fun sendAudioStreamEnd()

    /** Sends a typed user message as a complete turn. */
    fun sendText(text: String)

    fun close()

    /** Callbacks are invoked on the websocket reader thread. */
    interface Listener {
        fun onSetupComplete(session: LiveSession)
        fun onAudio(session: LiveSession, pcm: ByteArray)
        fun onInputTranscript(session: LiveSession, text: String)
        fun onOutputTranscript(session: LiveSession, text: String)
        fun onTurnComplete(session: LiveSession)
        fun onInterrupted(session: LiveSession)
        fun onGoAway(session: LiveSession, timeLeft: String)
        fun onResumptionHandle(session: LiveSession, handle: String)
        fun onClosed(session: LiveSession, code: Int, reason: String)
        fun onFailure(session: LiveSession, error: Throwable, httpCode: Int?, body: String?)
    }
}
