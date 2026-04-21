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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlinellm.data.repo.AppGraph
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

@Composable
private fun Root(factory: AppViewModelFactory) {
    var tab by remember { mutableStateOf(0) }
    val chatVm: ChatViewModel = viewModel(factory = factory)
    val modelVm: ModelViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { modelVm.import(it.toString()) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                listOf("Models", "Chat", "Settings", "Info").forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, label = { Text(t) }, icon = {})
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            when (tab) {
                0 -> ModelScreen(modelVm, onImport = { picker.launch(arrayOf("*/*")) })
                1 -> ChatScreen(chatVm)
                2 -> SettingsScreen(settingsVm)
                else -> DiagnosticsScreen(chatVm)
            }
        }
    }
}
