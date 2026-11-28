package me.rerere.zeroagent.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.rerere.zeroagent.data.db.entity.MemoryEntity

@Dao
interface MemoryDAO {
    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId")
    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun getMemoriesOfAssistant(assistantId: String): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memoryentity")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memoryentity WHERE id = :id")
    suspend fun getMemoryById(id: Int): MemoryEntity?

    @Query("""
        SELECT EXISTS(SELECT 1 FROM memoryentity WHERE assistant_id = :assistantId
            AND TRIM(content) = :content COLLATE NOCASE AND (expires_at IS NULL OR expires_at > :now))
    """)
    suspend fun hasActiveMemory(assistantId: String, content: String, now: Long): Boolean

    @Transaction
    suspend fun insertCapturedMemory(assistantId: String, content: String, now: Long): Boolean {
        if (hasActiveMemory(assistantId, content, now)) return false
        insertMemory(MemoryEntity(assistantId = assistantId, content = content))
        return true
    }

    @Insert
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Query("UPDATE memoryentity SET expires_at = :expiresAt WHERE id = :id")
    suspend fun setExpiry(id: Int, expiresAt: Long?): Int

    @Query("DELETE FROM memoryentity WHERE expires_at IS NOT NULL AND expires_at <= :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("DELETE FROM memoryentity WHERE id = :id")
    suspend fun deleteMemory(id: Int)

    @Query("DELETE FROM memoryentity WHERE assistant_id = :assistantId")
    suspend fun deleteMemoriesOfAssistant(assistantId: String)
}
