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
    val promptUtf8Length: Int = 0,
    val promptPreviewStart: String = "",
    val promptPreviewEnd: String = "",
    val selectedPromptTemplate: String = "auto",
    val stopReason: String = "",
    val stopMarker: String = "",
    val generatedTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val timeToFirstTokenMs: Long = 0L,
    val modelLoadMs: Long = 0L
)

private object PromptTemplate {
    private const val historyLimit = 8
    private const val historyCharBudget = 1200
    enum class Kind { AUTO, TINYLLAMA_CHATML, ALPACA, PLAIN }

    private val tinyLlamaStopMarkers = listOf("</s>", "<|user|>", "<|system|>")
    private val alpacaStopMarkers = listOf("\n### Instruction:", "\n### User:", "\n### System:")
    private val plainStopMarkers = listOf("\nUser:", "\n### User:")

    fun resolveKind(model: ModelInfo?, selected: String): Kind {
        return when (selected.lowercase(Locale.ROOT)) {
            "tinyllama_chatml" -> Kind.TINYLLAMA_CHATML
            "alpaca" -> Kind.ALPACA
            "plain" -> Kind.PLAIN
            else -> {
                val hint = "${model?.filename ?: ""} ${model?.metadata ?: ""}".lowercase(Locale.ROOT)
                if (hint.contains("tinyllama") || hint.contains("chatml")) Kind.TINYLLAMA_CHATML else Kind.ALPACA
            }
        }
    }

    fun kindKey(kind: Kind): String = when (kind) {
        Kind.AUTO -> "auto"
        Kind.TINYLLAMA_CHATML -> "tinyllama_chatml"
        Kind.ALPACA -> "alpaca"
        Kind.PLAIN -> "plain"
    }

    fun buildPrompt(kind: Kind, systemPrompt: String, history: List<ChatMessage>, userInput: String): String {
        val trimmedHistory = history.takeLast(historyLimit).takeLastWithinCharBudget(historyCharBudget)
        val sb = StringBuilder(2048)
        when (kind) {
            Kind.TINYLLAMA_CHATML -> {
                sb.append("<|system|>\n").append(systemPrompt.trim()).append("</s>\n")
                trimmedHistory.forEach { msg ->
                    when (msg.role.lowercase(Locale.ROOT)) {
                        "user" -> sb.append("<|user|>\n").append(msg.content.trim()).append("</s>\n")
                        "assistant" -> sb.append("<|assistant|>\n").append(msg.content.trim()).append("</s>\n")
                    }
                }
                sb.append("<|user|>\n").append(userInput.trim()).append("</s>\n")
                sb.append("<|assistant|>\n")
            }
            Kind.ALPACA -> {
                sb.append("### System:\n").append(systemPrompt.trim()).append("\n\n")
                trimmedHistory.forEach { msg ->
                    when (msg.role.lowercase(Locale.ROOT)) {
                        "user" -> sb.append("### User:\n").append(msg.content.trim()).append("\n\n")
                        "assistant" -> sb.append("### Assistant:\n").append(msg.content.trim()).append("\n\n")
                    }
                }
                sb.append("### User:\n").append(userInput.trim()).append("\n\n### Assistant:\n")
            }
            Kind.PLAIN, Kind.AUTO -> {
                sb.append(systemPrompt.trim()).append("\n\n")
                trimmedHistory.forEach { msg ->
                    val role = if (msg.role.equals("user", true)) "User" else "Assistant"
                    sb.append(role).append(": ").append(msg.content.trim()).append("\n")
                }
                sb.append("User: ").append(userInput.trim()).append("\nAssistant: ")
            }
        }
        return sb.toString()
    }

    fun stopMarkers(kind: Kind): List<String> = when (kind) {
        Kind.TINYLLAMA_CHATML -> tinyLlamaStopMarkers
        Kind.ALPACA -> alpacaStopMarkers
        Kind.PLAIN, Kind.AUTO -> plainStopMarkers
    }

    fun cutAtStopMarker(text: String, markers: List<String>): Pair<String, String?> {
        val match = markers
            .mapNotNull { marker ->
                val idx = text.indexOf(marker)
                if (idx >= 0) idx to marker else null
            }
            .minByOrNull { it.first } ?: return text to null
        return text.substring(0, match.first) to match.second
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
            val selectedKind = PromptTemplate.resolveKind(model, settings.promptTemplate)
            val stopMarkers = PromptTemplate.stopMarkers(selectedKind)
            val promptForModel = PromptTemplate.buildPrompt(selectedKind, settings.systemPrompt, existingMessages, prompt)
            val promptTokenCount = LlamaNativeBridge.countTokens(promptForModel).coerceAtLeast(0)
            val promptUtf8Length = promptForModel.toByteArray(Charsets.UTF_8).size
            val promptPreviewStart = promptForModel.take(300)
            val promptPreviewEnd = promptForModel.takeLast(300)

            val builder = StringBuilder()
            streaming.value = ""
            gen.value = GenerationState.Generating
            perf.value = perf.value.copy(
                promptTokens = promptTokenCount,
                promptUtf8Length = promptUtf8Length,
                promptPreviewStart = promptPreviewStart,
                promptPreviewEnd = promptPreviewEnd,
                selectedPromptTemplate = PromptTemplate.kindKey(selectedKind),
                stopReason = "",
                stopMarker = "",
                generatedTokens = 0,
                tokensPerSecond = 0.0,
                timeToFirstTokenMs = 0L,
                modelLoadMs = modelLoadMs
            )

            val generationStart = SystemClock.elapsedRealtime()
            var firstTokenMs = 0L
            var emittedChunks = 0
            var stopByMarker = false
            var stopMarkerMatched: String? = null

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
                    val (trimmed, matchedMarker) = PromptTemplate.cutAtStopMarker(builder.toString(), stopMarkers)
                    if (matchedMarker != null) {
                        stopByMarker = true
                        stopMarkerMatched = matchedMarker
                        builder.setLength(0)
                        builder.append(trimmed)
                        LlamaNativeBridge.stopGeneration()
                    }
                    streaming.value = builder.toString()
                    perf.value = perf.value.copy(
                        generatedTokens = emittedChunks,
                        timeToFirstTokenMs = firstTokenMs,
                        stopReason = if (stopByMarker) "STOP_MARKER" else "",
                        stopMarker = stopMarkerMatched ?: ""
                    )
                }
            }.onFailure {
                gen.value = if ((it.message ?: "").contains("canceled", true)) {
                    GenerationState.Canceled
                } else {
                    GenerationState.Error(it.message ?: "GENERATION_FAILED")
                }
                perf.value = perf.value.copy(
                    stopReason = if (gen.value == GenerationState.Canceled) "CANCELED" else "ERROR",
                    stopMarker = stopMarkerMatched ?: ""
                )
            }

            val (finalTextRaw, finalMarker) = PromptTemplate.cutAtStopMarker(builder.toString(), stopMarkers)
            if (stopMarkerMatched == null && finalMarker != null) {
                stopMarkerMatched = finalMarker
            }
            val finalText = finalTextRaw.trim()
            if (finalText.isNotBlank()) {
                chatRepository.addMessage("assistant", finalText)
            }
            val totalMs = (SystemClock.elapsedRealtime() - generationStart).coerceAtLeast(1L)
            val generatedTokens = LlamaNativeBridge.countTokens(finalText).takeIf { it >= 0 } ?: emittedChunks
            perf.value = perf.value.copy(
                generatedTokens = generatedTokens,
                tokensPerSecond = (generatedTokens * 1000.0) / totalMs,
                timeToFirstTokenMs = firstTokenMs,
                stopReason = when {
                    stopByMarker -> "STOP_MARKER"
                    gen.value == GenerationState.Canceled -> "CANCELED"
                    else -> "EOS_OR_MAXTOKENS"
                },
                stopMarker = stopMarkerMatched ?: ""
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
