package me.rerere.zeroagent.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import me.rerere.zeroagent.data.db.entity.MessageNodeEntity

@Dao
interface MessageNodeDAO {
    @Query("SELECT EXISTS(SELECT 1 FROM message_node WHERE conversation_id = :conversationId)")
    suspend fun hasNodes(conversationId: String): Boolean
    // 使用与 messages 相同的 JSON 编码，保守保留所有分支中出现的 URL。
    @Query("SELECT EXISTS(SELECT 1 FROM message_node WHERE instr(messages, :encodedFileUrl) > 0)")
    suspend fun hasFileReference(encodedFileUrl: String): Boolean

    @Query("SELECT * FROM message_node WHERE conversation_id = :conversationId ORDER BY node_index ASC")
    suspend fun getNodesOfConversation(conversationId: String): List<MessageNodeEntity>

    // Bound CursorWindow rows. Oversized legacy data is read separately in byte chunks.
    @Query(
        "SELECT $BOUNDED_NODE_COLUMNS FROM message_node WHERE conversation_id = :conversationId " +
            "ORDER BY node_index ASC, id ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun getBoundedNodes(conversationId: String, limit: Int, offset: Int): List<BoundedMessageNode>

    @Query("SELECT id FROM message_node WHERE instr(messages, '\"finishedAt\":null') > 0")
    suspend fun getOpenFinishedAtNodeIds(): List<String>

    @Query("SELECT $BOUNDED_NODE_COLUMNS FROM message_node WHERE id = :id")
    suspend fun getBoundedNode(id: String): BoundedMessageNode?

    @Query("SELECT substr(CAST(messages AS BLOB), :offset, :length) FROM message_node WHERE id = :id")
    suspend fun readPayloadBytes(id: String, offset: Long, length: Int): ByteArray?

    @Query("SELECT DISTINCT payload_sha256 FROM message_node WHERE conversation_id = :conversationId AND payload_sha256 IS NOT NULL")
    suspend fun getPayloadReferences(conversationId: String): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM message_node WHERE payload_sha256 = :digest)")
    suspend fun hasPayloadReference(digest: String): Boolean

    @Query(
        "SELECT id FROM message_node WHERE id > :after AND payload_sha256 IS NULL AND " +
            "length(CAST(messages AS BLOB)) > :threshold ORDER BY id LIMIT 16"
    )
    suspend fun getLargeInlineNodeIds(after: String, threshold: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<MessageNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: MessageNodeEntity)

    @Update
    suspend fun update(node: MessageNodeEntity)

    @Query("DELETE FROM message_node WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("DELETE FROM message_node WHERE id = :nodeId")
    suspend fun deleteById(nodeId: String)

    // 使用 @RawQuery 绕过 Room 编译期校验，以便使用 json_each() 虚拟表
    @RawQuery
    suspend fun getTokenStatsRaw(query: SupportSQLiteQuery): MessageTokenStats

    @RawQuery
    suspend fun getMessageCountPerDayRaw(query: SupportSQLiteQuery): List<MessageDayCount>
}

private const val MESSAGE_READ_BYTES = 128 * 1024
private const val MESSAGE_BYTES_EXPRESSION = "length(CAST(messages AS BLOB))"
private const val BOUNDED_NODE_COLUMNS =
    "id, conversation_id AS conversationId, node_index AS nodeIndex, select_index AS selectIndex, " +
        "payload_sha256 AS payloadSha256, " +
        "$MESSAGE_BYTES_EXPRESSION AS payloadBytes, " +
        "CASE WHEN $MESSAGE_BYTES_EXPRESSION <= $MESSAGE_READ_BYTES THEN messages END AS inlineMessages"

data class BoundedMessageNode(
    val id: String,
    val conversationId: String,
    val nodeIndex: Int,
    val selectIndex: Int,
    val payloadBytes: Int,
    val inlineMessages: String?,
    val payloadSha256: String?,
)

// Call inside a Room transaction so all chunks describe the same row version.
internal suspend fun MessageNodeDAO.readMessagePayload(node: BoundedMessageNode): String =
    node.inlineMessages ?: assembleMessagePayload(node.payloadBytes) { offset, length ->
        readPayloadBytes(node.id, offset, length)
    }

internal inline fun assembleMessagePayload(
    totalBytes: Int,
    read: (offset: Long, length: Int) -> ByteArray?,
): String {
    val bytes = ByteArray(totalBytes)
    var copied = 0
    while (copied < totalBytes) {
        val length = minOf(MESSAGE_READ_BYTES, totalBytes - copied)
        val chunk = read(copied.toLong() + 1, length)
        check(chunk != null && chunk.size == length) { "Incomplete stored message payload" }
        chunk.copyInto(bytes, copied)
        copied += length
    }
    return bytes.decodeToString(throwOnInvalidSequence = true)
}

data class MessageTokenStats(
    val totalMessages: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val cachedTokens: Long = 0,
)

data class MessageDayCount(val day: String, val count: Int)

// 在 json_each() 的参数内校验 JSON，避免损坏行导致整个统计查询失败。
// 使用 CASE 而非依赖 WHERE 条件的求值顺序，无效 JSON 按空数组处理。
private const val VALID_MESSAGES_JSON = "CASE WHEN json_valid(mn.messages) THEN mn.messages ELSE '[]' END"

// SQLite json_each() 展开 messages JSON 数组，json_extract() 提取 Token 字段并聚合
private val TOKEN_STATS_SQL = SimpleSQLiteQuery(
    "SELECT COUNT(*) AS totalMessages, " +
        "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.promptTokens') AS INTEGER)), 0) AS promptTokens, " +
        "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.completionTokens') AS INTEGER)), 0) AS completionTokens, " +
        "COALESCE(SUM(CAST(json_extract(j.value, '$.usage.cachedTokens') AS INTEGER)), 0) AS cachedTokens " +
        "FROM message_node mn, json_each($VALID_MESSAGES_JSON) j"
)

suspend fun MessageNodeDAO.getTokenStats(): MessageTokenStats = getTokenStatsRaw(TOKEN_STATS_SQL)

// 按用户消息的 createdAt 字段（LocalDateTime ISO 字符串前10位即日期）统计每日消息数
suspend fun MessageNodeDAO.getMessageCountPerDay(startDate: String): List<MessageDayCount> =
    getMessageCountPerDayRaw(
        SimpleSQLiteQuery(
            "SELECT substr(json_extract(j.value, '$.createdAt'), 1, 10) AS day, " +
                "COUNT(*) AS count " +
                "FROM message_node mn, json_each($VALID_MESSAGES_JSON) j " +
                "WHERE json_extract(j.value, '$.role') = 'user' " +
                "AND json_extract(j.value, '$.createdAt') >= ? " +
                "GROUP BY day",
            arrayOf(startDate)
        )
    )
