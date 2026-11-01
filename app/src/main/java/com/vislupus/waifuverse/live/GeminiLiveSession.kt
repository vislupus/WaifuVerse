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
 * One websocket connection to the Gemini Live API (BidiGenerateContent).
 *
 * Wire format (JSON, v1beta):
 *   client -> server : {"setup": {...}}, {"realtimeInput": {"audio": {...}}}, {"clientContent": {...}}
 *   server -> client : {"setupComplete": {}}, {"serverContent": {...}}, {"goAway": {...}}, ...
 */
class GeminiLiveSession(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    override val model: String,
    private val setupMessage: JSONObject,
    private val listener: LiveSession.Listener,
) : LiveSession {

    private var webSocket: WebSocket? = null
    private val closed = AtomicBoolean(false)

    @Volatile
    override var setupDone: Boolean = false
        private set

    override val inputSampleRate: Int = ModelConfig.INPUT_SAMPLE_RATE

    override fun open() {
        val request = Request.Builder()
            .url("${ModelConfig.LIVE_ENDPOINT}?key=$apiKey")
            .build()
        webSocket = httpClient.newWebSocket(request, socketListener)
    }

    override fun sendAudio(pcm: ByteArray, length: Int) {
        val ws = webSocket ?: return
        if (!setupDone) return
        val data = Base64.encodeToString(pcm, 0, length, Base64.NO_WRAP)
        val audio = JSONObject()
            .put("data", data)
            .put("mimeType", "audio/pcm;rate=${ModelConfig.INPUT_SAMPLE_RATE}")
        val msg = JSONObject().put("realtimeInput", JSONObject().put("audio", audio))
        ws.send(msg.toString())
    }

    override fun sendAudioStreamEnd() {
        val ws = webSocket ?: return
        if (!setupDone) return
        ws.send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString())
    }

    override fun sendText(text: String) {
        val ws = webSocket ?: return
        val turn = JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", text)))
        val content = JSONObject()
            .put("turns", JSONArray().put(turn))
            .put("turnComplete", true)
        ws.send(JSONObject().put("clientContent", content).toString())
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
            webSocket.send(setupMessage.toString())
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
            if (closed.compareAndSet(false, true)) listener.onClosed(this@GeminiLiveSession, code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closed.compareAndSet(false, true)) {
                val body = try {
                    response?.body?.string()
                } catch (_: Exception) {
                    null
                }
                listener.onFailure(this@GeminiLiveSession, t, response?.code, body)
            }
        }
    }

    private fun handleMessage(raw: String) {
        val msg = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return
        }
        if (msg.has("setupComplete")) {
            setupDone = true
            listener.onSetupComplete(this)
            return
        }
        msg.optJSONObject("serverContent")?.let { handleServerContent(it) }
        msg.optJSONObject("goAway")?.let { listener.onGoAway(this, it.optString("timeLeft", "")) }
        msg.optJSONObject("sessionResumptionUpdate")?.let {
            val handle = it.optString("newHandle", "")
            if (it.optBoolean("resumable", false) && handle.isNotEmpty()) listener.onResumptionHandle(this, handle)
        }
    }

    private fun handleServerContent(sc: JSONObject) {
        if (sc.optBoolean("interrupted", false)) listener.onInterrupted(this)
        sc.optJSONObject("inputTranscription")?.optString("text", "")?.let {
            if (it.isNotEmpty()) listener.onInputTranscript(this, it)
        }
        sc.optJSONObject("outputTranscription")?.optString("text", "")?.let {
            if (it.isNotEmpty()) listener.onOutputTranscript(this, it)
        }
        sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val inline = part.optJSONObject("inlineData") ?: continue
                val mime = inline.optString("mimeType", "")
                val data = inline.optString("data", "")
                if (data.isNotEmpty() && mime.startsWith("audio/pcm")) {
                    listener.onAudio(this, Base64.decode(data, Base64.DEFAULT))
                }
            }
        }
        if (sc.optBoolean("turnComplete", false)) listener.onTurnComplete(this)
    }
}
