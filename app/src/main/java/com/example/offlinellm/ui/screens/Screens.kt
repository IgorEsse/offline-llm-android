package com.example.offlinellm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.offlinellm.domain.GenerationState
import com.example.offlinellm.domain.InferenceSettings
import com.example.offlinellm.viewmodel.ChatViewModel
import com.example.offlinellm.viewmodel.ModelViewModel
import com.example.offlinellm.viewmodel.SettingsViewModel

@Composable
fun ModelScreen(vm: ModelViewModel, onImport: () -> Unit) {
    val models by vm.models.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onImport) { Text("Import GGUF model") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(models) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.filename)
                        Text("${m.sizeBytes / (1024 * 1024)} MB")
                        Text(m.metadata ?: "No metadata")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.setActive(m.id) }) { Text(if (m.isActive) "Active" else "Set active") }
                            Button(onClick = { vm.delete(m.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val state by vm.uiState.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Local-only chat. No remote inference.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.messages) { msg ->
                Card(Modifier.fillMaxWidth()) { Text("${msg.role}: ${msg.content}", Modifier.padding(8.dp)) }
            }
            if (state.streamingText.isNotBlank()) {
                item { Card(Modifier.fillMaxWidth()) { Text("assistant: ${state.streamingText}", Modifier.padding(8.dp)) } }
            }
        }
        OutlinedTextField(value = state.input, onValueChange = vm::onInputChanged, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::send) { Text("Send") }
            if (state.generationState is GenerationState.Generating) {
                Button(onClick = vm::stopGeneration) { Text("Stop") }
            }
            Button(onClick = vm::clearChat) { Text("Clear") }
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Inference settings")
        OutlinedTextField(settings.systemPrompt, { vm.update(settings.copy(systemPrompt = it)) }, label = { Text("System prompt") })
        NumberField("Temperature", settings.temperature.toString()) { vm.update(settings.copy(temperature = it.toFloatOrNull() ?: settings.temperature)) }
        NumberField("Top K", settings.topK.toString()) { vm.update(settings.copy(topK = it.toIntOrNull() ?: settings.topK)) }
        NumberField("Top P", settings.topP.toString()) { vm.update(settings.copy(topP = it.toFloatOrNull() ?: settings.topP)) }
        NumberField("Max tokens", settings.maxTokens.toString()) { vm.update(settings.copy(maxTokens = it.toIntOrNull() ?: settings.maxTokens)) }
        NumberField("Context size", settings.contextSize.toString()) { vm.update(settings.copy(contextSize = it.toIntOrNull() ?: settings.contextSize)) }
        NumberField("Threads", settings.threads.toString()) { vm.update(settings.copy(threads = it.toIntOrNull() ?: settings.threads)) }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
fun DiagnosticsScreen(chatViewModel: ChatViewModel) {
    val state by chatViewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ABI: arm64-v8a")
        Text("Selected model: ${state.activeModel?.path ?: "None"}")
        Text("Last load status: ${state.generationState}")
        Text("Speed metric: expose tok/s from native in next iteration")
        Text("Privacy: chats stay on-device.")
    }
}
