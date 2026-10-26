package com.vislupus.waifuverse.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vislupus.waifuverse.ChatLine
import com.vislupus.waifuverse.NoticeKind
import com.vislupus.waifuverse.config.CharactersConfig
import com.vislupus.waifuverse.config.Language
import com.vislupus.waifuverse.config.ThemeMode
import com.vislupus.waifuverse.data.AppSettings
import com.vislupus.waifuverse.data.Conversation
import com.vislupus.waifuverse.data.Role
import com.vislupus.waifuverse.data.Waifu
import com.vislupus.waifuverse.live.LiveSessionManager
import com.vislupus.waifuverse.ui.screens.ChatActions
import com.vislupus.waifuverse.ui.screens.ChatContent
import com.vislupus.waifuverse.ui.screens.ChatUiState
import com.vislupus.waifuverse.ui.screens.HistoryContent
import com.vislupus.waifuverse.ui.screens.PersonaContent
import com.vislupus.waifuverse.ui.screens.SettingsContent
import com.vislupus.waifuverse.ui.theme.WaifuVerseTheme

/**
 * Android Studio previews (open this file and click the "Split"/"Design" tab,
 * or run `make preview` to open the project in Android Studio).
 * They use the default config and sample data; no device needed.
 */

private val sampleWaifus = CharactersConfig.waifus.map { Waifu.fromDefaults(it, 0L) }

private val sampleLines = listOf(
    ChatLine(1, Role.SYSTEM, "", notice = NoticeKind.CONNECTED, noticeArgs = mapOf("model" to "gemini-3.8-live")),
    ChatLine(2, Role.USER, "Hey Mira, how was your day?"),
    ChatLine(3, Role.ASSISTANT, "Soft and slow, like a cloud drifting by... and better now that you are here."),
    ChatLine(4, Role.USER, "Tell me about the stars", pending = true),
)

private val sampleConversations = listOf(
    Conversation(1, "mira", "How was your day?", true, 0L, System.currentTimeMillis(), 12),
    Conversation(2, "akari", "Rooftop dares", false, 0L, System.currentTimeMillis() - 86_400_000L, 5),
    Conversation(3, "sora", "Books about time", true, 0L, System.currentTimeMillis() - 3 * 86_400_000L, 31),
)

@Preview(name = "Chat · dark", showBackground = true, widthDp = 393, heightDp = 851)
@Composable
fun PreviewChatDark() {
    WaifuVerseTheme(AppSettings(themeMode = ThemeMode.DARK)) {
        ChatContent(
            ChatUiState(
                waifus = sampleWaifus,
                selectedWaifu = sampleWaifus.first(),
                lines = sampleLines,
                status = LiveSessionManager.Status.LIVE,
                micOn = true,
                activeModel = "gemini-3.8-live",
            ),
            object : ChatActions {},
        )
    }
}

@Preview(name = "Chat · light · BG", showBackground = true, widthDp = 393, heightDp = 851)
@Composable
fun PreviewChatLightBulgarian() {
    WaifuVerseTheme(AppSettings(themeMode = ThemeMode.LIGHT, language = Language.BG, accentId = "rose")) {
        ChatContent(
            ChatUiState(waifus = sampleWaifus, selectedWaifu = sampleWaifus[1], lines = emptyList()),
            object : ChatActions {},
        )
    }
}

@Preview(name = "Persona", showBackground = true, widthDp = 393, heightDp = 851)
@Composable
fun PreviewPersona() {
    WaifuVerseTheme(AppSettings(themeMode = ThemeMode.DARK)) {
        PersonaContent(
            waifus = sampleWaifus,
            avatars = emptyMap(),
            waifu = sampleWaifus[2],
            onSelect = {},
            onSave = {},
            onReset = {},
        )
    }
}

@Preview(name = "History", showBackground = true, widthDp = 393, heightDp = 851)
@Composable
fun PreviewHistory() {
    WaifuVerseTheme(AppSettings(themeMode = ThemeMode.LIGHT, accentId = "teal")) {
        HistoryContent(
            conversations = sampleConversations,
            waifus = sampleWaifus,
            avatars = emptyMap(),
            currentId = 1,
            onOpen = {},
            onToggleRemembered = {},
            onDelete = {},
            onDeleteAll = {},
        )
    }
}

@Preview(name = "Settings", showBackground = true, widthDp = 393, heightDp = 851)
@Composable
fun PreviewSettings() {
    WaifuVerseTheme(AppSettings(themeMode = ThemeMode.DARK, accentId = "amber")) {
        SettingsContent(
            settings = AppSettings(themeMode = ThemeMode.DARK, accentId = "amber", apiKey = "AIza…"),
            systemDark = true,
            databasePath = "/data/user/0/com.vislupus.waifuverse/files/data/waifuverse.db",
            onUpdate = {},
            onDeleteAll = {},
        )
    }
}
