package com.vislupus.waifuverse.data

import android.content.Context
import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.config.CharactersConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Single access point to the database. Keeps in-memory StateFlows that the UI
 * observes and refreshes them after every write. All suspend functions run on
 * the IO dispatcher.
 */
class AppRepository(context: Context) {

    val db = AppDatabase(context)
    val databasePath: String = AppDatabase.databaseFile(context).absolutePath

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _waifus = MutableStateFlow<List<Waifu>>(emptyList())
    val waifus: StateFlow<List<Waifu>> = _waifus.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    /** Loads everything from disk. Call once at start-up. */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        db.seedWaifus()
        db.deleteEmptyConversations()
        _settings.value = AppSettings.fromMap(db.loadSettings())
        _waifus.value = db.loadWaifus()
        _conversations.value = db.loadConversations()
    }

    // ---------------- settings ----------------

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        val updated = transform(_settings.value)
        _settings.value = updated
        db.saveSettings(updated.toMap())
    }

    // ---------------- waifus ----------------

    fun waifu(id: String): Waifu? = _waifus.value.firstOrNull { it.id == id }

    suspend fun saveWaifu(w: Waifu) = withContext(Dispatchers.IO) {
        db.saveWaifu(w.copy(updatedAt = System.currentTimeMillis()))
        _waifus.value = db.loadWaifus()
    }

    suspend fun resetWaifu(id: String) = withContext(Dispatchers.IO) {
        val defaults = CharactersConfig.waifus.firstOrNull { it.id == id } ?: return@withContext
        db.saveWaifu(Waifu.fromDefaults(defaults))
        _waifus.value = db.loadWaifus()
    }

    // ---------------- conversations ----------------

    suspend fun refreshConversations() = withContext(Dispatchers.IO) {
        _conversations.value = db.loadConversations()
    }

    suspend fun createConversation(waifuId: String, remembered: Boolean): Conversation = withContext(Dispatchers.IO) {
        val c = db.createConversation(waifuId, remembered)
        _conversations.value = db.loadConversations()
        c
    }

    suspend fun conversation(id: Long): Conversation? = withContext(Dispatchers.IO) { db.loadConversation(id) }

    suspend fun setRemembered(id: Long, remembered: Boolean) = withContext(Dispatchers.IO) {
        db.setConversationRemembered(id, remembered)
        _conversations.value = db.loadConversations()
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        db.deleteConversation(id)
        _conversations.value = db.loadConversations()
    }

    suspend fun deleteAllConversations() = withContext(Dispatchers.IO) {
        db.deleteAllConversations()
        _conversations.value = emptyList()
    }

    // ---------------- messages ----------------

    suspend fun messages(conversationId: Long): List<Message> = withContext(Dispatchers.IO) {
        db.loadMessages(conversationId)
    }

    /** Saves a message; the first user message also becomes the conversation title. */
    suspend fun addMessage(conversation: Conversation, role: Role, text: String): Message = withContext(Dispatchers.IO) {
        val m = db.addMessage(conversation.id, role, text)
        if (conversation.title.isBlank() && role == Role.USER) {
            db.setConversationTitle(conversation.id, autoTitle(text))
        }
        _conversations.value = db.loadConversations()
        m
    }

    suspend fun memoryFor(waifuId: String, excludeConversationId: Long): List<Message> = withContext(Dispatchers.IO) {
        db.loadMemory(waifuId, excludeConversationId, AppConfig.MEMORY_MAX_MESSAGES)
    }

    suspend fun resumeContextFor(conversationId: Long): List<Message> = withContext(Dispatchers.IO) {
        db.loadMessages(conversationId, AppConfig.RESUME_MAX_MESSAGES)
    }

    private fun autoTitle(text: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= AppConfig.AUTO_TITLE_MAX_CHARS) clean
        else clean.take(AppConfig.AUTO_TITLE_MAX_CHARS).trimEnd() + "…"
    }
}
