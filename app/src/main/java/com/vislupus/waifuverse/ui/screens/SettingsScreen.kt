package com.vislupus.waifuverse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vislupus.waifuverse.BuildConfig
import com.vislupus.waifuverse.MainViewModel
import com.vislupus.waifuverse.config.AudioMode
import com.vislupus.waifuverse.config.Language
import com.vislupus.waifuverse.config.ModelConfig
import com.vislupus.waifuverse.config.ThemeConfig
import com.vislupus.waifuverse.config.ThemeMode
import com.vislupus.waifuverse.data.AppSettings
import com.vislupus.waifuverse.ui.components.ChoiceChips
import com.vislupus.waifuverse.ui.components.ColorSwatches
import com.vislupus.waifuverse.ui.components.ConfirmDialog
import com.vislupus.waifuverse.ui.components.SectionHeader
import com.vislupus.waifuverse.ui.components.SettingRow
import com.vislupus.waifuverse.ui.components.SettingsCard
import com.vislupus.waifuverse.ui.components.SwitchRow
import com.vislupus.waifuverse.ui.icons.Lucide
import com.vislupus.waifuverse.ui.theme.LocalTexts

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    SettingsContent(
        settings = settings,
        systemDark = isDark,
        databasePath = vm.databasePath,
        onUpdate = vm::updateSettings,
        onDeleteAll = vm::deleteAllConversations,
    )
}

@Composable
fun SettingsContent(
    settings: AppSettings,
    systemDark: Boolean,
    databasePath: String,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val texts = LocalTexts.current
    var showKey by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            texts.settingsTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = ThemeConfig.SPACE_M.dp, vertical = ThemeConfig.SPACE_M.dp),
        )

        // ---------------- Appearance ----------------
        SectionHeader(texts.sectionAppearance)
        SettingsCard {
            SettingRow(icon = if (dark) Lucide.Moon else Lucide.Sun, title = texts.theme)
            ChoiceChips(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> texts.themeSystem
                        ThemeMode.LIGHT -> texts.themeLight
                        ThemeMode.DARK -> texts.themeDark
                    }
                },
                onSelect = { mode -> onUpdate { it.copy(themeMode = mode) } },
            )
            SettingRow(icon = Lucide.Palette, title = texts.accentColor)
            ColorSwatches(
                colors = ThemeConfig.accents.map { it.id to (if (dark) it.dark else it.light) },
                selectedId = settings.accentId,
                onSelect = { id -> onUpdate { it.copy(accentId = id) } },
            )
            SettingRow(icon = Lucide.Type, title = texts.fontSize)
            ChoiceChips(
                options = ThemeConfig.fontScales,
                selected = ThemeConfig.fontScale(settings.fontScaleId),
                label = {
                    when (it.id) {
                        "small" -> texts.fontSmall
                        "large" -> texts.fontLarge
                        "xlarge" -> texts.fontXLarge
                        else -> texts.fontDefault
                    }
                },
                onSelect = { opt -> onUpdate { it.copy(fontScaleId = opt.id) } },
            )
            SettingRow(icon = Lucide.Languages, title = texts.language)
            ChoiceChips(
                options = Language.entries,
                selected = settings.language,
                label = { it.displayName },
                onSelect = { lang -> onUpdate { it.copy(language = lang) } },
            )
            Spacer(Modifier.height(ThemeConfig.SPACE_S.dp))
        }

        // ---------------- AI ----------------
        SectionHeader(texts.sectionAi)
        SettingsCard {
            Column(modifier = Modifier.padding(ThemeConfig.SPACE_M.dp)) {
                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = { v -> onUpdate { it.copy(apiKey = v.trim()) } },
                    label = { Text(texts.apiKey) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Lucide.Key, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Lucide.EyeOff else Lucide.Eye, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    texts.apiKeyHint + "\n" + texts.apiKeyGetOne,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                )
                OutlinedTextField(
                    value = settings.primaryModel,
                    onValueChange = { v -> onUpdate { it.copy(primaryModel = v.trim()) } },
                    label = { Text(texts.primaryModel) },
                    placeholder = { Text(ModelConfig.MODEL_CHAIN.first()) },
                    singleLine = true,
                    leadingIcon = { Icon(Lucide.Cpu, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    texts.primaryModelHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            SettingRow(
                icon = Lucide.SlidersHorizontal,
                title = texts.fallbackModels,
                subtitle = settings.modelChain().joinToString("  →  "),
            )
        }

        // ---------------- Audio ----------------
        SectionHeader(texts.sectionAudio)
        SettingsCard {
            SettingRow(icon = Lucide.Volume2, title = texts.audioMode)
            AudioMode.entries.forEach { mode ->
                SettingRow(
                    icon = null,
                    title = if (mode == AudioMode.VOICE_CALL) texts.audioModeVoiceCall else texts.audioModeMedia,
                    subtitle = if (mode == AudioMode.VOICE_CALL) texts.audioModeVoiceCallHint else texts.audioModeMediaHint,
                    onClick = { onUpdate { it.copy(audioMode = mode) } },
                ) {
                    RadioButton(selected = settings.audioMode == mode, onClick = { onUpdate { it.copy(audioMode = mode) } })
                }
            }
            SwitchRow(
                icon = Lucide.MicOff,
                title = texts.muteMicWhileSpeaking,
                subtitle = texts.muteMicWhileSpeakingHint,
                checked = settings.muteMicWhileSpeaking,
            ) { v -> onUpdate { it.copy(muteMicWhileSpeaking = v) } }
            SwitchRow(
                icon = Lucide.Mic,
                title = texts.autoStartMic,
                checked = settings.autoStartMic,
            ) { v -> onUpdate { it.copy(autoStartMic = v) } }
        }

        // ---------------- Conversations ----------------
        SectionHeader(texts.sectionConversations)
        SettingsCard {
            SwitchRow(
                icon = Lucide.BookmarkCheck,
                title = texts.rememberByDefault,
                subtitle = texts.rememberByDefaultHint,
                checked = settings.rememberByDefault,
            ) { v -> onUpdate { it.copy(rememberByDefault = v) } }
        }

        // ---------------- Data ----------------
        SectionHeader(texts.sectionData)
        SettingsCard {
            SettingRow(icon = Lucide.Database, title = texts.storageLocation, subtitle = databasePath)
            SettingRow(
                icon = Lucide.Trash2,
                title = texts.deleteAllData,
                onClick = { confirmDelete = true },
            )
        }

        // ---------------- About ----------------
        SectionHeader(texts.sectionAbout)
        SettingsCard {
            SettingRow(icon = Lucide.Info, title = texts.version, subtitle = BuildConfig.VERSION_NAME)
        }

        Spacer(Modifier.height(ThemeConfig.SPACE_L.dp).navigationBarsPadding())
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = texts.deleteAllData,
            message = texts.deleteAllDataConfirm,
            confirmLabel = texts.delete,
            destructive = true,
            onConfirm = onDeleteAll,
            onDismiss = { confirmDelete = false },
        )
    }
}
