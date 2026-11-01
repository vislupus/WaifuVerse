package com.vislupus.waifuverse.live

import android.util.Base64
import com.vislupus.waifuverse.config.ModelConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One websocket connection to the OpenAI Realtime API.
 *
 * Wire format (JSON events):
 *   client -> server : session.update, input_audio_buffer.append, conversation.item.create, response.create
 *   server -> client : session.updated, response.output_audio.delta, response.output_audio_transcript.delta,
 *                      conversation.item.input_audio_transcription.*, input_audio_buffer.speech_started,
 *                      response.done, error
 * Audio is PCM16 mono at 24 kHz in both directions.
 */
class OpenAiRealtimeSession(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    override val model: String,
    private val sessionUpdate: JSONObject,
    /** Older "session.update" shape, tried once if the server rejects the current one. */
    private val legacySessionUpdate: JSONObject?,
    private val listener: LiveSession.Listener,
) : LiveSession {

    @Volatile
    private var legacyTried = false

    private var webSocket: WebSocket? = null
    private val closed = AtomicBoolean(false)

    @Volatile
    override var setupDone: Boolean = false
        private set

    override val inputSampleRate: Int = ModelConfig.OPENAI_SAMPLE_RATE

    /** True while a user turn is being transcribed with deltas (so the final transcript is not repeated). */
    @Volatile
    private var userDeltaSeen = false

    override fun open() {
        val request = Request.Builder()
            .url("${ModelConfig.OPENAI_REALTIME_ENDPOINT}?model=$model")
            .header("Authorization", "Bearer $apiKey")
            .build()
        webSocket = httpClient.newWebSocket(request, socketListener)
    }

    override fun sendAudio(pcm: ByteArray, length: Int) {
        val ws = webSocket ?: return
        if (!setupDone) return
        val data = Base64.encodeToString(pcm, 0, length, Base64.NO_WRAP)
        ws.send(JSONObject().put("type", "input_audio_buffer.append").put("audio", data).toString())
    }

    override fun sendAudioStreamEnd() {
        // Server-side voice activity detection ends turns by itself; nothing to do.
    }

    override fun sendText(text: String) {
        val ws = webSocket ?: return
        val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", text))
        val item = JSONObject()
            .put("type", "message")
            .put("role", "user")
            .put("content", content)
        ws.send(JSONObject().put("type", "conversation.item.create").put("item", item).toString())
        ws.send(JSONObject().put("type", "response.create").toString())
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                webSocket?.close(1000, "bye")
            } catch (_: Exception) {
            }
            webSocket = null
        }
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(sessionUpdate.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleMessage(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (closed.compareAndSet(false, true)) listener.onClosed(this@OpenAiRealtimeSession, code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closed.compareAndSet(false, true)) {
                val body = try {
                    response?.body?.string()
                } catch (_: Exception) {
                    null
                }
                listener.onFailure(this@OpenAiRealtimeSession, t, response?.code, body)
            }
        }
    }

    private fun handleMessage(raw: String) {
        val msg = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return
        }
        when (msg.optString("type", "")) {
            "session.updated" -> {
                if (!setupDone) {
                    setupDone = true
                    listener.onSetupComplete(this)
                }
            }
            "error" -> {
                val err = msg.optJSONObject("error")
                val message = err?.optString("message", "") ?: ""
                val code = err?.optString("code", "") ?: ""
                if (!setupDone) {
                    // The setup was rejected. Try the older session format once, then give up.
                    val legacy = legacySessionUpdate
                    if (legacy != null && !legacyTried) {
                        legacyTried = true
                        webSocket?.send(legacy.toString())
                    } else {
                        closeWithFailure(IllegalStateException("$code $message".trim()))
                    }
                }
                // Errors after setup (e.g. an empty audio buffer) are not fatal; ignore them.
            }
            "response.output_audio.delta", "response.audio.delta" -> {
                val delta = msg.optString("delta", "")
                if (delta.isNotEmpty()) listener.onAudio(this, Base64.decode(delta, Base64.DEFAULT))
            }
            "response.output_audio_transcript.delta", "response.audio_transcript.delta" -> {
                val delta = msg.optString("delta", "")
                if (delta.isNotEmpty()) listener.onOutputTranscript(this, delta)
            }
            "conversation.item.input_audio_transcription.delta" -> {
                val delta = msg.optString("delta", "")
                if (delta.isNotEmpty()) {
                    userDeltaSeen = true
                    listener.onInputTranscript(this, delta)
                }
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = msg.optString("transcript", "")
                if (!userDeltaSeen && transcript.isNotEmpty()) listener.onInputTranscript(this, transcript)
                userDeltaSeen = false
            }
            "input_audio_buffer.speech_started" -> {
                // The server cancels the running response itself (interrupt_response = true).
                userDeltaSeen = false
                listener.onInterrupted(this)
            }
            "response.done" -> listener.onTurnComplete(this)
        }
    }

    private fun closeWithFailure(error: Throwable) {
        if (closed.compareAndSet(false, true)) {
            try {
                webSocket?.close(1000, "setup rejected")
            } catch (_: Exception) {
            }
            webSocket = null
            listener.onFailure(this, error, null, null)
        }
    }
}
