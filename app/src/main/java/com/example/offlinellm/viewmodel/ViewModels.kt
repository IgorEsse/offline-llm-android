package com.example.offlinellm.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.offlinellm.domain.ChatMessage
import com.example.offlinellm.domain.ChatRepository
import com.example.offlinellm.domain.GenerationState
import com.example.offlinellm.domain.InferenceSettings
import com.example.offlinellm.domain.ModelInfo
import com.example.offlinellm.domain.ModelRepository
import com.example.offlinellm.domain.SettingsRepository
import com.example.offlinellm.nativebridge.LlamaNativeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val generationState: GenerationState = GenerationState.Idle,
    val activeModel: ModelInfo? = null,
    val streamingText: String = "",
    val perf: GenerationPerfStats = GenerationPerfStats()
)

data class GenerationPerfStats(
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val timeToFirstTokenMs: Long = 0L,
    val modelLoadMs: Long = 0L
)

private object PromptTemplate {
    private const val historyLimit = 8
    private const val historyCharBudget = 1200

    val stopMarkers = listOf(
        "\n### User:",
        "\nUser:",
        "\nUSER:",
        "\n### Human:",
        "\nHuman:",
        "<|user|>",
        "<|im_start|>user"
    )

    fun buildPrompt(systemPrompt: String, history: List<ChatMessage>, userInput: String): String {
        val sb = StringBuilder(2048)
        sb.append("### System:\n")
        sb.append(systemPrompt.trim())
        sb.append("\n\n")

        val trimmedHistory = history
            .takeLast(historyLimit)
            .takeLastWithinCharBudget(historyCharBudget)

        trimmedHistory.forEach { msg ->
            when (msg.role.lowercase(Locale.ROOT)) {
                "user" -> {
                    sb.append("### User:\n")
                    sb.append(msg.content.trim())
                    sb.append("\n\n")
                }
                "assistant" -> {
                    sb.append("### Assistant:\n")
                    sb.append(msg.content.trim())
                    sb.append("\n\n")
                }
            }
        }

        sb.append("### User:\n")
        sb.append(userInput.trim())
        sb.append("\n\n### Assistant:\n")
        return sb.toString()
    }

    fun cutAtStopMarker(text: String): String {
        val match = stopMarkers
            .map { marker -> text.indexOf(marker) }
            .filter { it >= 0 }
            .minOrNull() ?: return text
        return text.substring(0, match)
    }

    private fun List<ChatMessage>.takeLastWithinCharBudget(charBudget: Int): List<ChatMessage> {
        if (isEmpty()) return emptyList()
        var remaining = charBudget
        val selected = ArrayList<ChatMessage>()
        for (i in indices.reversed()) {
            val c = this[i].content.length + 20
            if (selected.isNotEmpty() && remaining - c < 0) break
            selected.add(this[i])
            remaining -= c
        }
        selected.reverse()
        return selected
    }
}

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val input = MutableStateFlow("")
    private val gen = MutableStateFlow<GenerationState>(GenerationState.Idle)
    private val streaming = MutableStateFlow("")
    private val perf = MutableStateFlow(GenerationPerfStats())
    private var loadedModelId: Long? = null

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.messages,
        input,
        gen,
        modelRepository.models,
        streaming,
        perf
    ) { values ->
        val messages = values[0] as List<ChatMessage>
        val inputText = values[1] as String
        val generation = values[2] as GenerationState
        val models = values[3] as List<ModelInfo>
        val streamingText = values[4] as String
        val perfState = values[5] as GenerationPerfStats
        ChatUiState(messages, inputText, generation, models.firstOrNull { it.isActive }, streamingText, perfState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun onInputChanged(value: String) {
        input.value = value
    }

    fun send() {
        val prompt = input.value.trim()
        if (prompt.isEmpty()) return
        input.value = ""

        viewModelScope.launch {
            val model = modelRepository.getActiveModel() ?: run {
                gen.value = GenerationState.Error("NO_MODEL")
                return@launch
            }

            val settings = settingsRepository.settings.first()
            gen.value = GenerationState.LoadingModel

            val shouldReload = loadedModelId != model.id || !LlamaNativeBridge.isModelLoaded()
            var modelLoadMs = 0L
            if (shouldReload) {
                if (LlamaNativeBridge.isModelLoaded()) LlamaNativeBridge.unloadModel()
                val loadStart = SystemClock.elapsedRealtime()
                val ok = LlamaNativeBridge.loadModel(model.path, settings.contextSize, settings.threads)
                modelLoadMs = SystemClock.elapsedRealtime() - loadStart
                if (!ok) {
                    gen.value = GenerationState.Error("LOAD_FAILED")
                    return@launch
                }
                loadedModelId = model.id
            }

            val existingMessages = chatRepository.messages.first()
            chatRepository.addMessage("user", prompt)
            val promptForModel = PromptTemplate.buildPrompt(settings.systemPrompt, existingMessages, prompt)
            val promptTokenCount = LlamaNativeBridge.countTokens(promptForModel).coerceAtLeast(0)

            val builder = StringBuilder()
            streaming.value = ""
            gen.value = GenerationState.Generating
            perf.value = perf.value.copy(
                promptTokens = promptTokenCount,
                generatedTokens = 0,
                tokensPerSecond = 0.0,
                timeToFirstTokenMs = 0L,
                modelLoadMs = modelLoadMs
            )

            val generationStart = SystemClock.elapsedRealtime()
            var firstTokenMs = 0L
            var emittedChunks = 0
            var stopByMarker = false

            runCatching {
                LlamaNativeBridge.generate(
                    prompt = promptForModel,
                    temperature = settings.temperature,
                    topK = settings.topK,
                    topP = settings.topP,
                    maxTokens = settings.maxTokens
                ).collect { token ->
                    if (stopByMarker) return@collect

                    if (firstTokenMs == 0L && token.isNotBlank()) {
                        firstTokenMs = SystemClock.elapsedRealtime() - generationStart
                    }
                    builder.append(token)
                    emittedChunks++
                    val trimmed = PromptTemplate.cutAtStopMarker(builder.toString())
                    if (trimmed.length != builder.length) {
                        stopByMarker = true
                        builder.setLength(0)
                        builder.append(trimmed)
                        LlamaNativeBridge.stopGeneration()
                    }
                    streaming.value = builder.toString()
                    perf.value = perf.value.copy(
                        generatedTokens = emittedChunks,
                        timeToFirstTokenMs = firstTokenMs
                    )
                }
            }.onFailure {
                gen.value = if ((it.message ?: "").contains("canceled", true)) {
                    GenerationState.Canceled
                } else {
                    GenerationState.Error(it.message ?: "GENERATION_FAILED")
                }
            }

            val finalText = PromptTemplate.cutAtStopMarker(builder.toString()).trim()
            if (finalText.isNotBlank()) {
                chatRepository.addMessage("assistant", finalText)
            }
            val totalMs = (SystemClock.elapsedRealtime() - generationStart).coerceAtLeast(1L)
            val generatedTokens = LlamaNativeBridge.countTokens(finalText).takeIf { it >= 0 } ?: emittedChunks
            perf.value = perf.value.copy(
                generatedTokens = generatedTokens,
                tokensPerSecond = (generatedTokens * 1000.0) / totalMs,
                timeToFirstTokenMs = firstTokenMs
            )
            streaming.value = ""
            if (gen.value !is GenerationState.Error && gen.value != GenerationState.Canceled) {
                gen.value = GenerationState.Idle
            }
        }
    }

    fun stopGeneration() {
        LlamaNativeBridge.stopGeneration()
        gen.value = GenerationState.Canceled
    }

    fun clearChat() = viewModelScope.launch { chatRepository.clear() }
}

class ModelViewModel(private val modelRepository: ModelRepository) : ViewModel() {
    val models = modelRepository.models.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val error = MutableStateFlow<String?>(null)

    fun import(uriString: String) = viewModelScope.launch {
        modelRepository.importModel(uriString).onFailure { error.value = it.message }
    }

    fun setActive(id: Long) = viewModelScope.launch {
        modelRepository.setActiveModel(id).onFailure { error.value = it.message }
    }

    fun delete(id: Long) = viewModelScope.launch {
        modelRepository.deleteModel(id).onFailure { error.value = it.message }
    }
}

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InferenceSettings())
    fun update(value: InferenceSettings) = viewModelScope.launch { settingsRepository.update(value) }
}

class AppViewModelFactory(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when (modelClass) {
            ChatViewModel::class.java -> ChatViewModel(chatRepository, modelRepository, settingsRepository)
            ModelViewModel::class.java -> ModelViewModel(modelRepository)
            SettingsViewModel::class.java -> SettingsViewModel(settingsRepository)
            else -> error("Unknown VM")
        } as T
    }
}
