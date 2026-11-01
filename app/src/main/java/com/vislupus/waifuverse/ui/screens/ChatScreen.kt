package com.vislupus.waifuverse.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vislupus.waifuverse.ChatLine
import com.vislupus.waifuverse.MainViewModel
import com.vislupus.waifuverse.NoticeKind
import com.vislupus.waifuverse.AppTab
import com.vislupus.waifuverse.config.AppConfig
import com.vislupus.waifuverse.config.CharactersConfig
import com.vislupus.waifuverse.config.ThemeConfig
import com.vislupus.waifuverse.data.Conversation
import com.vislupus.waifuverse.data.Role
import com.vislupus.waifuverse.data.Waifu
import com.vislupus.waifuverse.live.LiveSessionManager
import com.vislupus.waifuverse.sprite.SpriteLoader
import com.vislupus.waifuverse.ui.components.SpriteStage
import com.vislupus.waifuverse.ui.components.WaifuSelectorRow
import com.vislupus.waifuverse.ui.icons.Lucide
import com.vislupus.waifuverse.ui.theme.LocalNeutrals
import com.vislupus.waifuverse.ui.theme.LocalTexts
import com.vislupus.waifuverse.ui.theme.colorFromHex
import com.vislupus.waifuverse.ui.theme.fill

/** Everything the chat page shows. */
data class ChatUiState(
    val waifus: List<Waifu> = emptyList(),
    val selectedWaifu: Waifu? = null,
    val avatars: Map<String, ImageBitmap?> = emptyMap(),
    val sprite: SpriteLoader.Sprite? = null,
    val background: ImageBitmap? = null,
    val backgroundId: String = "",
    val mouthFrame: Int = 0,
    val status: LiveSessionManager.Status = LiveSessionManager.Status.IDLE,
    val micOn: Boolean = false,
    val speaking: Boolean = false,
    val activeModel: String? = null,
    val lines: List<ChatLine> = emptyList(),
    val remembered: Boolean = true,
    val conversation: Conversation? = null,
)

/** Everything the chat page can do (no-ops by default so previews work). */
interface ChatActions {
    fun selectWaifu(id: String) {}
    fun setBackground(id: String) {}
    fun setRemembered(remembered: Boolean) {}
    fun newConversation() {}
    fun toggleSession() {}
    fun toggleMic() {}
    fun send(text: String) {}
    fun openSettings() {}
}

@Composable
fun ChatScreen(vm: MainViewModel) {
    val waifus by vm.waifus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val avatars by vm.avatars.collectAsStateWithLifecycle()
    val sprite by vm.sprite.collectAsStateWithLifecycle()
    val background by vm.backgroundImage.collectAsStateWithLifecycle()
    val backgroundId by vm.stageBackgroundId.collectAsStateWithLifecycle()
    val mouthFrame by vm.live.mouthFrame.collectAsStateWithLifecycle()
    val status by vm.live.status.collectAsStateWithLifecycle()
    val micOn by vm.live.micOn.collectAsStateWithLifecycle()
    val speaking by vm.live.speaking.collectAsStateWithLifecycle()
    val activeModel by vm.live.activeModel.collectAsStateWithLifecycle()
    val lines by vm.chatLines.collectAsStateWithLifecycle()
    val remembered by vm.rememberCurrent.collectAsStateWithLifecycle()
    val conversation by vm.currentConversation.collectAsStateWithLifecycle()

    val context = LocalContext.current
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // What to do once the permission dialog closes.
    var pendingText by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val text = pendingText
        pendingText = null
        if (text != null) vm.sendText(text, granted) else vm.startSession(granted)
    }

    // Keep the screen on while talking.
    val sessionActive = status != LiveSessionManager.Status.IDLE
    DisposableEffect(sessionActive) {
        val window = (context as? Activity)?.window
        if (sessionActive && AppConfig.KEEP_SCREEN_ON_DURING_SESSION) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val state = ChatUiState(
        waifus = waifus,
        selectedWaifu = waifus.firstOrNull { it.id == settings.lastWaifuId } ?: waifus.firstOrNull(),
        avatars = avatars,
        sprite = sprite,
        background = background,
        backgroundId = backgroundId,
        mouthFrame = mouthFrame,
        status = status,
        micOn = micOn,
        speaking = speaking,
        activeModel = activeModel,
        lines = lines,
        remembered = remembered,
        conversation = conversation,
    )
    val actions = object : ChatActions {
        override fun selectWaifu(id: String) = vm.selectWaifu(id)
        override fun setBackground(id: String) = vm.setStageBackground(id)
        override fun setRemembered(remembered: Boolean) = vm.setRememberCurrent(remembered)
        override fun newConversation() = vm.newConversation()
        override fun toggleSession() {
            if (sessionActive) {
                vm.stopSession()
            } else if (micGranted()) {
                vm.startSession(true)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        override fun toggleMic() = vm.setMicEnabled(!micOn)
        override fun send(text: String) {
            if (sessionActive || micGranted()) {
                vm.sendText(text, micGranted())
            } else {
                pendingText = text
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        override fun openSettings() = vm.selectTab(AppTab.SETTINGS)
    }
    ChatContent(state, actions)
}

@Composable
fun ChatContent(state: ChatUiState, actions: ChatActions) {
    val waifu = state.selectedWaifu
    val accent = if (waifu != null) colorFromHex(waifu.accentHex, MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SpriteStage(
            sprite = state.sprite,
            background = state.background,
            mouthFrame = state.mouthFrame,
            accent = accent,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(AppConfig.STAGE_HEIGHT_FRACTION),
        ) {
            StageOverlay(state, actions, accent)
        }

        Transcript(
            lines = state.lines,
            waifuName = waifu?.name ?: "",
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onOpenSettings = actions::openSettings,
        )

        Composer(state, actions, accent)
        StatusRow(state, accent)
    }
}

@Composable
private fun StageOverlay(state: ChatUiState, actions: ChatActions, accent: Color) {
    val texts = LocalTexts.current
    val waifu = state.selectedWaifu
    var menuOpen by remember { mutableStateOf(false) }
    var backgroundMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Top-left: just the name and title.
        Column(modifier = Modifier.align(Alignment.TopStart).padding(ThemeConfig.SPACE_M.dp)) {
            Text(waifu?.name ?: "", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(waifu?.title ?: "", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        }

        // Top-right: one menu button; the actions unfold under it with generous spacing.
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(ThemeConfig.SPACE_S.dp),
            horizontalAlignment = Alignment.End,
        ) {
            StageIconButton(
                icon = if (menuOpen) Lucide.X else Lucide.EllipsisVertical,
                description = texts.stageMenu,
                active = menuOpen,
                accent = accent,
            ) { menuOpen = !menuOpen }
            AnimatedVisibility(
                visible = menuOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(ThemeConfig.STAGE_MENU_SPACING.dp),
                    modifier = Modifier.padding(top = ThemeConfig.STAGE_MENU_SPACING.dp),
                ) {
                    StageMenuAction(
                        icon = if (state.remembered) Lucide.BookmarkCheck else Lucide.Bookmark,
                        label = if (state.remembered) texts.rememberedShort else texts.temporaryShort,
                        active = state.remembered,
                        accent = accent,
                    ) { actions.setRemembered(!state.remembered) }
                    Box {
                        StageMenuAction(Lucide.Image, texts.chooseBackground, false, accent) { backgroundMenu = true }
                        DropdownMenu(expanded = backgroundMenu, onDismissRequest = { backgroundMenu = false }) {
                            CharactersConfig.backgrounds.forEach { bg ->
                                DropdownMenuItem(
                                    text = { Text(bg.name) },
                                    leadingIcon = {
                                        if (bg.id == state.backgroundId) {
                                            Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    onClick = {
                                        backgroundMenu = false
                                        menuOpen = false
                                        actions.setBackground(bg.id)
                                    },
                                )
                            }
                        }
                    }
                    StageMenuAction(Lucide.Plus, texts.newConversation, false, accent) {
                        menuOpen = false
                        actions.newConversation()
                    }
                }
            }
        }

        // Bottom: companion picker.
        WaifuSelectorRow(
            waifus = state.waifus,
            selectedId = waifu?.id,
            avatars = state.avatars,
            onSelect = actions::selectWaifu,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = ThemeConfig.SPACE_S.dp),
        )
    }
}

/** A menu action: label pill on the left, round icon button on the right. */
@Composable
private fun StageMenuAction(icon: ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Spacer(Modifier.width(ThemeConfig.SPACE_S.dp))
        StageIconButton(icon, label, active, accent, onClick)
    }
}

@Composable
private fun StageIconButton(icon: ImageVector, description: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) accent.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.35f)),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Connection state shown under the message box: icon + "Live · model" / "Offline". */
@Composable
private fun StatusRow(state: ChatUiState, accent: Color) {
    val texts = LocalTexts.current
    val (icon, label, color) = when (state.status) {
        LiveSessionManager.Status.IDLE -> Triple(Lucide.WifiOff, texts.offline, MaterialTheme.colorScheme.onSurfaceVariant)
        LiveSessionManager.Status.CONNECTING -> Triple(Lucide.Loader, texts.connecting, MaterialTheme.colorScheme.onSurfaceVariant)
        LiveSessionManager.Status.LIVE -> Triple(
            Lucide.Wifi,
            texts.live + (state.activeModel?.let { " · $it" } ?: ""),
            if (state.speaking) accent else MaterialTheme.colorScheme.primary,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ThemeConfig.SPACE_M.dp + 4.dp, end = ThemeConfig.SPACE_M.dp + 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        if (state.status == LiveSessionManager.Status.LIVE) {
            Spacer(Modifier.width(10.dp))
            val micIcon = if (state.micOn) Lucide.Mic else Lucide.MicOff
            val micLabel = when {
                state.speaking -> texts.speaking
                state.micOn -> texts.listening
                else -> texts.micMuted
            }
            Icon(micIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(micLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Transcript(lines: List<ChatLine>, waifuName: String, modifier: Modifier = Modifier, onOpenSettings: () -> Unit) {
    val texts = LocalTexts.current
    val listState = rememberLazyListState()
    val lastText = lines.lastOrNull()?.text ?: ""
    LaunchedEffect(lines.size, lastText.length) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    if (lines.isEmpty()) {
        Box(modifier = modifier.padding(ThemeConfig.SPACE_L.dp), contentAlignment = Alignment.Center) {
            Text(
                texts.chatEmptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = ThemeConfig.SPACE_M.dp, vertical = ThemeConfig.SPACE_S.dp),
        verticalArrangement = Arrangement.spacedBy(ThemeConfig.SPACE_S.dp),
    ) {
        items(lines, key = { it.id }) { line ->
            if (line.notice != null) NoticeRow(line, onOpenSettings) else Bubble(line, waifuName)
        }
    }
}

@Composable
private fun Bubble(line: ChatLine, waifuName: String) {
    val texts = LocalTexts.current
    val neutrals = LocalNeutrals.current
    val isUser = line.role == Role.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (isUser) texts.youLabel else waifuName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = ThemeConfig.RADIUS_MEDIUM.dp,
                        topEnd = ThemeConfig.RADIUS_MEDIUM.dp,
                        bottomStart = if (isUser) ThemeConfig.RADIUS_MEDIUM.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else ThemeConfig.RADIUS_MEDIUM.dp,
                    ),
                )
                .background(if (isUser) neutrals.bubbleUser else neutrals.bubbleAssistant)
                .then(
                    if (isUser) Modifier
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(ThemeConfig.RADIUS_MEDIUM.dp)),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = line.text + if (line.pending) " …" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (line.pending) 0.75f else 1f),
            )
        }
    }
}

@Composable
private fun NoticeRow(line: ChatLine, onOpenSettings: () -> Unit) {
    val texts = LocalTexts.current
    val kind = line.notice ?: return
    val text = when (kind) {
        NoticeKind.CONNECTED -> texts.usingModel.fill(line.noticeArgs)
        NoticeKind.MODEL_FALLBACK -> texts.modelFallback.fill(line.noticeArgs)
        NoticeKind.SESSION_ENDED -> texts.sessionEnded
        NoticeKind.SESSION_ERROR -> texts.sessionError.fill(line.noticeArgs)
        NoticeKind.ALL_MODELS_FAILED -> texts.allModelsFailed.fill(line.noticeArgs)
        NoticeKind.API_KEY_MISSING -> texts.apiKeyMissing.fill(line.noticeArgs)
        NoticeKind.MIC_DENIED -> texts.micPermissionDenied
    }
    val isError = kind == NoticeKind.SESSION_ERROR || kind == NoticeKind.ALL_MODELS_FAILED ||
        kind == NoticeKind.API_KEY_MISSING || kind == NoticeKind.MIC_DENIED
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = ThemeConfig.SPACE_M.dp, vertical = 2.dp),
        )
        if (kind == NoticeKind.API_KEY_MISSING) {
            TextButton(onClick = onOpenSettings) { Text(texts.goToSettings) }
        }
    }
}

@Composable
private fun Composer(state: ChatUiState, actions: ChatActions, accent: Color) {
    val texts = LocalTexts.current
    var text by rememberSaveable { mutableStateOf("") }
    val sessionActive = state.status != LiveSessionManager.Status.IDLE

    fun submit() {
        val t = text.trim()
        if (t.isEmpty()) return
        text = ""
        actions.send(t)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = ThemeConfig.SPACE_M.dp, vertical = ThemeConfig.SPACE_S.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Text input
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (text.isEmpty()) {
                Text(
                    texts.chatInputPlaceholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(ThemeConfig.SPACE_S.dp))

        if (text.isNotBlank()) {
            RoundButton(Lucide.Send, texts.send, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) { submit() }
        } else {
            if (sessionActive) {
                RoundButton(
                    icon = if (state.micOn) Lucide.Mic else Lucide.MicOff,
                    description = texts.voiceLabel,
                    background = if (state.micOn) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
                    tint = if (state.micOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                ) { actions.toggleMic() }
                Spacer(Modifier.width(ThemeConfig.SPACE_S.dp))
            }
            RoundButton(
                icon = if (sessionActive) Lucide.PhoneOff else Lucide.Phone,
                description = if (sessionActive) texts.stopVoice else texts.startVoice,
                background = if (sessionActive) MaterialTheme.colorScheme.error else accent,
                tint = Color.White,
            ) { actions.toggleSession() }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, background: Color, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(ThemeConfig.MIC_BUTTON_SIZE.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}
