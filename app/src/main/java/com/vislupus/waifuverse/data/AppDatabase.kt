package com.vislupus.waifuverse.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.config.CharactersConfig
import com.vislupus.waifuverse.config.ModelConfig
import com.vislupus.waifuverse.config.Provider
import java.io.File

/**
 * Plain SQLite database living inside the app sandbox:
 *   <filesDir>/data/waifuverse.db
 *
 * Tables: settings (key/value), waifus, conversations, messages.
 * All methods are synchronous; call them from a background dispatcher.
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    databaseFile(context).absolutePath,
    null,
    AppConfig.DATABASE_VERSION,
) {

    companion object {
        fun databaseFile(context: Context): File {
            val dir = File(context.filesDir, AppConfig.DATA_DIR)
            if (!dir.exists()) dir.mkdirs()
            return File(dir, AppConfig.DATABASE_FILE)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE settings (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE waifus (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                title TEXT NOT NULL,
                voice TEXT NOT NULL,
                provider TEXT NOT NULL DEFAULT 'gemini',
                openai_voice TEXT NOT NULL DEFAULT 'marin',
                background_id TEXT NOT NULL,
                accent_hex TEXT NOT NULL,
                persona TEXT NOT NULL,
                extra_instructions TEXT NOT NULL DEFAULT '',
                temperature REAL NOT NULL,
                top_p REAL NOT NULL,
                top_k INTEGER NOT NULL,
                max_output_tokens INTEGER NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                waifu_id TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                remembered INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id, id)")
        db.execSQL("CREATE INDEX idx_conversations_waifu ON conversations(waifu_id, updated_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add ALTER TABLE migrations here when DATABASE_VERSION is bumped.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE waifus ADD COLUMN provider TEXT NOT NULL DEFAULT 'gemini'")
            db.execSQL("ALTER TABLE waifus ADD COLUMN openai_voice TEXT NOT NULL DEFAULT '${ModelConfig.OPENAI_DEFAULT_VOICE}'")
        }
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    fun loadSettings(): Map<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT key, value FROM settings", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    fun saveSetting(key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveSettings(values: Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((k, v) in values) {
                val cv = ContentValues().apply {
                    put("key", k)
                    put("value", v)
                }
                db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------------
    // Waifus
    // ------------------------------------------------------------------

    /** Inserts the configured default waifus that are not in the table yet. */
    fun seedWaifus() {
        val existing = HashSet<String>()
        readableDatabase.rawQuery("SELECT id FROM waifus", null).use { c ->
            while (c.moveToNext()) existing.add(c.getString(0))
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            CharactersConfig.waifus.forEachIndexed { index, d ->
                if (d.id !in existing) {
                    val cv = waifuValues(Waifu.fromDefaults(d))
                    cv.put("sort_order", index)
                    db.insert("waifus", null, cv)
                } else {
                    val cv = ContentValues().apply { put("sort_order", index) }
                    db.update("waifus", cv, "id = ?", arrayOf(d.id))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadWaifus(): List<Waifu> {
        val out = ArrayList<Waifu>()
        readableDatabase.rawQuery("SELECT * FROM waifus ORDER BY sort_order, name", null).use { c ->
            while (c.moveToNext()) out.add(readWaifu(c))
        }
        return out
    }

    fun saveWaifu(w: Waifu) {
        val cv = waifuValues(w)
        val updated = writableDatabase.update("waifus", cv, "id = ?", arrayOf(w.id))
        if (updated == 0) writableDatabase.insert("waifus", null, cv)
    }

    private fun waifuValues(w: Waifu): ContentValues = ContentValues().apply {
        put("id", w.id)
        put("name", w.name)
        put("title", w.title)
        put("voice", w.voice)
        put("provider", w.provider.id)
        put("openai_voice", w.openAiVoice)
        put("background_id", w.backgroundId)
        put("accent_hex", w.accentHex)
        put("persona", w.persona)
        put("extra_instructions", w.extraInstructions)
        put("temperature", w.temperature)
        put("top_p", w.topP)
        put("top_k", w.topK)
        put("max_output_tokens", w.maxOutputTokens)
        put("updated_at", w.updatedAt)
    }

    private fun readWaifu(c: Cursor) = Waifu(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        voice = c.getString(c.getColumnIndexOrThrow("voice")),
        provider = Provider.fromId(c.getString(c.getColumnIndexOrThrow("provider"))),
        openAiVoice = c.getString(c.getColumnIndexOrThrow("openai_voice")),
        backgroundId = c.getString(c.getColumnIndexOrThrow("background_id")),
        accentHex = c.getString(c.getColumnIndexOrThrow("accent_hex")),
        persona = c.getString(c.getColumnIndexOrThrow("persona")),
        extraInstructions = c.getString(c.getColumnIndexOrThrow("extra_instructions")),
        temperature = c.getFloat(c.getColumnIndexOrThrow("temperature")),
        topP = c.getFloat(c.getColumnIndexOrThrow("top_p")),
        topK = c.getInt(c.getColumnIndexOrThrow("top_k")),
        maxOutputTokens = c.getInt(c.getColumnIndexOrThrow("max_output_tokens")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
    )

    // ------------------------------------------------------------------
    // Conversations
    // ------------------------------------------------------------------

    fun createConversation(waifuId: String, remembered: Boolean, now: Long = System.currentTimeMillis()): Conversation {
        val cv = ContentValues().apply {
            put("waifu_id", waifuId)
            put("title", "")
            put("remembered", if (remembered) 1 else 0)
            put("created_at", now)
            put("updated_at", now)
        }
        val id = writableDatabase.insert("conversations", null, cv)
        return Conversation(id, waifuId, "", remembered, now, now, 0)
    }

    fun loadConversations(limit: Int = AppConfig.HISTORY_PAGE_SIZE): List<Conversation> {
        val out = ArrayList<Conversation>()
        val sql = """SELECT c.id, c.waifu_id, c.title, c.remembered, c.created_at, c.updated_at,
                (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS cnt
                FROM conversations c ORDER BY c.updated_at DESC LIMIT ?"""
        readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) out.add(readConversation(c))
        }
        return out
    }

    fun loadConversation(id: Long): Conversation? {
        val sql = """SELECT c.id, c.waifu_id, c.title, c.remembered, c.created_at, c.updated_at,
                (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id) AS cnt
                FROM conversations c WHERE c.id = ?"""
        readableDatabase.rawQuery(sql, arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) readConversation(c) else null
        }
    }

    private fun readConversation(c: Cursor) = Conversation(
        id = c.getLong(0),
        waifuId = c.getString(1),
        title = c.getString(2),
        remembered = c.getInt(3) != 0,
        createdAt = c.getLong(4),
        updatedAt = c.getLong(5),
        messageCount = c.getInt(6),
    )

    fun setConversationRemembered(id: Long, remembered: Boolean) {
        val cv = ContentValues().apply { put("remembered", if (remembered) 1 else 0) }
        writableDatabase.update("conversations", cv, "id = ?", arrayOf(id.toString()))
    }

    fun setConversationTitle(id: Long, title: String) {
        val cv = ContentValues().apply { put("title", title) }
        writableDatabase.update("conversations", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteConversation(id: Long) {
        writableDatabase.delete("conversations", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllConversations() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", null, null)
            db.delete("conversations", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes conversations that never received a message (e.g. opened and abandoned). */
    fun deleteEmptyConversations() {
        writableDatabase.delete(
            "conversations",
            "id NOT IN (SELECT DISTINCT conversation_id FROM messages)",
            null,
        )
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    fun addMessage(conversationId: Long, role: Role, text: String, now: Long = System.currentTimeMillis()): Message {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("conversation_id", conversationId)
                put("role", role.id)
                put("text", text)
                put("created_at", now)
            }
            val id = db.insert("messages", null, cv)
            val up = ContentValues().apply { put("updated_at", now) }
            db.update("conversations", up, "id = ?", arrayOf(conversationId.toString()))
            db.setTransactionSuccessful()
            return Message(id, conversationId, role, text, now)
        } finally {
            db.endTransaction()
        }
    }

    fun loadMessages(conversationId: Long, limit: Int = Int.MAX_VALUE): List<Message> {
        val out = ArrayList<Message>()
        // Take the LAST `limit` messages but return them in chronological order.
        val sql = """SELECT id, conversation_id, role, text, created_at FROM (
                SELECT * FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT ?
            ) ORDER BY id ASC"""
        readableDatabase.rawQuery(sql, arrayOf(conversationId.toString(), limit.toString())).use { c ->
            while (c.moveToNext()) out.add(readMessage(c))
        }
        return out
    }

    /**
     * The most recent messages from REMEMBERED conversations with this waifu,
     * oldest first — this is the waifu's long-term memory.
     */
    fun loadMemory(waifuId: String, excludeConversationId: Long, limit: Int): List<Message> {
        val out = ArrayList<Message>()
        val sql = """SELECT id, conversation_id, role, text, created_at FROM (
                SELECT m.* FROM messages m
                JOIN conversations c ON c.id = m.conversation_id
                WHERE c.waifu_id = ? AND c.remembered = 1 AND c.id != ?
                  AND m.role IN ('user', 'assistant')
                ORDER BY m.id DESC LIMIT ?
            ) ORDER BY id ASC"""
        readableDatabase.rawQuery(
            sql,
            arrayOf(waifuId, excludeConversationId.toString(), limit.toString()),
        ).use { c ->
            while (c.moveToNext()) out.add(readMessage(c))
        }
        return out
    }

    private fun readMessage(c: Cursor) = Message(
        id = c.getLong(0),
        conversationId = c.getLong(1),
        role = Role.fromId(c.getString(2)),
        text = c.getString(3),
        createdAt = c.getLong(4),
    )
}
