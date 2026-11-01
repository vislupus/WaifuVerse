package com.vislupus.waifuverse.data

import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.config.AudioMode
import com.vislupus.waifuverse.config.CharactersConfig
import com.vislupus.waifuverse.config.Language
import com.vislupus.waifuverse.config.ModelConfig
import com.vislupus.waifuverse.config.Provider
import com.vislupus.waifuverse.config.ThemeMode
import com.vislupus.waifuverse.config.WaifuDefaults

/** A companion as stored in the database (editable on the Persona page). */
data class Waifu(
    val id: String,
    val name: String,
    val title: String,
    /** Gemini voice. */
    val voice: String,
    val provider: Provider,
    val openAiVoice: String,
    val backgroundId: String,
    val accentHex: String,
    val persona: String,
    val extraInstructions: String,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxOutputTokens: Int,
    val updatedAt: Long,
) {
    /** Builds the system prompt sent to Gemini from the configured template. */
    fun systemPrompt(): String = CharactersConfig.BASE_PROMPT
        .replace("{name}", name)
        .replace("{title}", title)
        .replace("{persona}", persona.trim())
        .replace("{extra}", extraInstructions.trim())
        .lines()
        .filter { it.isNotBlank() }
        .joinToString("\n")

    companion object {
        fun fromDefaults(d: WaifuDefaults, now: Long = System.currentTimeMillis()) = Waifu(
            id = d.id,
            name = d.name,
            title = d.title,
            voice = d.voice,
            provider = d.provider,
            openAiVoice = d.openAiVoice,
            backgroundId = d.backgroundId,
            accentHex = d.accentHex,
            persona = d.persona,
            extraInstructions = d.extraInstructions,
            temperature = d.temperature,
            topP = d.topP,
            topK = d.topK,
            maxOutputTokens = d.maxOutputTokens,
            updatedAt = now,
        )
    }
}

/** One chat (voice or text) with a waifu. */
data class Conversation(
    val id: Long,
    val waifuId: String,
    val title: String,
    /** true = used as memory for future conversations with the same waifu. */
    val remembered: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
)

enum class Role(val id: String) {
    USER("user"), ASSISTANT("assistant"), SYSTEM("system");

    companion object {
        fun fromId(id: String): Role = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

data class Message(
    val id: Long,
    val conversationId: Long,
    val role: Role,
    val text: String,
    val createdAt: Long,
)

/** User settings (Settings page). Defaults come from the config files. */
data class AppSettings(
    val themeMode: ThemeMode = AppConfig.DEFAULT_THEME_MODE,
    val accentId: String = AppConfig.DEFAULT_ACCENT_ID,
    val fontScaleId: String = AppConfig.DEFAULT_FONT_SCALE_ID,
    val language: Language = AppConfig.DEFAULT_LANGUAGE,
    val apiKey: String = "",
    val primaryModel: String = "",
    val openAiApiKey: String = "",
    val openAiPrimaryModel: String = "",
    val audioMode: AudioMode = ModelConfig.DEFAULT_AUDIO_MODE,
    val muteMicWhileSpeaking: Boolean = ModelConfig.DEFAULT_MUTE_MIC_WHILE_SPEAKING,
    val autoStartMic: Boolean = AppConfig.AUTO_START_MIC_ON_CONNECT,
    val rememberByDefault: Boolean = AppConfig.DEFAULT_REMEMBER_CONVERSATIONS,
    val lastWaifuId: String = AppConfig.DEFAULT_WAIFU_ID,
    val lastConversationId: Long = 0L,
) {
    /** Models of a provider in the order they are tried. */
    fun modelChain(provider: Provider): List<String> {
        val primary = when (provider) {
            Provider.GEMINI -> primaryModel
            Provider.OPENAI -> openAiPrimaryModel
        }.trim()
        val chain = when (provider) {
            Provider.GEMINI -> ModelConfig.MODEL_CHAIN
            Provider.OPENAI -> ModelConfig.OPENAI_MODEL_CHAIN
        }
        return if (primary.isEmpty()) chain else listOf(primary) + chain.filter { it != primary }
    }

    fun apiKey(provider: Provider): String = when (provider) {
        Provider.GEMINI -> apiKey
        Provider.OPENAI -> openAiApiKey
    }

    companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_ACCENT = "accent_id"
        const val KEY_FONT_SCALE = "font_scale_id"
        const val KEY_LANGUAGE = "language"
        const val KEY_API_KEY = "api_key"
        const val KEY_PRIMARY_MODEL = "primary_model"
        const val KEY_OPENAI_API_KEY = "openai_api_key"
        const val KEY_OPENAI_PRIMARY_MODEL = "openai_primary_model"
        const val KEY_AUDIO_MODE = "audio_mode"
        const val KEY_MUTE_MIC = "mute_mic_while_speaking"
        const val KEY_AUTO_START_MIC = "auto_start_mic"
        const val KEY_REMEMBER_DEFAULT = "remember_by_default"
        const val KEY_LAST_WAIFU = "last_waifu_id"
        const val KEY_LAST_CONVERSATION = "last_conversation_id"

        fun fromMap(m: Map<String, String>): AppSettings {
            val d = AppSettings()
            return AppSettings(
                themeMode = ThemeMode.fromId(m[KEY_THEME]),
                accentId = m[KEY_ACCENT] ?: d.accentId,
                fontScaleId = m[KEY_FONT_SCALE] ?: d.fontScaleId,
                language = Language.fromId(m[KEY_LANGUAGE]),
                apiKey = m[KEY_API_KEY] ?: "",
                primaryModel = m[KEY_PRIMARY_MODEL] ?: "",
                openAiApiKey = m[KEY_OPENAI_API_KEY] ?: "",
                openAiPrimaryModel = m[KEY_OPENAI_PRIMARY_MODEL] ?: "",
                audioMode = AudioMode.fromId(m[KEY_AUDIO_MODE]),
                muteMicWhileSpeaking = m[KEY_MUTE_MIC]?.toBooleanStrictOrNull() ?: d.muteMicWhileSpeaking,
                autoStartMic = m[KEY_AUTO_START_MIC]?.toBooleanStrictOrNull() ?: d.autoStartMic,
                rememberByDefault = m[KEY_REMEMBER_DEFAULT]?.toBooleanStrictOrNull() ?: d.rememberByDefault,
                lastWaifuId = m[KEY_LAST_WAIFU] ?: d.lastWaifuId,
                lastConversationId = m[KEY_LAST_CONVERSATION]?.toLongOrNull() ?: 0L,
            )
        }
    }

    fun toMap(): Map<String, String> = mapOf(
        KEY_THEME to themeMode.id,
        KEY_ACCENT to accentId,
        KEY_FONT_SCALE to fontScaleId,
        KEY_LANGUAGE to language.id,
        KEY_API_KEY to apiKey,
        KEY_PRIMARY_MODEL to primaryModel,
        KEY_OPENAI_API_KEY to openAiApiKey,
        KEY_OPENAI_PRIMARY_MODEL to openAiPrimaryModel,
        KEY_AUDIO_MODE to audioMode.id,
        KEY_MUTE_MIC to muteMicWhileSpeaking.toString(),
        KEY_AUTO_START_MIC to autoStartMic.toString(),
        KEY_REMEMBER_DEFAULT to rememberByDefault.toString(),
        KEY_LAST_WAIFU to lastWaifuId,
        KEY_LAST_CONVERSATION to lastConversationId.toString(),
    )
}
