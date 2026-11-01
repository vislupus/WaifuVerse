package com.vislupus.waifuverse.live

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.config.AudioMode
import com.vislupus.waifuverse.config.ModelConfig
import com.vislupus.waifuverse.config.Provider
import com.vislupus.waifuverse.data.Waifu
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Runs one live voice session: picks a working model from the chain,
 * streams the microphone up, plays the voice down, computes the mouth frame
 * for lip sync and forwards transcripts as [Event]s.
 *
 * It knows nothing about the database or the UI.
 */
class LiveSessionManager(context: Context, private val scope: CoroutineScope) {

    enum class Status { IDLE, CONNECTING, LIVE }

    sealed interface Event {
        data class UserTranscript(val delta: String) : Event
        data class AssistantTranscript(val delta: String) : Event
        object TurnComplete : Event
        object Interrupted : Event
        data class ModelFallback(val from: String, val to: String) : Event
        data class Connected(val model: String) : Event
        data class Ended(val error: String?, val allModelsFailed: Boolean = false) : Event
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _activeModel = MutableStateFlow<String?>(null)
    val activeModel: StateFlow<String?> = _activeModel.asStateFlow()

    private val _micOn = MutableStateFlow(false)
    val micOn: StateFlow<Boolean> = _micOn.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _mouthFrame = MutableStateFlow(0)
    val mouthFrame: StateFlow<Int> = _mouthFrame.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 256)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    // Session state (accessed from several threads; guarded by @Volatile + simple ownership).
    @Volatile
    private var session: LiveSession? = null
    private var input: AudioInput? = null
    private var output: AudioOutput? = null
    private var tickerJob: Job? = null
    private var connectJob: Job? = null
    private val pendingTexts = ArrayList<String>()

    @Volatile
    private var resumptionHandle: String? = null

    @Volatile
    private var reconnecting = false

    private var apiKey = ""
    private var provider = ModelConfig.DEFAULT_PROVIDER
    private var waifu: Waifu? = null
    private var systemPrompt = ""
    private var audioMode = ModelConfig.DEFAULT_AUDIO_MODE
    private var muteMicWhileSpeaking = false
    private var autoStartMic = true

    val isSessionActive: Boolean get() = _status.value != Status.IDLE

    fun start(
        provider: Provider,
        apiKey: String,
        modelChain: List<String>,
        waifu: Waifu,
        systemPrompt: String,
        audioMode: AudioMode,
        muteMicWhileSpeaking: Boolean,
        autoStartMic: Boolean,
    ) {
        if (isSessionActive) return
        this.provider = provider
        this.apiKey = apiKey
        this.waifu = waifu
        this.systemPrompt = systemPrompt
        this.audioMode = audioMode
        // MEDIA mode has no hardware echo cancellation: without half-duplex she hears herself.
        this.muteMicWhileSpeaking = muteMicWhileSpeaking ||
            (audioMode == AudioMode.MEDIA && ModelConfig.MEDIA_MODE_FORCES_HALF_DUPLEX)
        this.autoStartMic = autoStartMic
        this.resumptionHandle = null
        _status.value = Status.CONNECTING
        _activeModel.value = null

        connectJob = scope.launch(Dispatchers.IO) {
            var connected: LiveSession? = null
            for ((index, model) in modelChain.withIndex()) {
                if (!isSessionActive) return@launch
                when (val result = connectOnce(model, null)) {
                    is ConnectResult.Success -> {
                        connected = result.session
                        break
                    }
                    is ConnectResult.Fatal -> {
                        finish(result.reason)
                        return@launch
                    }
                    is ConnectResult.Failed -> {
                        val next = modelChain.getOrNull(index + 1)
                        if (next != null) _events.tryEmit(Event.ModelFallback(model, next))
                    }
                }
            }
            if (connected == null) {
                finish(null, allModelsFailed = true)
                return@launch
            }
            session = connected
            _activeModel.value = connected.model
            configureAudioRoute(true)
            output = AudioOutput(audioMode).also { it.start() }
            _status.value = Status.LIVE
            _events.tryEmit(Event.Connected(connected.model))
            flushPendingTexts()
            if (autoStartMic) setMicEnabled(true)
            startTicker()
        }
    }

    private sealed interface ConnectResult {
        data class Success(val session: LiveSession) : ConnectResult
        data class Failed(val reason: String) : ConnectResult
        data class Fatal(val reason: String) : ConnectResult
    }

    /** Opens one websocket and waits for setupComplete (or a failure). */
    private suspend fun connectOnce(model: String, handle: String?): ConnectResult {
        val w = waifu ?: return ConnectResult.Fatal("no waifu")
        val deferred = CompletableDeferred<ConnectResult>()
        val s: LiveSession = when (provider) {
            Provider.GEMINI -> GeminiLiveSession(
                httpClient, apiKey, model,
                SetupBuilder.setupMessage(model, w, systemPrompt, handle),
                SessionListener(deferred),
            )
            Provider.OPENAI -> OpenAiRealtimeSession(
                httpClient, apiKey, model,
                SetupBuilder.openAiSessionUpdate(w, systemPrompt),
                SessionListener(deferred),
            )
        }
        s.open()
        val result = withTimeoutOrNull(AppConfig.SETUP_TIMEOUT_SECONDS * 1000) { deferred.await() }
        if (result == null) {
            s.close()
            return ConnectResult.Failed("timeout")
        }
        return result
    }

    /** Handles callbacks from one websocket; before setup they resolve [setupResult]. */
    private inner class SessionListener(
        private val setupResult: CompletableDeferred<ConnectResult>,
    ) : LiveSession.Listener {

        private fun isCurrent(s: LiveSession) = session === s

        override fun onSetupComplete(session: LiveSession) {
            setupResult.complete(ConnectResult.Success(session))
        }

        override fun onAudio(session: LiveSession, pcm: ByteArray) {
            output?.enqueue(pcm)
        }

        override fun onInputTranscript(session: LiveSession, text: String) {
            _events.tryEmit(Event.UserTranscript(text))
        }

        override fun onOutputTranscript(session: LiveSession, text: String) {
            _events.tryEmit(Event.AssistantTranscript(text))
        }

        override fun onTurnComplete(session: LiveSession) {
            _events.tryEmit(Event.TurnComplete)
        }

        override fun onInterrupted(session: LiveSession) {
            output?.flush()
            _events.tryEmit(Event.Interrupted)
        }

        override fun onGoAway(session: LiveSession, timeLeft: String) {
            if (!isCurrent(session)) return
            val handle = resumptionHandle
            if (handle != null && !reconnecting) {
                reconnecting = true
                scope.launch(Dispatchers.IO) { reconnect(session.model, handle) }
            }
        }

        override fun onResumptionHandle(session: LiveSession, handle: String) {
            if (isCurrent(session) || !setupResult.isCompleted) resumptionHandle = handle
        }

        override fun onClosed(session: LiveSession, code: Int, reason: String) {
            if (!setupResult.isCompleted) {
                setupResult.complete(classify(code, reason, null))
                return
            }
            if (isCurrent(session) && !reconnecting) finish(if (code == 1000) null else "$code $reason")
        }

        override fun onFailure(session: LiveSession, error: Throwable, httpCode: Int?, body: String?) {
            if (!setupResult.isCompleted) {
                setupResult.complete(classify(httpCode ?: -1, error.message ?: "", body))
                return
            }
            if (isCurrent(session) && !reconnecting) finish(error.message ?: error.javaClass.simpleName)
        }

        /** Decides whether an error is model-specific (try next) or fatal (bad key / no network). */
        private fun classify(code: Int, reason: String, body: String?): ConnectResult {
            val text = (reason + " " + (body ?: "")).lowercase()
            val keyProblem = text.contains("api key") || text.contains("api_key") ||
                text.contains("permission_denied") || text.contains("unauthenticated") ||
                text.contains("invalid_api_key") || text.contains("incorrect api key") ||
                code == 401 || code == 403
            if (keyProblem) return ConnectResult.Fatal(reason.ifBlank { "invalid API key" })
            val network = text.contains("unable to resolve host") || text.contains("failed to connect") ||
                text.contains("network is unreachable") || text.contains("timeout")
            if (network) return ConnectResult.Fatal(reason.ifBlank { "network error" })
            return ConnectResult.Failed(reason)
        }
    }

    /** Seamlessly moves to a new websocket using the session resumption handle (after goAway). */
    private suspend fun reconnect(model: String, handle: String) {
        val old = session
        when (val r = connectOnce(model, handle)) {
            is ConnectResult.Success -> {
                session = r.session
                reconnecting = false
                old?.close()
                flushPendingTexts()
            }
            else -> {
                reconnecting = false
                finish("reconnect failed")
            }
        }
    }

    fun sendText(text: String) {
        val s = session
        if (s != null && s.setupDone && !reconnecting) {
            s.sendText(text)
        } else {
            synchronized(pendingTexts) { pendingTexts.add(text) }
        }
    }

    private fun flushPendingTexts() {
        val s = session ?: return
        val copy: List<String>
        synchronized(pendingTexts) {
            copy = ArrayList(pendingTexts)
            pendingTexts.clear()
        }
        for (t in copy) s.sendText(t)
    }

    /** Starts/stops the microphone stream. */
    fun setMicEnabled(enabled: Boolean) {
        if (!isSessionActive) return
        if (enabled) {
            if (input?.isRunning == true) return
            val rate = session?.inputSampleRate ?: ModelConfig.INPUT_SAMPLE_RATE
            val mic = AudioInput(audioMode, rate) { buf, len -> session?.sendAudio(buf, len) }
            if (mic.start()) {
                input = mic
                _micOn.value = true
            }
        } else {
            input?.stop()
            input = null
            _micOn.value = false
            session?.sendAudioStreamEnd()
        }
    }

    fun stop() {
        if (!isSessionActive) return
        finish(null)
    }

    /** Tears everything down and emits [Event.Ended]. Safe to call from any thread. */
    private fun finish(error: String?, allModelsFailed: Boolean = false) {
        if (_status.value == Status.IDLE) return
        _status.value = Status.IDLE
        connectJob = null
        tickerJob?.cancel()
        tickerJob = null
        val s = session
        session = null
        s?.close()
        input?.stop()
        input = null
        output?.stop()
        output = null
        reconnecting = false
        synchronized(pendingTexts) { pendingTexts.clear() }
        configureAudioRoute(false)
        _micOn.value = false
        _speaking.value = false
        _mouthFrame.value = 0
        _events.tryEmit(Event.Ended(error, allModelsFailed))
    }

    /** Lip sync + "speaking" state + auto-mute, re-evaluated every few milliseconds. */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            var smoothed = 0f
            var lastPlayingAt = 0L
            while (isSessionActive && isActive) {
                val out = output
                val level = out?.currentLevel() ?: 0f
                smoothed = if (level > smoothed) {
                    level * AppConfig.LIP_ATTACK + smoothed * (1f - AppConfig.LIP_ATTACK)
                } else {
                    smoothed * (1f - AppConfig.LIP_DECAY)
                }
                val frame = when {
                    smoothed > AppConfig.LIP_LEVEL_WIDE -> 3
                    smoothed > AppConfig.LIP_LEVEL_MEDIUM -> 2
                    smoothed > AppConfig.LIP_LEVEL_SMALL -> 1
                    else -> 0
                }
                if (_mouthFrame.value != frame) _mouthFrame.value = frame
                val playing = out?.isPlaying() ?: false
                if (_speaking.value != playing) _speaking.value = playing
                val now = System.currentTimeMillis()
                if (playing) lastPlayingAt = now
                // Half duplex: stay muted a little after she stops so the echo tail is not heard as speech.
                val muteNow = muteMicWhileSpeaking && (playing || now - lastPlayingAt < ModelConfig.MIC_UNMUTE_DELAY_MS)
                input?.let { if (it.muted != muteNow) it.muted = muteNow }
                delay(AppConfig.LIP_FRAME_INTERVAL_MS)
            }
        }
    }

    /** Puts the phone in call mode with the loudspeaker (VOICE_CALL) or restores normal mode. */
    private fun configureAudioRoute(active: Boolean) {
        try {
            if (active && audioMode == AudioMode.VOICE_CALL) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (ModelConfig.USE_SPEAKERPHONE) setSpeaker(true)
            } else if (active) {
                audioManager.mode = AudioManager.MODE_NORMAL
            } else {
                if (audioMode == AudioMode.VOICE_CALL && ModelConfig.USE_SPEAKERPHONE) setSpeaker(false)
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                val speaker = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) audioManager.setCommunicationDevice(speaker)
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            audioManager.isSpeakerphoneOn = on
        }
    }
}
