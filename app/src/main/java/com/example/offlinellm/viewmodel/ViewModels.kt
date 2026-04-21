package com.example.offlinellm.viewmodel

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val generationState: GenerationState = GenerationState.Idle,
    val activeModel: ModelInfo? = null,
    val streamingText: String = ""
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val input = MutableStateFlow("")
    private val gen = MutableStateFlow<GenerationState>(GenerationState.Idle)
    private val streaming = MutableStateFlow("")

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.messages,
        input,
        gen,
        modelRepository.models,
        streaming
    ) { messages, inputText, generation, models, streamingText ->
        ChatUiState(messages, inputText, generation, models.firstOrNull { it.isActive }, streamingText)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    fun onInputChanged(value: String) { input.value = value }

    fun send() {
        val prompt = input.value.trim()
        if (prompt.isEmpty()) return
        input.value = ""
        viewModelScope.launch {
            val model = modelRepository.getActiveModel() ?: run {
                gen.value = GenerationState.Error("No active model selected")
                return@launch
            }
            val settings = settingsRepository.settings.first()
            gen.value = GenerationState.LoadingModel
            if (!LlamaNativeBridge.isModelLoaded()) {
                val ok = LlamaNativeBridge.loadModel(model.path, settings.contextSize, settings.threads)
                if (!ok) {
                    gen.value = GenerationState.Error("Model load failure")
                    return@launch
                }
            }
            chatRepository.addMessage("user", prompt)
            val builder = StringBuilder()
            streaming.value = ""
            gen.value = GenerationState.Generating
            runCatching {
                LlamaNativeBridge.generate(
                    prompt = "${settings.systemPrompt}\n\nUser: $prompt\nAssistant:",
                    temperature = settings.temperature,
                    topK = settings.topK,
                    topP = settings.topP,
                    maxTokens = settings.maxTokens
                ).collect { token ->
                    builder.append(token)
                    streaming.value = builder.toString()
                }
            }.onFailure {
                gen.value = if ((it.message ?: "").contains("canceled", true)) GenerationState.Canceled else GenerationState.Error(it.message ?: "generation failed")
            }
            if (builder.isNotEmpty()) chatRepository.addMessage("assistant", builder.toString())
            streaming.value = ""
            if (gen.value !is GenerationState.Error && gen.value != GenerationState.Canceled) gen.value = GenerationState.Idle
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
