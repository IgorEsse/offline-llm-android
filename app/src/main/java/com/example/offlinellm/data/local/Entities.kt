package com.example.offlinellm.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
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
    val role: String,
    val content: String,
    val timestamp: Long
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
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(msg: MessageEntity)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Database(entities = [ModelEntity::class, MessageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun messageDao(): MessageDao
}
