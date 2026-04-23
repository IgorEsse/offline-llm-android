package com.example.offlinellm

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(factory: AppViewModelFactory) {
    var tab by remember { mutableStateOf(0) }
    val chatVm: ChatViewModel = viewModel(factory = factory)
    val modelVm: ModelViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val tabTitles = listOf("Models", "Chat", "Settings", "Info")

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { modelVm.import(it.toString()) }
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF8F9FF),
            Color(0xFFEFF3FF),
            Color(0xFFE8EEFF)
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(tabTitles[tab]) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color(0xFF1D243A)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 0.dp,
                color = Color(0xB3FFFFFF),
                shadowElevation = 10.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                NavigationBar(containerColor = Color.Transparent) {
                    tabTitles.forEachIndexed { i, t ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, label = { Text(t) }, icon = {})
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
                .clip(RoundedCornerShape(24.dp))
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                when (tab) {
                    0 -> ModelScreen(modelVm, onImport = { picker.launch(arrayOf("*/*")) })
                    1 -> ChatScreen(chatVm)
                    2 -> SettingsScreen(settingsVm)
                    else -> DiagnosticsScreen(chatVm)
                }
            }
        }
    }
}
