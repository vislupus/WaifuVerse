package com.vislupus.waifuverse.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vislupus.waifuverse.config.ThemeConfig
import com.vislupus.waifuverse.data.Waifu
import com.vislupus.waifuverse.ui.icons.Lucide
import com.vislupus.waifuverse.ui.theme.LocalTexts
import com.vislupus.waifuverse.ui.theme.colorFromHex
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Small uppercase section label used on Settings / Persona pages. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = ThemeConfig.SPACE_M.dp,
            end = ThemeConfig.SPACE_M.dp,
            top = ThemeConfig.SPACE_L.dp,
            bottom = ThemeConfig.SPACE_S.dp,
        ),
    )
}

/** Card-like container for a group of rows. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .padding(horizontal = ThemeConfig.SPACE_M.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ThemeConfig.RADIUS_MEDIUM.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(ThemeConfig.RADIUS_MEDIUM.dp)),
    ) {
        content()
    }
}

/** A labelled row with optional subtitle and trailing content. */
@Composable
fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = ThemeConfig.SPACE_M.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(ThemeConfig.SPACE_S.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(icon: ImageVector?, title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon = icon, title = title, subtitle = subtitle, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Horizontal choice chips (theme, font size, language...). */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThemeConfig.SPACE_S.dp),
        contentPadding = PaddingValues(horizontal = ThemeConfig.SPACE_M.dp, vertical = ThemeConfig.SPACE_S.dp),
    ) {
        items(options) { option ->
            val isSelected = option == selected
            Text(
                text = label(option),
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Circular colour swatches for the accent picker. */
@Composable
fun ColorSwatches(colors: List<Pair<String, Color>>, selectedId: String, onSelect: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = ThemeConfig.SPACE_M.dp, vertical = ThemeConfig.SPACE_S.dp),
    ) {
        items(colors) { (id, color) ->
            val isSelected = id == selectedId
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Lucide.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Yes/No confirmation dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val texts = LocalTexts.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(texts.cancel) } },
    )
}

/** Round avatar made from the top of the body sprite. */
@Composable
fun WaifuAvatar(image: ImageBitmap?, accentHex: String, size: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val accent = colorFromHex(accentHex, MaterialTheme.colorScheme.primary)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.25f))
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.size(size.dp).clip(CircleShape),
            )
        } else {
            Icon(Lucide.User, contentDescription = null, tint = accent, modifier = Modifier.size((size / 2).dp))
        }
    }
}

/** Row of companions to pick from (Chat stage, Persona page). */
@Composable
fun WaifuSelectorRow(
    waifus: List<Waifu>,
    selectedId: String?,
    avatars: Map<String, ImageBitmap?>,
    onSelect: (String) -> Unit,
    showNames: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = ThemeConfig.SPACE_M.dp),
    ) {
        items(waifus, key = { it.id }) { w ->
            val selected = w.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(w.id) },
            ) {
                WaifuAvatar(avatars[w.id], w.accentHex, size = 44, selected = selected)
                if (showNames) {
                    Text(
                        w.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/** "Today 14:05", "Yesterday 09:12" or a full date. */
@Composable
fun formatTimestamp(millis: Long): String {
    val texts = LocalTexts.current
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "${texts.today} $time"
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (yesterday) return "${texts.yesterday} $time"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis)) + " " + time
}

@Composable
fun VerticalSpace(dp: Int) = Spacer(Modifier.height(dp.dp))
