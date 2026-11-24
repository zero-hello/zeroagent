package me.rerere.zeroagent.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.zeroagent.data.db.entity.ConversationCompactionEntity

@Dao
interface ConversationCompactionDAO {
    @Query("SELECT * FROM conversation_compaction WHERE conversation_id = :conversationId")
    suspend fun getByConversationId(conversationId: String): ConversationCompactionEntity?

    @Query("SELECT * FROM conversation_compaction WHERE conversation_id = :conversationId")
    fun observeByConversationId(conversationId: String): Flow<ConversationCompactionEntity?>

    /** Doctor support: how many conversations have ever been compacted. */
    @Query("SELECT COUNT(*) FROM conversation_compaction")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(compaction: ConversationCompactionEntity)

    @Query("DELETE FROM conversation_compaction WHERE conversation_id = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)
}
