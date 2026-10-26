package com.vislupus.waifuverse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vislupus.waifuverse.MainViewModel
import com.vislupus.waifuverse.config.ThemeConfig
import com.vislupus.waifuverse.data.Conversation
import com.vislupus.waifuverse.data.Waifu
import com.vislupus.waifuverse.ui.components.ConfirmDialog
import com.vislupus.waifuverse.ui.components.WaifuAvatar
import com.vislupus.waifuverse.ui.components.formatTimestamp
import com.vislupus.waifuverse.ui.icons.Lucide
import com.vislupus.waifuverse.ui.theme.LocalTexts
import com.vislupus.waifuverse.ui.theme.fill

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val waifus by vm.waifus.collectAsStateWithLifecycle()
    val avatars by vm.avatars.collectAsStateWithLifecycle()
    val current by vm.currentConversation.collectAsStateWithLifecycle()
    HistoryContent(
        conversations = conversations,
        waifus = waifus,
        avatars = avatars,
        currentId = current?.id,
        onOpen = vm::openConversation,
        onToggleRemembered = vm::toggleConversationRemembered,
        onDelete = vm::deleteConversation,
        onDeleteAll = vm::deleteAllConversations,
    )
}

@Composable
fun HistoryContent(
    conversations: List<Conversation>,
    waifus: List<Waifu>,
    avatars: Map<String, ImageBitmap?>,
    currentId: Long?,
    onOpen: (Long) -> Unit,
    onToggleRemembered: (Conversation) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val texts = LocalTexts.current
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    val waifuById = waifus.associateBy { it.id }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = ThemeConfig.SPACE_M.dp, end = 4.dp, top = ThemeConfig.SPACE_M.dp, bottom = ThemeConfig.SPACE_S.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                texts.historyTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (conversations.isNotEmpty()) {
                IconButton(onClick = { deleteAll = true }) {
                    Icon(Lucide.Trash2, contentDescription = texts.clearAllHistory, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(ThemeConfig.SPACE_L.dp), contentAlignment = Alignment.Center) {
                Text(
                    texts.historyEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = ThemeConfig.SPACE_M.dp, vertical = ThemeConfig.SPACE_S.dp),
                verticalArrangement = Arrangement.spacedBy(ThemeConfig.SPACE_S.dp),
            ) {
                items(conversations, key = { it.id }) { c ->
                    val w = waifuById[c.waifuId]
                    ConversationCard(
                        conversation = c,
                        waifu = w,
                        avatar = avatars[c.waifuId],
                        isCurrent = c.id == currentId,
                        onOpen = { onOpen(c.id) },
                        onToggleRemembered = { onToggleRemembered(c) },
                        onDelete = { deleteId = c.id },
                    )
                }
            }
        }
    }

    deleteId?.let { id ->
        ConfirmDialog(
            title = texts.deleteConversation,
            message = texts.deleteConfirm,
            confirmLabel = texts.delete,
            destructive = true,
            onConfirm = { onDelete(id) },
            onDismiss = { deleteId = null },
        )
    }
    if (deleteAll) {
        ConfirmDialog(
            title = texts.clearAllHistory,
            message = texts.clearAllConfirm,
            confirmLabel = texts.delete,
            destructive = true,
            onConfirm = onDeleteAll,
            onDismiss = { deleteAll = false },
        )
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    waifu: Waifu?,
    avatar: ImageBitmap?,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onToggleRemembered: () -> Unit,
    onDelete: () -> Unit,
) {
    val texts = LocalTexts.current
    val shape = RoundedCornerShape(ThemeConfig.RADIUS_MEDIUM.dp)
    val timestamp = formatTimestamp(conversation.updatedAt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaifuAvatar(avatar, waifu?.accentHex ?: "#888888", size = 44, selected = false)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conversation.title.ifBlank { texts.untitledConversation },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (waifu?.name ?: conversation.waifuId) + " · " + timestamp + " · " +
                    texts.messagesCount.fill("n" to conversation.messageCount.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (conversation.remembered) texts.rememberedShort else texts.temporaryShort,
                style = MaterialTheme.typography.labelSmall,
                color = if (conversation.remembered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleRemembered) {
            Icon(
                if (conversation.remembered) Lucide.BookmarkCheck else Lucide.Bookmark,
                contentDescription = if (conversation.remembered) texts.rememberedShort else texts.temporaryShort,
                tint = if (conversation.remembered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Lucide.Trash2, contentDescription = texts.deleteConversation, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
