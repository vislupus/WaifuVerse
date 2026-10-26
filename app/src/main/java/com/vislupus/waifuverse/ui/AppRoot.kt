package com.vislupus.waifuverse.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vislupus.waifuverse.AppTab
import com.vislupus.waifuverse.MainViewModel
import com.vislupus.waifuverse.ui.icons.Lucide
import com.vislupus.waifuverse.ui.screens.ChatScreen
import com.vislupus.waifuverse.ui.screens.HistoryScreen
import com.vislupus.waifuverse.ui.screens.PersonaScreen
import com.vislupus.waifuverse.ui.screens.SettingsScreen
import com.vislupus.waifuverse.ui.theme.LocalTexts
import com.vislupus.waifuverse.ui.theme.WaifuVerseTheme

/** Root of the UI: theme + bottom navigation + the four pages. */
@Composable
fun AppRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()

    WaifuVerseTheme(settings) {
        if (!ready) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@WaifuVerseTheme
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { BottomBar(tab, vm::selectTab) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                when (tab) {
                    AppTab.CHAT -> ChatScreen(vm)
                    AppTab.PERSONA -> PersonaScreen(vm)
                    AppTab.HISTORY -> HistoryScreen(vm)
                    AppTab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun BottomBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val texts = LocalTexts.current
    val items = listOf(
        Triple(AppTab.CHAT, Lucide.MessageCircle, texts.tabChat),
        Triple(AppTab.PERSONA, Lucide.UserPen, texts.tabPersona),
        Triple(AppTab.HISTORY, Lucide.History, texts.tabHistory),
        Triple(AppTab.SETTINGS, Lucide.Settings, texts.tabSettings),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
