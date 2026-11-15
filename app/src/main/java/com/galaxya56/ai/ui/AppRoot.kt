package com.galaxya56.ai.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galaxya56.ai.ui.theme.Accent
import com.galaxya56.ai.ui.theme.Ink
import com.galaxya56.ai.ui.theme.Surface1
import com.galaxya56.ai.ui.theme.Surface2
import com.galaxya56.ai.ui.theme.TextMuted
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    CHAT("Chat"),
    STUDIO("Studio"),
    MODELS("Modelle"),
    SETTINGS("Optionen")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: AppViewModel, globalStatus: String) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.CHAT) }

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hardware by viewModel.hardware.collectAsStateWithLifecycle()
    val loadedModel by viewModel.loadedModel.collectAsStateWithLifecycle()

    val active = conversations.firstOrNull { it.id == activeId }
    val subtitle = when (tab) {
        Tab.CHAT -> when {
            settings.cloudReady -> "Cloud · ${settings.cloudModel}"
            loadedModel != null -> loadedModel!!
            else -> "Lokal · kein Modell geladen"
        }
        Tab.STUDIO -> "Bild · Video · Musik"
        Tab.MODELS -> "Hugging Face & Civitai"
        Tab.SETTINGS -> hardware.gpu.ifBlank { "Gerät & Backends" }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Surface1) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(Accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A56", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("Local AI Studio", style = MaterialTheme.typography.titleMedium)
                            Text("Galaxy A56 · Exynos 1580", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.newConversation()
                            tab = Tab.CHAT
                            scope.launch { drawerState.close() }
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Accent)
                    Spacer(Modifier.size(10.dp))
                    Text("Neuer Chat", fontWeight = FontWeight.Medium)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Text(
                    "VERLAUF",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(conversations, key = { it.id }) { conv ->
                        val selected = conv.id == activeId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .background(
                                    if (selected) Surface2 else Surface1,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.openConversation(conv.id)
                                    tab = Tab.CHAT
                                    scope.launch { drawerState.close() }
                                }
                                .padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                conv.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.deleteConversation(conv.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Chat löschen",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Tab.entries.forEach { entry ->
                    val icon = when (entry) {
                        Tab.CHAT -> Icons.Outlined.ChatBubbleOutline
                        Tab.STUDIO -> Icons.Outlined.AutoAwesome
                        Tab.MODELS -> Icons.Outlined.FolderOpen
                        Tab.SETTINGS -> Icons.Outlined.Settings
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                tab = entry
                                scope.launch { drawerState.close() }
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Text(
                    "llama.cpp · NPU/GPU-Erkennung · v2.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menü")
                        }
                    },
                    title = {
                        Column {
                            Text(tab.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (tab == Tab.CHAT) {
                            IconButton(onClick = { viewModel.newConversation() }) {
                                Icon(Icons.Filled.Add, contentDescription = "Neuer Chat")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Surface1, tonalElevation = 0.dp) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = {
                                val icon = when (entry) {
                                    Tab.CHAT -> Icons.Outlined.ChatBubbleOutline
                                    Tab.STUDIO -> Icons.Outlined.AutoAwesome
                                    Tab.MODELS -> Icons.Outlined.FolderOpen
                                    Tab.SETTINGS -> Icons.Outlined.Settings
                                }
                                Icon(icon, contentDescription = entry.label, modifier = Modifier.size(20.dp))
                            },
                            label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                indicatorColor = Surface2,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.CHAT -> ChatScreen(viewModel, onOpenModels = { tab = Tab.MODELS })
                    Tab.STUDIO -> StudioScreen(viewModel)
                    Tab.MODELS -> ModelsScreen(viewModel)
                    Tab.SETTINGS -> SettingsScreen(viewModel)
                }
                if (globalStatus.isNotBlank() && tab != Tab.CHAT) {
                    Text(
                        globalStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Surface2)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
