package com.example.offlinellm.data.repo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.Room
import com.example.offlinellm.data.local.AppDatabase
import com.example.offlinellm.data.local.ConversationEntity
import com.example.offlinellm.data.local.MessageEntity
import com.example.offlinellm.data.local.ModelEntity
import com.example.offlinellm.data.local.SettingsStore
import com.example.offlinellm.domain.ChatMessage
import com.example.offlinellm.domain.ChatRepository
import com.example.offlinellm.domain.ConversationInfo
import com.example.offlinellm.domain.InferenceSettings
import com.example.offlinellm.domain.ModelInfo
import com.example.offlinellm.domain.ModelRepository
import com.example.offlinellm.domain.SettingsRepository
import com.example.offlinellm.nativebridge.LlamaNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest

class AppGraph(context: Context) {
    val db = Room.databaseBuilder(context, AppDatabase::class.java, "offline_llm.db")
        .fallbackToDestructiveMigration()
        .build()
    val settingsStore = SettingsStore(context)
    val modelRepository = DefaultModelRepository(context, db, settingsStore)
    val chatRepository = DefaultChatRepository(db)
    val settingsRepository = DefaultSettingsRepository(settingsStore)
}

class DefaultModelRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore
) : ModelRepository {
    override val models: Flow<List<ModelInfo>> = db.modelDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun importModel(uriString: String): Result<ModelInfo> = runCatching {
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            val (name, size) = queryNameAndSize(context.contentResolver, uri)
            require(name.endsWith(".gguf", ignoreCase = true)) { "Only .gguf files are supported" }
            val existing = db.modelDao().findByFilenameSize(name, size)
            require(existing == null) { "Duplicate model import" }

            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val dest = File(modelsDir, name)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open model input stream" }
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            val checksum = sha256(dest)
            val metadata = LlamaNativeBridge.getModelMetadata(dest.absolutePath)
            val entity = ModelEntity(
                filename = name,
                path = dest.absolutePath,
                sizeBytes = dest.length(),
                checksum = checksum,
                architecture = metadata.substringBefore(';').ifBlank { null },
                metadata = metadata,
                importedAt = System.currentTimeMillis()
            )
            val id = db.modelDao().insert(entity)
            entity.copy(id = id).toDomain()
        }
    }

    override suspend fun deleteModel(id: Long): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val model = db.modelDao().byId(id)
            val active = db.modelDao().active()
            if (active?.id == id) LlamaNativeBridge.unloadModel()
            model?.path?.let { File(it).delete() }
            db.modelDao().delete(id)
        }
    }

    override suspend fun setActiveModel(id: Long): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            db.modelDao().setActive(id)
            settingsStore.update(settingsStore.settings.map { it.copy(defaultModelId = id) }.first())
        }
    }

    override suspend fun getActiveModel(): ModelInfo? = db.modelDao().active()?.toDomain()

    private fun queryNameAndSize(cr: ContentResolver, uri: Uri): Pair<String, Long> {
        cr.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                return c.getString(nameIdx) to c.getLong(sizeIdx)
            }
        }
        error("Unable to read file metadata")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class DefaultChatRepository(private val db: AppDatabase) : ChatRepository {
    override val conversations: Flow<List<ConversationInfo>> =
        db.conversationDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    override val messages: Flow<List<ChatMessage>> =
        db.conversationDao().observeAll().flatMapLatest { conversations ->
            val activeId = conversations.firstOrNull { it.isActive }?.id ?: conversations.firstOrNull()?.id ?: -1L
            db.messageDao().observeByConversation(activeId)
        }.map { rows ->
            rows.map { ChatMessage(it.id, it.conversationId, it.role, it.content, it.timestamp) }
        }

    override suspend fun createConversation(title: String): ConversationInfo {
        val now = System.currentTimeMillis()
        val cleanTitle = title.ifBlank { "New chat" }
        val id = db.conversationDao().insert(
            ConversationEntity(
                title = cleanTitle,
                createdAt = now,
                updatedAt = now,
                lastMessagePreview = "",
                isActive = false
            )
        )
        db.conversationDao().setActive(id)
        return requireNotNull(db.conversationDao().byId(id)).toDomain()
    }

    override suspend fun ensureConversation(): ConversationInfo {
        db.conversationDao().active()?.let { return it.toDomain() }
        if (db.conversationDao().count() == 0) {
            return createConversation("")
        }
        val first = db.conversationDao().observeAll().first().first()
        db.conversationDao().setActive(first.id)
        return requireNotNull(db.conversationDao().byId(first.id)).toDomain()
    }

    override suspend fun setActiveConversation(id: Long) {
        db.conversationDao().setActive(id)
    }

    override suspend fun renameConversation(id: Long, title: String) {
        db.conversationDao().rename(id, title.ifBlank { "New chat" }, System.currentTimeMillis())
    }

    override suspend fun deleteConversation(id: Long) {
        db.messageDao().deleteByConversation(id)
        db.conversationDao().delete(id)
        val all = db.conversationDao().observeAll().first()
        if (all.isEmpty()) {
            createConversation("")
        } else if (all.none { it.isActive }) {
            db.conversationDao().setActive(all.first().id)
        }
    }

    override suspend fun clearConversation(id: Long) {
        db.messageDao().clear(id)
        db.conversationDao().touch(id, System.currentTimeMillis(), "")
    }

    override suspend fun addMessage(role: String, content: String) {
        val conv = ensureConversation()
        addMessage(conv.id, role, content)
    }

    override suspend fun addMessage(conversationId: Long, role: String, content: String) {
        db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                timestamp = System.currentTimeMillis()
            )
        )
        db.conversationDao().touch(conversationId, System.currentTimeMillis(), content.take(120))
    }

    override suspend fun deleteMessage(messageId: Long) {
        val conv = activeConversation() ?: return
        db.messageDao().deleteMessage(conv.id, messageId)
    }

    override suspend fun exportConversationText(conversationId: Long): String {
        return db.messageDao().listByConversation(conversationId)
            .joinToString("\n") { "${it.role.replaceFirstChar(Char::uppercaseChar)}: ${it.content}" }
    }

    override suspend fun activeConversation(): ConversationInfo? {
        return db.conversationDao().active()?.toDomain()
    }

    override suspend fun clear() {
        val active = activeConversation() ?: return
        clearConversation(active.id)
    }
}

class DefaultSettingsRepository(private val store: SettingsStore) : SettingsRepository {
    override val settings: Flow<InferenceSettings> = store.settings

    override suspend fun update(settings: InferenceSettings) {
        store.update(settings)
    }
}

private fun ModelEntity.toDomain() = ModelInfo(id, filename, path, sizeBytes, checksum, architecture, metadata, importedAt, isActive)
private fun ConversationEntity.toDomain() = ConversationInfo(id, title, createdAt, updatedAt, lastMessagePreview, isActive)
