package com.example.offlinellm.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.offlinellm.domain.GenerationState
import com.example.offlinellm.viewmodel.ChatViewModel
import com.example.offlinellm.viewmodel.ModelViewModel
import com.example.offlinellm.viewmodel.SettingsViewModel

@Composable
fun ModelScreen(vm: ModelViewModel, onImport: () -> Unit) {
    val models by vm.models.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Model library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Import GGUF files and choose the active model for chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onImport, shape = RoundedCornerShape(14.dp)) { Text("Import .gguf model") }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(models) { m ->
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(m.filename, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text("${m.sizeBytes / (1024 * 1024)} MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(m.metadata ?: "No metadata", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.setActive(m.id) }, shape = RoundedCornerShape(12.dp)) {
                                Text(if (m.isActive) "Active" else "Set active")
                            }
                            Button(
                                onClick = { vm.delete(m.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Delete")
                            }
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
    val isBusy = state.generationState is GenerationState.Generating || state.generationState is GenerationState.LoadingModel

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On-device assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Everything stays local. No remote inference.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedVisibility(visible = state.generationState is GenerationState.Error) {
                    val message = (state.generationState as? GenerationState.Error)?.message.orEmpty()
                    Text("Error: $message", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages) { msg ->
                val isUser = msg.role.equals("user", ignoreCase = true)
                val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else Color(0xAAFFFFFF)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = bubbleColor),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "${msg.role}: ${msg.content}",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (state.streamingText.isNotBlank()) {
                item {
                    GlassCard {
                        Text("assistant: ${state.streamingText}")
                    }
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Type your message") },
                    shape = RoundedCornerShape(18.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::send, enabled = !isBusy, shape = RoundedCornerShape(12.dp)) { Text("Send") }
                    if (state.generationState is GenerationState.Generating) {
                        Button(onClick = vm::stopGeneration, shape = RoundedCornerShape(12.dp)) { Text("Stop") }
                    }
                    Button(onClick = vm::clearChat, shape = RoundedCornerShape(12.dp)) { Text("Clear") }
                    if (isBusy) {
                        Spacer(Modifier.width(4.dp))
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassCard {
            Text("Inference settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    settings.systemPrompt,
                    { vm.update(settings.copy(systemPrompt = it)) },
                    label = { Text("System prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                NumberField("Temperature", settings.temperature.toString()) { vm.update(settings.copy(temperature = it.toFloatOrNull() ?: settings.temperature)) }
                NumberField("Top K", settings.topK.toString()) { vm.update(settings.copy(topK = it.toIntOrNull() ?: settings.topK)) }
                NumberField("Top P", settings.topP.toString()) { vm.update(settings.copy(topP = it.toFloatOrNull() ?: settings.topP)) }
                NumberField("Max tokens", settings.maxTokens.toString()) { vm.update(settings.copy(maxTokens = it.toIntOrNull() ?: settings.maxTokens)) }
                NumberField("Context size", settings.contextSize.toString()) { vm.update(settings.copy(contextSize = it.toIntOrNull() ?: settings.contextSize)) }
                NumberField("Threads", settings.threads.toString()) { vm.update(settings.copy(threads = it.toIntOrNull() ?: settings.threads)) }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun DiagnosticsScreen(chatViewModel: ChatViewModel) {
    val state by chatViewModel.uiState.collectAsState()
    GlassCard(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("ABI: arm64-v8a")
            Text("Selected model: ${state.activeModel?.path ?: "None"}")
            Text("Last load status: ${state.generationState}")
            Text("Speed metric: expose tok/s from native in next iteration")
            Text("Privacy: chats stay on-device.")
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.18f))
                .padding(14.dp)
        ) {
            content()
        }
    }
}
