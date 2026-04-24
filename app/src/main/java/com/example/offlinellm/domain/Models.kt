package com.example.offlinellm.domain

import kotlinx.coroutines.flow.Flow

data class ModelInfo(
    val id: Long,
    val filename: String,
    val path: String,
    val sizeBytes: Long,
    val checksum: String,
    val architecture: String?,
    val metadata: String?,
    val importedAt: Long,
    val isActive: Boolean
)

data class ChatMessage(
    val id: Long,
    val role: String,
    val content: String,
    val timestamp: Long
)

data class InferenceSettings(
    val systemPrompt: String = "You are a helpful assistant. Answer clearly and briefly.",
    val temperature: Float = 0.3f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val maxTokens: Int = 64,
    val contextSize: Int = 1024,
    val threads: Int = 4,
    val defaultModelId: Long? = null,
    val uiLanguage: String = "system",
    val promptTemplate: String = "auto"
)

sealed interface GenerationState {
    data object Idle : GenerationState
    data object LoadingModel : GenerationState
    data object Generating : GenerationState
    data class Error(val message: String) : GenerationState
    data object Canceled : GenerationState
}

interface ModelRepository {
    val models: Flow<List<ModelInfo>>
    suspend fun importModel(uriString: String): Result<ModelInfo>
    suspend fun deleteModel(id: Long): Result<Unit>
    suspend fun setActiveModel(id: Long): Result<Unit>
    suspend fun getActiveModel(): ModelInfo?
}

interface ChatRepository {
    val messages: Flow<List<ChatMessage>>
    suspend fun addMessage(role: String, content: String)
    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<InferenceSettings>
    suspend fun update(settings: InferenceSettings)
}
