package com.example.offlinellm.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.offlinellm.R
import com.example.offlinellm.BuildConfig
import com.example.offlinellm.domain.GenerationState
import com.example.offlinellm.viewmodel.ChatViewModel
import com.example.offlinellm.viewmodel.ModelViewModel
import com.example.offlinellm.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

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
@OptIn(ExperimentalFoundationApi::class)
fun ChatScreen(vm: ChatViewModel, onOpenModels: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isLoading = state.generationState is GenerationState.LoadingModel
    val isGenerating = state.generationState is GenerationState.Generating
    val isBusy = isLoading || isGenerating

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                var menuOpen by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.84f),
                        colors = CardDefaults.cardColors(containerColor = bubbleColor),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 6.dp,
                            bottomEnd = if (isUser) 6.dp else 18.dp
                        )
                    ) {
                        Text(
                            text = msg.content,
                            modifier = Modifier
                                .padding(12.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { menuOpen = true }
                                ),
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_message)) },
                            onClick = {
                                clipboard.setText(AnnotatedString(msg.content))
                                android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
                                menuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_conversation)) },
                            onClick = {
                                scope.launch {
                                    val full = vm.exportActiveConversation()
                                    clipboard.setText(AnnotatedString(full))
                                    android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
                                }
                                menuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_message)) },
                            onClick = {
                                vm.deleteMessage(msg.id)
                                menuOpen = false
                            }
                        )
                    }
                }
            }

            if (state.streamingText.isNotBlank()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.84f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(
                                text = state.streamingText,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            if (isGenerating && state.streamingText.isBlank()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.30f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            TypingIndicator()
                        }
                    }
                }
            }
        }

        ScreenCard {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInputChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.chat_input_label)) },
                    enabled = state.activeModel != null && !isLoading,
                    shape = RoundedCornerShape(20.dp),
                    minLines = 1,
                    maxLines = 4
                )
                Button(
                    onClick = if (isGenerating) vm::stopGeneration else vm::send,
                    enabled = state.activeModel != null && (!isBusy || isGenerating),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.Send,
                        contentDescription = if (isGenerating) stringResource(R.string.stop_button) else stringResource(R.string.send_button),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val a1 = transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "dot1")
    val a2 = transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(550, delayMillis = 120), RepeatMode.Reverse), label = "dot2")
    val a3 = transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(550, delayMillis = 240), RepeatMode.Reverse), label = "dot3")
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = a1.value))
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = a2.value))
        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = a3.value))
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
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
                        stringResource(R.string.settings_language),
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
                    Text(
                        stringResource(R.string.settings_prompt_template),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageButton(
                            selected = settings.promptTemplate == "auto",
                            label = stringResource(R.string.prompt_template_auto)
                        ) { vm.update(settings.copy(promptTemplate = "auto")) }
                        LanguageButton(
                            selected = settings.promptTemplate == "tinyllama_chatml",
                            label = stringResource(R.string.prompt_template_tinyllama)
                        ) { vm.update(settings.copy(promptTemplate = "tinyllama_chatml")) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageButton(
                            selected = settings.promptTemplate == "alpaca",
                            label = stringResource(R.string.prompt_template_alpaca)
                        ) { vm.update(settings.copy(promptTemplate = "alpaca")) }
                        LanguageButton(
                            selected = settings.promptTemplate == "plain",
                            label = stringResource(R.string.prompt_template_plain)
                        ) { vm.update(settings.copy(promptTemplate = "plain")) }
                    }
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
                    OutlinedButton(
                        onClick = { showAdvanced = !showAdvanced },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (showAdvanced) {
                                stringResource(R.string.settings_hide_advanced)
                            } else {
                                stringResource(R.string.settings_show_advanced)
                            }
                        )
                    }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = showAdvanced,
                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(120))
            ) {
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
private fun RowScope.LanguageButton(selected: Boolean, label: String, onClick: () -> Unit) {
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
            Text(stringResource(R.string.info_prompt_tokens, state.perf.promptTokens))
            Text(stringResource(R.string.info_prompt_utf8_length, state.perf.promptUtf8Length))
            Text(stringResource(R.string.info_selected_prompt_template, state.perf.selectedPromptTemplate))
            Text(stringResource(R.string.info_stop_reason, state.perf.stopReason.ifBlank { stringResource(R.string.info_none) }))
            Text(stringResource(R.string.info_stop_marker, state.perf.stopMarker.ifBlank { stringResource(R.string.info_none) }))
            Text(stringResource(R.string.info_generated_tokens, state.perf.generatedTokens))
            Text(stringResource(R.string.info_tokens_per_second, String.format("%.2f", state.perf.tokensPerSecond)))
            Text(stringResource(R.string.info_time_to_first_token, state.perf.timeToFirstTokenMs))
            Text(stringResource(R.string.info_model_load_time, state.perf.modelLoadMs))
            Text(stringResource(R.string.info_prompt_preview_start, state.perf.promptPreviewStart))
            Text(stringResource(R.string.info_prompt_preview_end, state.perf.promptPreviewEnd))
            Text(
                stringResource(
                    R.string.info_build,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    BuildConfig.GIT_HASH,
                    BuildConfig.BUILD_TIME_UTC
                )
            )
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
