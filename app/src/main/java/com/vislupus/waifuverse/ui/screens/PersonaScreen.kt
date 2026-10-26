package com.vislupus.waifuverse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vislupus.waifuverse.MainViewModel
import com.vislupus.waifuverse.config.CharactersConfig
import com.vislupus.waifuverse.config.ModelConfig
import com.vislupus.waifuverse.config.ThemeConfig
import com.vislupus.waifuverse.data.Waifu
import com.vislupus.waifuverse.ui.components.ChoiceChips
import com.vislupus.waifuverse.ui.components.ConfirmDialog
import com.vislupus.waifuverse.ui.components.SectionHeader
import com.vislupus.waifuverse.ui.components.SettingRow
import com.vislupus.waifuverse.ui.components.SettingsCard
import com.vislupus.waifuverse.ui.components.VerticalSpace
import com.vislupus.waifuverse.ui.components.WaifuSelectorRow
import com.vislupus.waifuverse.ui.icons.Lucide
import com.vislupus.waifuverse.ui.theme.LocalTexts
import kotlin.math.roundToInt

@Composable
fun PersonaScreen(vm: MainViewModel) {
    val waifus by vm.waifus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val avatars by vm.avatars.collectAsStateWithLifecycle()
    val personaId by vm.personaWaifuId.collectAsStateWithLifecycle()
    val selectedId = personaId ?: settings.lastWaifuId
    val waifu = waifus.firstOrNull { it.id == selectedId } ?: waifus.firstOrNull()

    PersonaContent(
        waifus = waifus,
        avatars = avatars,
        waifu = waifu,
        onSelect = vm::selectPersonaWaifu,
        onSave = vm::saveWaifu,
        onReset = vm::resetWaifu,
    )
}

@Composable
fun PersonaContent(
    waifus: List<Waifu>,
    avatars: Map<String, ImageBitmap?>,
    waifu: Waifu?,
    onSelect: (String) -> Unit,
    onSave: (Waifu) -> Unit,
    onReset: (String) -> Unit,
) {
    val texts = LocalTexts.current
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Column(modifier = Modifier.padding(horizontal = ThemeConfig.SPACE_M.dp, vertical = ThemeConfig.SPACE_M.dp)) {
            Text(texts.personaTitle, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(texts.personaSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = ThemeConfig.SPACE_S.dp)) {
            WaifuSelectorRow(
                waifus = waifus,
                selectedId = waifu?.id,
                avatars = avatars,
                onSelect = onSelect,
                showNames = false,
            )
        }
        if (waifu != null) {
            // key() restarts the editor state when another companion is picked.
            androidx.compose.runtime.key(waifu.id, waifu.updatedAt) {
                PersonaEditor(waifu = waifu, onSave = onSave, onReset = onReset)
            }
        }
    }
}

@Composable
private fun PersonaEditor(waifu: Waifu, onSave: (Waifu) -> Unit, onReset: (String) -> Unit) {
    val texts = LocalTexts.current
    var draft by remember { mutableStateOf(waifu) }
    var showVoices by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }
    val dirty = draft != waifu

    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            kotlinx.coroutines.delay(1500)
            savedFlash = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(texts.fieldName)
        SettingsCard {
            Column(modifier = Modifier.padding(ThemeConfig.SPACE_M.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(texts.fieldName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = { Text(texts.fieldTitle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SettingRow(
                icon = Lucide.AudioLines,
                title = texts.fieldVoice,
                subtitle = draft.voice,
                onClick = { showVoices = true },
            ) { Icon(Lucide.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            SettingRow(icon = Lucide.Image, title = texts.fieldBackground, subtitle = CharactersConfig.background(draft.backgroundId).name)
            ChoiceChips(
                options = CharactersConfig.backgrounds,
                selected = CharactersConfig.background(draft.backgroundId),
                label = { it.name },
                onSelect = { draft = draft.copy(backgroundId = it.id) },
            )
            VerticalSpace(ThemeConfig.SPACE_S)
        }

        SectionHeader(texts.fieldPersona)
        SettingsCard {
            Column(modifier = Modifier.padding(ThemeConfig.SPACE_M.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.persona,
                    onValueChange = { draft = draft.copy(persona = it) },
                    label = { Text(texts.fieldPersona) },
                    placeholder = { Text(texts.fieldPersonaHint) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.extraInstructions,
                    onValueChange = { draft = draft.copy(extraInstructions = it) },
                    label = { Text(texts.fieldExtra) },
                    placeholder = { Text(texts.fieldExtraHint) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showPrompt = !showPrompt }) {
                    Icon(if (showPrompt) Lucide.ChevronDown else Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(texts.promptPreview)
                }
                if (showPrompt) {
                    Text(
                        draft.systemPrompt(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ThemeConfig.RADIUS_SMALL.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                    )
                }
            }
        }

        SectionHeader(texts.modelParameters)
        SettingsCard {
            Column(modifier = Modifier.padding(ThemeConfig.SPACE_M.dp)) {
                ParamSlider(
                    label = texts.temperature,
                    value = draft.temperature,
                    min = ModelConfig.TEMPERATURE_MIN,
                    max = ModelConfig.TEMPERATURE_MAX,
                    display = "%.2f".format(draft.temperature),
                    hint = texts.temperatureHint,
                ) { draft = draft.copy(temperature = it) }
                ParamSlider(
                    label = texts.topP,
                    value = draft.topP,
                    min = ModelConfig.TOP_P_MIN,
                    max = ModelConfig.TOP_P_MAX,
                    display = "%.2f".format(draft.topP),
                ) { draft = draft.copy(topP = it) }
                ParamSlider(
                    label = texts.topK,
                    value = draft.topK.toFloat(),
                    min = ModelConfig.TOP_K_MIN.toFloat(),
                    max = ModelConfig.TOP_K_MAX.toFloat(),
                    display = draft.topK.toString(),
                ) { draft = draft.copy(topK = it.roundToInt()) }
                ParamSlider(
                    label = texts.maxTokens,
                    value = draft.maxOutputTokens.toFloat(),
                    min = ModelConfig.MAX_TOKENS_MIN.toFloat(),
                    max = ModelConfig.MAX_TOKENS_MAX.toFloat(),
                    display = draft.maxOutputTokens.toString(),
                ) { draft = draft.copy(maxOutputTokens = (it / 64f).roundToInt() * 64) }
            }
        }

        VerticalSpace(ThemeConfig.SPACE_L)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ThemeConfig.SPACE_M.dp),
            horizontalArrangement = Arrangement.spacedBy(ThemeConfig.SPACE_S.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showReset = true }) {
                Icon(Lucide.RotateCcw, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(texts.resetToDefault)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    onSave(draft)
                    savedFlash = true
                },
                enabled = dirty || savedFlash,
            ) {
                Icon(if (savedFlash) Lucide.Check else Lucide.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (savedFlash) texts.saved else texts.save)
            }
        }
        if (dirty && !savedFlash) {
            Text(
                texts.unsavedChanges,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ThemeConfig.SPACE_M.dp, vertical = 4.dp).align(Alignment.End),
            )
        }
        Spacer(Modifier.height(ThemeConfig.SPACE_L.dp).navigationBarsPadding())
    }

    if (showVoices) {
        AlertDialog(
            onDismissRequest = { showVoices = false },
            title = { Text(texts.fieldVoice) },
            text = {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    items(ModelConfig.VOICES) { v ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft = draft.copy(voice = v)
                                    showVoices = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(v, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (v == draft.voice) Icon(Lucide.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVoices = false }) { Text(texts.close) } },
        )
    }
    if (showReset) {
        ConfirmDialog(
            title = texts.resetToDefault,
            message = texts.resetConfirm,
            confirmLabel = texts.resetToDefault,
            onConfirm = { onReset(waifu.id) },
            onDismiss = { showReset = false },
        )
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    display: String,
    hint: String? = null,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
