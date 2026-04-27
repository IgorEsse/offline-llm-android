package com.example.offlinellm

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlinellm.data.repo.AppGraph
import com.example.offlinellm.ui.localization.LocaleManager
import com.example.offlinellm.ui.screens.ChatScreen
import com.example.offlinellm.ui.screens.DiagnosticsScreen
import com.example.offlinellm.ui.screens.ModelScreen
import com.example.offlinellm.ui.screens.SettingsScreen
import com.example.offlinellm.ui.theme.AppTheme
import com.example.offlinellm.viewmodel.AppViewModelFactory
import com.example.offlinellm.viewmodel.ChatViewModel
import com.example.offlinellm.viewmodel.ModelViewModel
import com.example.offlinellm.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = AppGraph(applicationContext)
        val factory = AppViewModelFactory(graph.chatRepository, graph.modelRepository, graph.settingsRepository)
        setContent {
            AppTheme {
                Root(factory)
            }
        }
    }
}

private data class SheetTabItem(
    val labelRes: Int,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(factory: AppViewModelFactory) {
    var showSheet by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableIntStateOf(0) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val chatVm: ChatViewModel = viewModel(factory = factory)
    val modelVm: ModelViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    LaunchedEffect(settings.uiLanguage) {
        LocaleManager.applyLanguage(settings.uiLanguage)
    }

    val sheetTabs = listOf(
        SheetTabItem(R.string.tab_models) { Icon(Icons.Default.Folder, contentDescription = null) },
        SheetTabItem(R.string.tab_settings) { Icon(Icons.Default.Settings, contentDescription = null) },
        SheetTabItem(R.string.tab_info) { Icon(Icons.Default.Info, contentDescription = null) }
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { modelVm.import(it.toString()) }
    }

    val chatState by chatVm.uiState.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.drawer_chats_title), style = MaterialTheme.typography.titleLarge)
                    androidx.compose.material3.Button(onClick = {
                        chatVm.createConversation()
                        scope.launch { drawerState.close() }
                    }) { Text(stringResource(R.string.drawer_new_chat)) }
                    if (chatState.conversations.isEmpty()) {
                        Text(stringResource(R.string.drawer_no_chats))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chatState.conversations) { conv ->
                            var menu by remember { mutableStateOf(false) }
                            Surface(
                                color = if (conv.id == chatState.activeConversationId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                onClick = {
                                    chatVm.selectConversation(conv.id)
                                    scope.launch { drawerState.close() }
                                }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                                            Text(conv.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                            Text(
                                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(conv.updatedAt)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { menu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = null)
                                        }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.drawer_rename)) },
                                                onClick = {
                                                    chatVm.renameConversation(conv.id, "${stringResource(R.string.drawer_rename)} ${conv.id}")
                                                    menu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.drawer_clear_chat)) },
                                                onClick = {
                                                    chatVm.clearConversation(conv.id)
                                                    menu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.drawer_delete)) },
                                                onClick = {
                                                    pendingDeleteId = conv.id
                                                    menu = false
                                                }
                                            )
                                        }
                                    }
                                    if (conv.lastMessagePreview.isNotBlank()) {
                                        Text(conv.lastMessagePreview, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.open_chats))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSheet = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.open_unified_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) {
            ChatScreen(
                vm = chatVm,
                onOpenModels = {
                    sheetTab = 0
                    showSheet = true
                }
            )

            if (showSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        PrimaryTabRow(selectedTabIndex = sheetTab) {
                            sheetTabs.forEachIndexed { index, tab ->
                                Tab(
                                    selected = sheetTab == index,
                                    onClick = { sheetTab = index },
                                    text = { Text(stringResource(tab.labelRes)) },
                                    icon = tab.icon
                                )
                            }
                        }
                        when (sheetTab) {
                            0 -> ModelScreen(modelVm, onImport = { picker.launch(arrayOf("*/*")) })
                            1 -> SettingsScreen(settingsVm)
                            else -> DiagnosticsScreen(chatVm)
                        }
                    }
                }
            }
        }

        if (pendingDeleteId != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text(stringResource(R.string.delete_conversation_title)) },
                text = { Text(stringResource(R.string.delete_conversation_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        chatVm.deleteConversation(requireNotNull(pendingDeleteId))
                        pendingDeleteId = null
                    }) { Text(stringResource(R.string.drawer_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.cancel_button)) }
                }
            )
        }
    }
}
