package com.example.offlinellm

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

private data class TabItem(
    val labelRes: Int,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(factory: AppViewModelFactory) {
    var tab by remember { mutableIntStateOf(0) }
    val chatVm: ChatViewModel = viewModel(factory = factory)
    val modelVm: ModelViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    LaunchedEffect(settings.uiLanguage) {
        LocaleManager.applyLanguage(settings.uiLanguage)
    }

    val tabs = listOf(
        TabItem(R.string.tab_chat) { Icon(Icons.Default.Chat, contentDescription = null) },
        TabItem(R.string.tab_models) { Icon(Icons.Default.Folder, contentDescription = null) },
        TabItem(R.string.tab_settings) { Icon(Icons.Default.Settings, contentDescription = null) },
        TabItem(R.string.tab_info) { Icon(Icons.Default.Info, contentDescription = null) }
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { modelVm.import(it.toString()) }
    }

    val bgBrush = Brush.verticalGradient(
        listOf(
            androidx.compose.material3.MaterialTheme.colorScheme.background,
            androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tabs[tab].labelRes)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                NavigationBar {
                    tabs.forEachIndexed { i, item ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            label = { Text(stringResource(item.labelRes)) },
                            icon = item.icon
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(pad)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when (tab) {
                0 -> ChatScreen(chatVm, onOpenModels = { tab = 1 })
                1 -> ModelScreen(modelVm, onImport = { picker.launch(arrayOf("*/*")) })
                2 -> SettingsScreen(settingsVm)
                else -> DiagnosticsScreen(chatVm)
            }
        }
    }
}
