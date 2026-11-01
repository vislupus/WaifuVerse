package com.vislupus.waifuverse.config

/**
 * =====================================================================
 *  APP CONFIG — behaviour of the application.
 * =====================================================================
 *
 * Everything that controls HOW the app behaves is here. Appearance lives in
 * [ThemeConfig], texts in [TextsConfig], characters in [CharactersConfig] and
 * AI models / audio in [ModelConfig]. Change values here, rebuild, done.
 */
object AppConfig {

    /** Shown in the top bar and the launcher. */
    const val APP_NAME = "WaifuVerse"

    // ---------------------------------------------------------------
    // Storage (everything stays inside the app sandbox: filesDir)
    // ---------------------------------------------------------------

    /** Sub-folder of the app's private files directory where data is kept. */
    const val DATA_DIR = "data"

    /** SQLite database file name (created inside [DATA_DIR]). */
    const val DATABASE_FILE = "waifuverse.db"

    /** Bump when the SQL schema in AppDatabase changes. */
    const val DATABASE_VERSION = 2

    // ---------------------------------------------------------------
    // Defaults for user settings (changeable later from the Settings page)
    // ---------------------------------------------------------------

    val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
    const val DEFAULT_ACCENT_ID = "violet"          // see ThemeConfig.accents
    const val DEFAULT_FONT_SCALE_ID = "default"     // see ThemeConfig.fontScales
    val DEFAULT_LANGUAGE = Language.EN
    const val DEFAULT_WAIFU_ID = "mira"             // see CharactersConfig
    const val DEFAULT_REMEMBER_CONVERSATIONS = true

    // ---------------------------------------------------------------
    // Conversations & memory
    // ---------------------------------------------------------------

    /**
     * How many of the most recent REMEMBERED messages (across all remembered
     * conversations with the same waifu) are injected as memory when a new
     * live session starts.
     */
    const val MEMORY_MAX_MESSAGES = 60

    /** Hard cap on characters of memory text injected into the prompt. */
    const val MEMORY_MAX_CHARS = 12_000

    /** How many messages of the conversation being resumed are re-sent as context. */
    const val RESUME_MAX_MESSAGES = 80

    /** Keep temporary (not remembered) conversations in History so you can reopen them. */
    const val KEEP_TEMPORARY_IN_HISTORY = true

    /** Characters taken from the first user message to auto-title a conversation. */
    const val AUTO_TITLE_MAX_CHARS = 48

    /** Max rows loaded for the History page. */
    const val HISTORY_PAGE_SIZE = 200

    // ---------------------------------------------------------------
    // Live session behaviour
    // ---------------------------------------------------------------

    /** Keep the screen on while a voice session is active. */
    const val KEEP_SCREEN_ON_DURING_SESSION = true

    /** End the voice session when the app goes to the background. */
    const val STOP_SESSION_IN_BACKGROUND = true

    /** Automatically start the microphone right after the session connects. */
    const val AUTO_START_MIC_ON_CONNECT = true

    /** Seconds to wait for Gemini's "setupComplete" before trying the next model. */
    const val SETUP_TIMEOUT_SECONDS = 15L

    /** Seconds to wait for the websocket to open. */
    const val CONNECT_TIMEOUT_SECONDS = 15L

    // ---------------------------------------------------------------
    // Lip sync (driven by the loudness of the waifu's voice)
    // ---------------------------------------------------------------

    /** RMS loudness (0..1) above which mouth frame 1 / 2 / 3 is shown. */
    const val LIP_LEVEL_SMALL = 0.030f
    const val LIP_LEVEL_MEDIUM = 0.075f
    const val LIP_LEVEL_WIDE = 0.140f

    /** Smoothing: fast attack, slower decay (0..1, higher = faster). */
    const val LIP_ATTACK = 0.70f
    const val LIP_DECAY = 0.18f

    /** How often the mouth frame is re-evaluated, in milliseconds. */
    const val LIP_FRAME_INTERVAL_MS = 33L

    // ---------------------------------------------------------------
    // Stage animation
    // ---------------------------------------------------------------

    /** Subtle idle "breathing" of the whole sprite (body + mouth move together). */
    const val BREATHING_ENABLED = true
    const val BREATHING_PERIOD_MS = 4600
    const val BREATHING_SCALE_AMPLITUDE = 0.006f
    const val BREATHING_TRANSLATE_PX = 2f

    /** Portion of the screen height used by the character stage on the Chat page. */
    const val STAGE_HEIGHT_FRACTION = 0.42f

    /** Portion of the stage height the sprite is scaled to (1.0 = full height). */
    const val SPRITE_HEIGHT_FRACTION = 1.0f
}

/** Theme modes available in Settings. */
enum class ThemeMode(val id: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: AppConfig.DEFAULT_THEME_MODE
    }
}

/** UI languages available in Settings. Add a new [Texts] instance in TextsConfig to add one. */
enum class Language(val id: String, val displayName: String) {
    EN("en", "English"), BG("bg", "Български");

    companion object {
        fun fromId(id: String?): Language = entries.firstOrNull { it.id == id } ?: AppConfig.DEFAULT_LANGUAGE
    }
}
