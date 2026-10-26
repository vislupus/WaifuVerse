package com.vislupus.waifuverse

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.config.CharactersConfig
import com.vislupus.waifuverse.data.AppSettings
import com.vislupus.waifuverse.data.Conversation
import com.vislupus.waifuverse.data.Message
import com.vislupus.waifuverse.data.Role
import com.vislupus.waifuverse.data.Waifu
import com.vislupus.waifuverse.live.LiveSessionManager
import com.vislupus.waifuverse.live.SetupBuilder
import com.vislupus.waifuverse.sprite.SpriteLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Bottom navigation destinations. */
enum class AppTab { CHAT, PERSONA, HISTORY, SETTINGS }

/** Kinds of system notices shown inside the chat (translated by the UI). */
enum class NoticeKind { CONNECTED, MODEL_FALLBACK, SESSION_ENDED, SESSION_ERROR, ALL_MODELS_FAILED, API_KEY_MISSING, MIC_DENIED }

/** One row of the chat transcript. */
data class ChatLine(
    val id: Long,
    val role: Role,
    val text: String,
    /** Still being transcribed (not yet saved). */
    val pending: Boolean = false,
    val notice: NoticeKind? = null,
    val noticeArgs: Map<String, String> = emptyMap(),
)

/**
 * Holds all UI state and talks to the repository and the live session manager.
 * The Compose screens only read StateFlows and call the functions below.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WaifuVerseApp
    private val repo = app.repository
    private val spriteLoader: SpriteLoader = app.sprites
    val live = LiveSessionManager(application, viewModelScope)

    val settings: StateFlow<AppSettings> = repo.settings
    val waifus: StateFlow<List<Waifu>> = repo.waifus
    val conversations: StateFlow<List<Conversation>> = repo.conversations
    val databasePath: String = repo.databasePath

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _tab = MutableStateFlow(AppTab.CHAT)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _rememberCurrent = MutableStateFlow(AppConfig.DEFAULT_REMEMBER_CONVERSATIONS)
    val rememberCurrent: StateFlow<Boolean> = _rememberCurrent.asStateFlow()

    private val _chatLines = MutableStateFlow<List<ChatLine>>(emptyList())
    val chatLines: StateFlow<List<ChatLine>> = _chatLines.asStateFlow()

    private val _stageBackgroundId = MutableStateFlow(CharactersConfig.backgrounds.first().id)
    val stageBackgroundId: StateFlow<String> = _stageBackgroundId.asStateFlow()

    private val _sprite = MutableStateFlow<SpriteLoader.Sprite?>(null)
    val sprite: StateFlow<SpriteLoader.Sprite?> = _sprite.asStateFlow()

    private val _backgroundImage = MutableStateFlow<ImageBitmap?>(null)
    val backgroundImage: StateFlow<ImageBitmap?> = _backgroundImage.asStateFlow()

    /** Body image of every waifu, used for the round avatars. */
    private val _avatars = MutableStateFlow<Map<String, ImageBitmap?>>(emptyMap())
    val avatars: StateFlow<Map<String, ImageBitmap?>> = _avatars.asStateFlow()

    /** Waifu edited on the Persona page (defaults to the chat waifu). */
    private val _personaWaifuId = MutableStateFlow<String?>(null)
    val personaWaifuId: StateFlow<String?> = _personaWaifuId.asStateFlow()

    private var nextLineId = 1L
    private var pendingUserLineId: Long? = null
    private var pendingAssistantLineId: Long? = null

    val selectedWaifu: Waifu?
        get() = repo.waifu(settings.value.lastWaifuId) ?: waifus.value.firstOrNull()

    init {
        viewModelScope.launch {
            repo.initialize()
            _rememberCurrent.value = settings.value.rememberByDefault
            val w = selectedWaifu
            if (w != null) {
                _stageBackgroundId.value = w.backgroundId
                loadVisuals(w)
            }
            val lastId = settings.value.lastConversationId
            if (lastId > 0) {
                val c = repo.conversation(lastId)
                if (c != null && c.waifuId == settings.value.lastWaifuId) showConversation(c)
            }
            _ready.value = true
            val map = HashMap<String, ImageBitmap?>()
            for (waifu in waifus.value) {
                map[waifu.id] = spriteLoader.sprite(waifu.id)?.body
                _avatars.value = HashMap(map)
            }
        }
        viewModelScope.launch { live.events.collect { handleLiveEvent(it) } }
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    fun selectTab(tab: AppTab) {
        _tab.value = tab
    }

    // ------------------------------------------------------------------
    // Waifu / stage
    // ------------------------------------------------------------------

    fun selectWaifu(id: String) {
        if (id == settings.value.lastWaifuId) return
        val w = repo.waifu(id) ?: return
        stopSession()
        _currentConversation.value = null
        _chatLines.value = emptyList()
        resetPending()
        _rememberCurrent.value = settings.value.rememberByDefault
        _stageBackgroundId.value = w.backgroundId
        viewModelScope.launch {
            repo.updateSettings { it.copy(lastWaifuId = id, lastConversationId = 0L) }
            loadVisuals(w)
        }
    }

    fun setStageBackground(id: String) {
        _stageBackgroundId.value = id
        viewModelScope.launch { _backgroundImage.value = spriteLoader.background(CharactersConfig.background(id).file) }
    }

    private suspend fun loadVisuals(w: Waifu) {
        _sprite.value = spriteLoader.sprite(w.id)
        _backgroundImage.value = spriteLoader.background(CharactersConfig.background(_stageBackgroundId.value).file)
    }

    // ------------------------------------------------------------------
    // Conversations
    // ------------------------------------------------------------------

    fun setRememberCurrent(remembered: Boolean) {
        _rememberCurrent.value = remembered
        val c = _currentConversation.value ?: return
        viewModelScope.launch {
            repo.setRemembered(c.id, remembered)
            _currentConversation.value = c.copy(remembered = remembered)
        }
    }

    fun newConversation() {
        stopSession()
        _currentConversation.value = null
        _chatLines.value = emptyList()
        resetPending()
        _rememberCurrent.value = settings.value.rememberByDefault
        viewModelScope.launch { repo.updateSettings { it.copy(lastConversationId = 0L) } }
    }

    /** Opens a conversation from History and switches to the Chat tab. */
    fun openConversation(id: Long) {
        viewModelScope.launch {
            val c = repo.conversation(id) ?: return@launch
            stopSession()
            val w = repo.waifu(c.waifuId)
            if (w != null && w.id != settings.value.lastWaifuId) {
                _stageBackgroundId.value = w.backgroundId
                loadVisuals(w)
            }
            repo.updateSettings { it.copy(lastWaifuId = c.waifuId, lastConversationId = c.id) }
            showConversation(c)
            _tab.value = AppTab.CHAT
        }
    }

    private suspend fun showConversation(c: Conversation) {
        _currentConversation.value = c
        _rememberCurrent.value = c.remembered
        resetPending()
        _chatLines.value = repo.messages(c.id).map { it.toLine() }
    }

    fun toggleConversationRemembered(c: Conversation) {
        viewModelScope.launch {
            repo.setRemembered(c.id, !c.remembered)
            if (_currentConversation.value?.id == c.id) {
                _currentConversation.value = c.copy(remembered = !c.remembered)
                _rememberCurrent.value = !c.remembered
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (_currentConversation.value?.id == id) newConversation()
            repo.deleteConversation(id)
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            newConversation()
            repo.deleteAllConversations()
        }
    }

    private suspend fun ensureConversation(): Conversation? {
        _currentConversation.value?.let { return it }
        val w = selectedWaifu ?: return null
        val c = repo.createConversation(w.id, _rememberCurrent.value)
        _currentConversation.value = c
        repo.updateSettings { it.copy(lastConversationId = c.id) }
        return c
    }

    // ------------------------------------------------------------------
    // Live session
    // ------------------------------------------------------------------

    /** Starts a voice session. [micGranted] = RECORD_AUDIO permission state. Returns false if it cannot start. */
    fun startSession(micGranted: Boolean): Boolean {
        if (live.isSessionActive) return true
        val s = settings.value
        if (s.apiKey.isBlank()) {
            addNotice(NoticeKind.API_KEY_MISSING)
            return false
        }
        val w = selectedWaifu ?: return false
        if (!micGranted) addNotice(NoticeKind.MIC_DENIED)
        viewModelScope.launch {
            val conversation = ensureConversation() ?: return@launch
            val memory = repo.memoryFor(w.id, conversation.id)
            val resume = repo.resumeContextFor(conversation.id)
            val prompt = SetupBuilder.systemPrompt(w, memory, resume)
            live.start(
                apiKey = s.apiKey,
                modelChain = s.modelChain(),
                waifu = w,
                systemPrompt = prompt,
                audioMode = s.audioMode,
                muteMicWhileSpeaking = s.muteMicWhileSpeaking,
                autoStartMic = s.autoStartMic && micGranted,
            )
        }
        return true
    }

    fun stopSession() {
        if (!live.isSessionActive) return
        finalizePending(interrupted = false)
        live.stop()
    }

    fun setMicEnabled(enabled: Boolean) = live.setMicEnabled(enabled)

    /** Sends a typed message; starts a session first if needed. */
    fun sendText(text: String, micGranted: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!live.isSessionActive && !startSession(micGranted)) return
        viewModelScope.launch {
            val c = ensureConversation() ?: return@launch
            val m = repo.addMessage(c, Role.USER, clean)
            _currentConversation.value = repo.conversation(c.id) ?: c
            _chatLines.update { it + m.toLine() }
            live.sendText(clean)
        }
    }

    private fun handleLiveEvent(e: LiveSessionManager.Event) {
        when (e) {
            is LiveSessionManager.Event.UserTranscript -> appendPending(Role.USER, e.delta)
            is LiveSessionManager.Event.AssistantTranscript -> appendPending(Role.ASSISTANT, e.delta)
            LiveSessionManager.Event.TurnComplete -> finalizePending(interrupted = false)
            LiveSessionManager.Event.Interrupted -> finalizeAssistant(interrupted = true)
            is LiveSessionManager.Event.Connected -> addNotice(NoticeKind.CONNECTED, mapOf("model" to e.model))
            is LiveSessionManager.Event.ModelFallback ->
                addNotice(NoticeKind.MODEL_FALLBACK, mapOf("from" to e.from, "to" to e.to))
            is LiveSessionManager.Event.Ended -> {
                finalizePending(interrupted = false)
                when {
                    e.allModelsFailed -> addNotice(NoticeKind.ALL_MODELS_FAILED)
                    e.error != null -> addNotice(NoticeKind.SESSION_ERROR, mapOf("error" to e.error))
                    else -> addNotice(NoticeKind.SESSION_ENDED)
                }
            }
        }
    }

    private fun appendPending(role: Role, delta: String) {
        val pendingId = if (role == Role.USER) pendingUserLineId else pendingAssistantLineId
        if (pendingId == null) {
            val line = ChatLine(nextLineId++, role, delta, pending = true)
            if (role == Role.USER) pendingUserLineId = line.id else pendingAssistantLineId = line.id
            _chatLines.update { it + line }
        } else {
            _chatLines.update { lines -> lines.map { if (it.id == pendingId) it.copy(text = it.text + delta) else it } }
        }
    }

    /** Saves the pending user and assistant lines to the database. */
    private fun finalizePending(interrupted: Boolean) {
        finalizeLine(pendingUserLineId, Role.USER, false)
        pendingUserLineId = null
        finalizeAssistant(interrupted)
    }

    private fun finalizeAssistant(interrupted: Boolean) {
        finalizeLine(pendingAssistantLineId, Role.ASSISTANT, interrupted)
        pendingAssistantLineId = null
    }

    private fun finalizeLine(lineId: Long?, role: Role, interrupted: Boolean) {
        val id = lineId ?: return
        val line = _chatLines.value.firstOrNull { it.id == id } ?: return
        val text = line.text.trim()
        if (text.isEmpty()) {
            _chatLines.update { lines -> lines.filter { it.id != id } }
            return
        }
        _chatLines.update { lines -> lines.map { if (it.id == id) it.copy(text = text, pending = false) else it } }
        viewModelScope.launch {
            val c = ensureConversation() ?: return@launch
            repo.addMessage(c, role, text)
            if (c.title.isBlank() && role == Role.USER) {
                _currentConversation.value = repo.conversation(c.id) ?: c
            }
        }
    }

    private fun resetPending() {
        pendingUserLineId = null
        pendingAssistantLineId = null
    }

    private fun addNotice(kind: NoticeKind, args: Map<String, String> = emptyMap()) {
        _chatLines.update { it + ChatLine(nextLineId++, Role.SYSTEM, "", notice = kind, noticeArgs = args) }
    }

    private fun Message.toLine() = ChatLine(id = nextLineId++, role = role, text = text)

    // ------------------------------------------------------------------
    // Persona
    // ------------------------------------------------------------------

    fun selectPersonaWaifu(id: String) {
        _personaWaifuId.value = id
    }

    fun saveWaifu(w: Waifu) {
        viewModelScope.launch {
            repo.saveWaifu(w)
            if (w.id == settings.value.lastWaifuId) {
                _sprite.value = spriteLoader.sprite(w.id)
            }
        }
    }

    fun resetWaifu(id: String) {
        viewModelScope.launch { repo.resetWaifu(id) }
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }

    override fun onCleared() {
        live.stop()
        super.onCleared()
    }
}
