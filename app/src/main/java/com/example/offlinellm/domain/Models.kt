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
    val conversationId: Long,
    val role: String,
    val content: String,
    val timestamp: Long
)

data class ConversationInfo(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessagePreview: String,
    val isActive: Boolean
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
    val conversations: Flow<List<ConversationInfo>>
    suspend fun createConversation(title: String = ""): ConversationInfo
    suspend fun ensureConversation(): ConversationInfo
    suspend fun setActiveConversation(id: Long)
    suspend fun renameConversation(id: Long, title: String)
    suspend fun deleteConversation(id: Long)
    suspend fun clearConversation(id: Long)
    suspend fun addMessage(role: String, content: String)
    suspend fun addMessage(conversationId: Long, role: String, content: String)
    suspend fun deleteMessage(messageId: Long)
    suspend fun exportConversationText(conversationId: Long): String
    suspend fun activeConversation(): ConversationInfo?
    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<InferenceSettings>
    suspend fun update(settings: InferenceSettings)
}
