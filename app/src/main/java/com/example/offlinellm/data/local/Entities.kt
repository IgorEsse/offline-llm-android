package com.example.offlinellm.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Index
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "models")
data class ModelEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val path: String,
    val sizeBytes: Long,
    val checksum: String,
    val architecture: String?,
    val metadata: String?,
    val importedAt: Long,
    val isActive: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val timestamp: Long
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"])])
data class ConversationEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessagePreview: String = "",
    val isActive: Boolean = false
)

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(model: ModelEntity): Long

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE models SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: Long)

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    suspend fun active(): ModelEntity?

    @Query("SELECT * FROM models WHERE filename = :filename AND sizeBytes = :size LIMIT 1")
    suspend fun findByFilenameSize(filename: String, size: Long): ModelEntity?

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ModelEntity?
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(msg: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clear(conversationId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id = :messageId")
    suspend fun deleteMessage(conversationId: Long, messageId: Long)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun listByConversation(conversationId: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: Long)

    @Query("SELECT * FROM conversations WHERE isActive = 1 LIMIT 1")
    suspend fun active(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET updatedAt = :updatedAt, lastMessagePreview = :preview WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long, preview: String)

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int
}

@Database(entities = [ModelEntity::class, MessageEntity::class, ConversationEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
}
