package com.example.offlinellm.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.offlinellm.R
import com.example.offlinellm.domain.GenerationState
import com.example.offlinellm.viewmodel.ChatViewModel
import com.example.offlinellm.viewmodel.ModelViewModel
import com.example.offlinellm.viewmodel.SettingsViewModel

@Composable
fun ModelScreen(vm: ModelViewModel, onImport: () -> Unit) {
    val models by vm.models.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.models_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.models_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onImport, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.import_model_button))
                }
            }
        }

        if (models.isEmpty()) {
            ScreenCard {
                Text(stringResource(R.string.models_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(models) { m ->
                ScreenCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(m.filename, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.models_size_mb, (m.sizeBytes / (1024 * 1024)).toString()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(m.metadata ?: stringResource(R.string.models_no_metadata), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.setActive(m.id) }, shape = RoundedCornerShape(12.dp)) {
                                Text(stringResource(if (m.isActive) R.string.models_active else R.string.models_set_active))
                            }
                            Button(
                                onClick = { vm.delete(m.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.delete_button))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel, onOpenModels: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isLoading = state.generationState is GenerationState.LoadingModel
    val isGenerating = state.generationState is GenerationState.Generating
    val isBusy = isLoading || isGenerating

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.chat_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusLine(state.generationState)
            }
        }

        if (state.activeModel == null) {
            ScreenCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.chat_empty_no_model_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.chat_empty_no_model_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenModels, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.chat_go_to_models))
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages) { msg ->
                val isUser = msg.role.equals("user", ignoreCase = true)
                val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = bubbleColor),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "${if (isUser) stringResource(R.string.chat_role_user) else stringResource(R.string.chat_role_assistant)}: ${msg.content}",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (state.streamingText.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.chat_role_assistant)}: ${state.streamingText}",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        ScreenCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.chat_input_label)) },
                    enabled = state.activeModel != null,
                    shape = RoundedCornerShape(16.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::send,
                        enabled = !isBusy && state.activeModel != null,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.send_button)) }

                    if (isGenerating) {
                        Button(onClick = vm::stopGeneration, shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.stop_button))
                        }
                    }

                    Button(onClick = vm::clearChat, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.clear_button))
                    }

                    AnimatedVisibility(visible = isBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(4.dp))
                            CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenCard {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        item {
            ScreenCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.settings_language_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.settings_language_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageButton(
                            selected = settings.uiLanguage == "system",
                            label = stringResource(R.string.language_system)
                        ) { vm.update(settings.copy(uiLanguage = "system")) }
                        LanguageButton(
                            selected = settings.uiLanguage == "en",
                            label = stringResource(R.string.language_english)
                        ) { vm.update(settings.copy(uiLanguage = "en")) }
                        LanguageButton(
                            selected = settings.uiLanguage == "ru",
                            label = stringResource(R.string.language_russian)
                        ) { vm.update(settings.copy(uiLanguage = "ru")) }
                    }
                }
            }
        }
        item {
            ScreenCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.settings_inference_basic_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = settings.systemPrompt,
                        onValueChange = { vm.update(settings.copy(systemPrompt = it)) },
                        label = { Text(stringResource(R.string.settings_system_prompt)) },
                        supportingText = { Text(stringResource(R.string.settings_system_prompt_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(14.dp)
                    )
                    NumberField(
                        label = stringResource(R.string.settings_temperature),
                        value = settings.temperature.toString(),
                        onChange = { vm.update(settings.copy(temperature = it.toFloatOrNull() ?: settings.temperature)) },
                        helper = stringResource(R.string.settings_temperature_hint),
                        keyboardType = KeyboardType.Decimal
                    )
                    NumberField(
                        label = stringResource(R.string.settings_top_p),
                        value = settings.topP.toString(),
                        onChange = { vm.update(settings.copy(topP = it.toFloatOrNull() ?: settings.topP)) },
                        helper = stringResource(R.string.settings_top_p_hint),
                        keyboardType = KeyboardType.Decimal
                    )
                    NumberField(
                        label = stringResource(R.string.settings_max_tokens),
                        value = settings.maxTokens.toString(),
                        onChange = { vm.update(settings.copy(maxTokens = it.toIntOrNull() ?: settings.maxTokens)) },
                        helper = stringResource(R.string.settings_max_tokens_hint)
                    )
                }
            }
        }
        item {
            ScreenCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.settings_inference_advanced_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    NumberField(
                        label = stringResource(R.string.settings_top_k),
                        value = settings.topK.toString(),
                        onChange = { vm.update(settings.copy(topK = it.toIntOrNull() ?: settings.topK)) },
                        helper = stringResource(R.string.settings_top_k_hint)
                    )
                    NumberField(
                        label = stringResource(R.string.settings_context_size),
                        value = settings.contextSize.toString(),
                        onChange = { vm.update(settings.copy(contextSize = it.toIntOrNull() ?: settings.contextSize)) },
                        helper = stringResource(R.string.settings_context_size_hint)
                    )
                    NumberField(
                        label = stringResource(R.string.settings_threads),
                        value = settings.threads.toString(),
                        onChange = { vm.update(settings.copy(threads = it.toIntOrNull() ?: settings.threads)) },
                        helper = stringResource(R.string.settings_threads_hint)
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = {
            if (!helper.isNullOrBlank()) {
                Text(helper)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        singleLine = true
    )
}

@Composable
private fun LanguageButton(selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
    val buttonModifier = Modifier.weight(1f)
    OutlinedButton(
        onClick = onClick,
        modifier = buttonModifier,
        colors = colors,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun StatusLine(state: GenerationState) {
    val color = when (state) {
        is GenerationState.Error -> MaterialTheme.colorScheme.error
        GenerationState.Generating, GenerationState.LoadingModel -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val suffix = if (state is GenerationState.Error) ": ${localizedError(state.message)}" else ""
    Text(text = statusLabel(state) + suffix, color = color)
}

@Composable
private fun statusLabel(state: GenerationState): String = when (state) {
    GenerationState.Idle -> stringResource(R.string.status_idle)
    GenerationState.LoadingModel -> stringResource(R.string.status_loading_model)
    GenerationState.Generating -> stringResource(R.string.status_generating)
    GenerationState.Canceled -> stringResource(R.string.status_canceled)
    is GenerationState.Error -> stringResource(R.string.status_error)
}

@Composable
private fun localizedError(codeOrMessage: String): String {
    return when (codeOrMessage) {
        "NO_MODEL" -> stringResource(R.string.error_no_model)
        "LOAD_FAILED" -> stringResource(R.string.error_load_failed)
        "GENERATION_FAILED" -> stringResource(R.string.error_generation_failed)
        else -> codeOrMessage
    }
}

@Composable
fun DiagnosticsScreen(chatViewModel: ChatViewModel) {
    val state by chatViewModel.uiState.collectAsStateWithLifecycle()
    ScreenCard(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.info_abi))
            Text(stringResource(R.string.info_model, state.activeModel?.path ?: stringResource(R.string.info_none)))
            Text(stringResource(R.string.info_status, statusLabel(state.generationState)))
            Text(stringResource(R.string.info_privacy))
        }
    }
}

@Composable
private fun ScreenCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}
