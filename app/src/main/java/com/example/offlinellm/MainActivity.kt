package com.example.offlinellm

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
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

private data class SheetTabItem(
    val labelRes: Int,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(factory: AppViewModelFactory) {
    var showSheet by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableIntStateOf(0) }
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
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
    ) { pad ->
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
}
