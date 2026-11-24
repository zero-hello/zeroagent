package me.rerere.zeroagent.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.rerere.ai.ui.UIMessage
import me.rerere.common.android.Logging
import me.rerere.zeroagent.data.chat.ChatDraftStore
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.db.ChatPayloadStore
import me.rerere.zeroagent.data.db.fts.MessageFtsManager
import me.rerere.zeroagent.data.db.fts.MessageSearchSort
import me.rerere.zeroagent.data.db.dao.ConversationDAO
import me.rerere.zeroagent.data.db.dao.ConversationCompactionDAO
import me.rerere.zeroagent.data.db.dao.FavoriteDAO
import me.rerere.zeroagent.data.db.dao.MessageNodeDAO
import me.rerere.zeroagent.data.db.entity.ConversationEntity
import me.rerere.zeroagent.data.db.entity.ConversationCompactionEntity
import me.rerere.zeroagent.data.db.entity.MessageNodeEntity
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.model.ConversationCompaction
import me.rerere.zeroagent.data.model.MessageNode
import me.rerere.zeroagent.utils.JsonInstant
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationRepository(
    private val conversationDAO: ConversationDAO,
    private val conversationCompactionDAO: ConversationCompactionDAO,
    private val messageNodeDAO: MessageNodeDAO,
    private val favoriteDAO: FavoriteDAO,
    private val database: AppDatabase,
    private val filesManager: FilesManager,
    private val messageFtsManager: MessageFtsManager,
    context: Context,
) {
    private val drafts = ChatDraftStore(context)
    private val payloads = ChatPayloadStore(context, database)

    companion object {
        private const val PAGE_SIZE = 20
        private const val INITIAL_LOAD_SIZE = 40
    }

    fun getAllConversations(): Flow<List<Conversation>> = conversationDAO.getAll().map { entries -> entries.map { conversationEntityToConversation(it, emptyList()) } }

    fun getUnfiledConversationsPaging(): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { conversationDAO.getUnfiledPaging() },
    ).flow.map { data -> data.map { conversationSummaryToConversation(it) } }

    suspend fun updateChatModel(id: Uuid, modelId: Uuid) = conversationDAO.updateChatModel(id.toString(), encodeChatModelId(modelId))

    suspend fun hasFileReference(fileUrl: String): Boolean =
        messageNodeDAO.hasFileReference(JsonInstant.encodeToString(fileUrl))

    suspend fun getRecentConversations(assistantId: Uuid, limit: Int = 10): List<Conversation> {
        return conversationDAO.getRecentConversationsOfAssistant(
            assistantId = assistantId.toString(),
            limit = limit
        ).map { entity ->
            val nodes = loadMessageNodes(entity.id)
            conversationEntityToConversation(entity, nodes)
        }
    }

    fun getConversationsOfAssistant(assistantId: Uuid): Flow<List<Conversation>> {
        return conversationDAO
            .getConversationsOfAssistant(assistantId.toString())
            .map { flow ->
                flow.map { entity ->
                    // 列表视图不需要完整的 nodes，使用空列表
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    fun getConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.getConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    fun getUnfiledConversationsOfAssistantPaging(assistantId: Uuid): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString()) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    /** How many conversations are filed in [folderId] — drives the folder delete guard. */
    fun countInFolderFlow(folderId: Uuid): Flow<Int> =
        conversationDAO.countInFolderFlow(folderId.toString())

    fun getConversationsOfFolderPaging(folderId: Uuid): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.getConversationsOfFolderPaging(folderId.toString()) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    suspend fun getConversationsOfAssistantPage(
        assistantId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        val pagingSource = conversationDAO.getConversationsOfAssistantPaging(assistantId.toString())
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        conversationSummaryToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    suspend fun searchConversationsOfAssistantPage(
        assistantId: Uuid,
        titleKeyword: String,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        val pagingSource = conversationDAO.searchConversationsOfAssistantPaging(
            assistantId = assistantId.toString(),
            searchText = titleKeyword
        )
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        conversationSummaryToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    suspend fun getUnfiledConversationsOfAssistantPage(
        assistantId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getUnfiledConversationsOfAssistantPaging(assistantId.toString()),
        offset,
        limit,
    )

    suspend fun getConversationsOfFolderPage(
        folderId: Uuid,
        offset: Int,
        limit: Int,
    ): ConversationPageResult = loadConversationPage(
        conversationDAO.getConversationsOfFolderPaging(folderId.toString()),
        offset,
        limit,
    )

    private suspend fun loadConversationPage(
        pagingSource: PagingSource<Int, LightConversationEntity>,
        offset: Int,
        limit: Int,
    ): ConversationPageResult {
        return try {
            when (
                val result = pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = if (offset == 0) null else offset,
                        loadSize = limit,
                        placeholdersEnabled = false
                    )
                )
            ) {
                is PagingSource.LoadResult.Page -> ConversationPageResult(
                    items = result.data.map { entity ->
                        conversationSummaryToConversation(entity)
                    },
                    nextOffset = result.nextKey
                )

                is PagingSource.LoadResult.Error -> throw result.throwable
                is PagingSource.LoadResult.Invalid -> ConversationPageResult(emptyList(), null)
            }
        } finally {
            pagingSource.invalidate()
        }
    }

    fun searchConversations(titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversations(titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    fun searchConversationsPaging(titleKeyword: String): Flow<PagingData<Conversation>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { conversationDAO.searchConversationsPaging(titleKeyword) }
    ).flow.map { pagingData ->
        pagingData.map { entity ->
            conversationSummaryToConversation(entity)
        }
    }

    fun searchConversationsOfAssistant(assistantId: Uuid, titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversationsOfAssistant(assistantId.toString(), titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    fun searchConversationsOfAssistantPaging(assistantId: Uuid, titleKeyword: String): Flow<PagingData<Conversation>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                conversationDAO.searchConversationsOfAssistantPaging(
                    assistantId.toString(),
                    titleKeyword
                )
            }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                conversationSummaryToConversation(entity)
            }
        }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        val entity = conversationDAO.getConversationById(uuid.toString())
        return if (entity != null) {
            val nodes = loadMessageNodes(entity.id)
            conversationEntityToConversation(entity, nodes)
        } else null
    }

    suspend fun existsConversationById(uuid: Uuid): Boolean {
        return conversationDAO.existsById(uuid.toString())
    }

    internal suspend fun hasMessageNodes(uuid: Uuid): Boolean = messageNodeDAO.hasNodes(uuid.toString())

    suspend fun countConversations(): Int {
        return conversationDAO.countAll()
    }

    suspend fun getCompaction(conversationId: Uuid): ConversationCompaction? =
        conversationCompactionDAO.getByConversationId(conversationId.toString())?.toDomainCompaction()

    fun observeCompaction(conversationId: Uuid): Flow<ConversationCompaction?> =
        conversationCompactionDAO.observeByConversationId(conversationId.toString())
            .map { it?.toDomainCompaction() }

    private fun ConversationCompactionEntity.toDomainCompaction() = ConversationCompaction(
        conversationId = Uuid.parse(conversationId),
        summary = summary,
        tailStartNodeId = tailStartNodeId?.let(Uuid::parse),
        sourceEndNodeId = Uuid.parse(sourceEndNodeId),
        summaryModelId = Uuid.parse(summaryModelId),
        isAuto = isAuto,
        sourceTokenEstimate = sourceTokenEstimate,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

    suspend fun upsertCompaction(compaction: ConversationCompaction) {
        conversationCompactionDAO.upsert(
            ConversationCompactionEntity(
                conversationId = compaction.conversationId.toString(),
                summary = compaction.summary,
                tailStartNodeId = compaction.tailStartNodeId?.toString(),
                sourceEndNodeId = compaction.sourceEndNodeId.toString(),
                summaryModelId = compaction.summaryModelId.toString(),
                isAuto = compaction.isAuto,
                sourceTokenEstimate = compaction.sourceTokenEstimate,
                createdAt = compaction.createdAt.toEpochMilli(),
            )
        )
    }

    suspend fun clearCompaction(conversationId: Uuid) {
        conversationCompactionDAO.deleteByConversationId(conversationId.toString())
    }

    suspend fun countConversationsByAssistant(): Map<Uuid, Int> {
        return conversationDAO.countByAssistant().mapNotNull { row ->
            runCatching { Uuid.parse(row.assistantId) }.getOrNull()?.let { it to row.count }
        }.toMap()
    }

    suspend fun insertConversation(
        conversation: Conversation,
        updateSearchIndex: Boolean = true,
    ) {
        database.withTransaction {
            conversationDAO.insert(
                conversationToConversationEntity(conversation)
            )
            saveMessageNodes(conversation.id.toString(), conversation.messageNodes)
        }
        if (updateSearchIndex) {
            messageFtsManager.indexConversation(conversation)
        }
    }

    suspend fun updateConversation(
        conversation: Conversation,
        updateSearchIndex: Boolean = true,
    ) {
        val conversationId = conversation.id.toString()
        val obsolete = database.withTransaction {
            val previous = messageNodeDAO.getPayloadReferences(conversationId)
            conversationDAO.update(conversationToConversationEntity(conversation))
            messageNodeDAO.deleteByConversation(conversationId)
            saveMessageNodes(conversationId, conversation.messageNodes)
            if (previous.isEmpty()) emptySet()
            else previous.toSet() - messageNodeDAO.getPayloadReferences(conversationId).toSet()
        }
        if (updateSearchIndex) {
            messageFtsManager.indexConversation(conversation)
            payloads.pruneUnreferenced(obsolete)
        }
    }

    internal fun indexTransferredConversation(conversation: Conversation) {
        messageFtsManager.indexOnCurrentConnection(conversation)
    }

    suspend fun deleteConversation(conversation: Conversation) {
        // 获取完整的 Conversation（包含 messageNodes）以正确清理文件
        val fullConversation = if (conversation.messageNodes.isEmpty()) {
            getConversationById(conversation.id) ?: conversation
        } else {
            conversation
        }
        val obsolete = database.withTransaction {
            val previous = messageNodeDAO.getPayloadReferences(conversation.id.toString())
            // message_node 会通过 CASCADE 自动删除
            conversationDAO.delete(
                conversationToConversationEntity(conversation)
            )
            previous
        }
        payloads.pruneUnreferenced(obsolete)
        // message_fts is a derived index over the same rows: only touch it after the
        // authoritative delete has committed, so a failed/rolled-back transaction can't
        // leave the conversation still present but unsearchable (matches insert/update,
        // which already index only after their transaction completes).
        drafts.remove(conversation.id)
        drafts.clearUnsent(conversation.id)
        messageFtsManager.deleteConversation(conversation.id.toString())
        filesManager.deleteChatFiles(fullConversation.files)
    }

    suspend fun searchMessages(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
        assistantId: Uuid? = null,
        offset: Int = 0,
    ) = messageFtsManager.search(keyword, sort, assistantId?.toString(), offset)

    suspend fun rebuildAllIndexes(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }) = database.withTransaction {
        messageFtsManager.deleteAllOnCurrentConnection()
        val allIds = conversationDAO.getAllIds()
        val total = allIds.size
        allIds.forEachIndexed { index, id ->
            val entity = conversationDAO.getConversationById(id) ?: return@forEachIndexed
            val nodes = loadMessageNodes(entity.id)
            val conversation = conversationEntityToConversation(entity, nodes)
            messageFtsManager.indexOnCurrentConnection(conversation)
            onProgress(index + 1, total)
        }
    }

    /**
     * Repair the FTS5 search index when SQLite reports a malformed inverted index. Drops
     * the message_fts virtual table (frees the corrupted index pages, DELETE alone won't),
     * recreates it via the shared schema, then re-indexes every conversation. Returns the
     * number of conversations re-indexed so the Doctor can report progress.
     */
    suspend fun repairAndRebuildIndexes(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): Int {
        messageFtsManager.dropAndRecreate()
        val allIds = conversationDAO.getAllIds()
        val total = allIds.size
        allIds.forEachIndexed { index, id ->
            val entity = conversationDAO.getConversationById(id) ?: return@forEachIndexed
            val nodes = loadMessageNodes(entity.id)
            val conversation = conversationEntityToConversation(entity, nodes)
            messageFtsManager.indexConversation(conversation)
            onProgress(index + 1, total)
        }
        return total
    }

    suspend fun deleteConversationOfAssistant(assistantId: Uuid) {
        getConversationsOfAssistant(assistantId).first().forEach { conversation ->
            deleteConversation(conversation)
        }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        require(conversation.messageNodes.none { it.messages.any { message -> message.hasBase64Part() } })
        return ConversationEntity(
            id = conversation.id.toString(),
            title = conversation.title,
            nodes = "[]",  // nodes 现在存储在单独的表中
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            assistantId = conversation.assistantId.toString(),
            chatSuggestions = JsonInstant.encodeToString(conversation.chatSuggestions),
            isPinned = conversation.isPinned,
            customSystemPrompt = conversation.customSystemPrompt ?: "",
            modeInjectionIds = JsonInstant.encodeToString(conversation.modeInjectionIds),
            lorebookIds = JsonInstant.encodeToString(conversation.lorebookIds),
            workspaceCwd = conversation.workspaceCwd ?: "",
            folderId = conversation.folderId?.toString() ?: "",
            chatModelId = encodeChatModelId(conversation.chatModelId),
        )
    }

    fun conversationEntityToConversation(
        conversationEntity: ConversationEntity,
        messageNodes: List<MessageNode>
    ): Conversation {
        return Conversation(
            id = Uuid.parse(conversationEntity.id),
            title = conversationEntity.title,
            messageNodes = messageNodes.filter { it.messages.isNotEmpty() },
            createAt = Instant.ofEpochMilli(conversationEntity.createAt),
            updateAt = Instant.ofEpochMilli(conversationEntity.updateAt),
            assistantId = Uuid.parse(conversationEntity.assistantId),
            chatSuggestions = JsonInstant.decodeFromString(conversationEntity.chatSuggestions),
            isPinned = conversationEntity.isPinned,
            customSystemPrompt = conversationEntity.customSystemPrompt.ifEmpty { null },
            modeInjectionIds = JsonInstant.decodeFromString(conversationEntity.modeInjectionIds),
            lorebookIds = JsonInstant.decodeFromString(conversationEntity.lorebookIds),
            workspaceCwd = conversationEntity.workspaceCwd.ifEmpty { null },
            folderId = conversationEntity.folderId.ifEmpty { null }?.let { Uuid.parse(it) },
            chatModelId = decodeChatModelId(conversationEntity.chatModelId),
        )
    }

    fun getPinnedConversations(): Flow<List<Conversation>> {
        return conversationDAO
            .getPinnedConversations()
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity, emptyList())
                }
            }
    }

    suspend fun togglePinStatus(conversationId: Uuid) {
        // Single atomic UPDATE: avoids the read->write TOCTOU that existed when
        // we read isPinned with getConversationById() and then flipped it.
        conversationDAO.togglePinStatus(conversationId.toString())
    }

    /**
     * 单列更新会话的文件夹归属，folderId 为 null 表示移出文件夹（未归类）。
     */
    suspend fun updateConversationFolderId(conversationId: Uuid, folderId: Uuid?) {
        conversationDAO.updateFolderId(
            id = conversationId.toString(),
            folderId = folderId?.toString() ?: ""
        )
    }

    /**
     * 单列更新会话标题。刻意不走 [updateConversation]：抽屉列表项来自
     * [LightConversationEntity] 投影，messageNodes 为空，若传入 updateConversation
     * 会把消息节点全部删除重建，等于清空聊天记录。
     */
    suspend fun renameConversationIfUnchanged(id: Uuid, oldTitle: String, title: String) {
        conversationDAO.updateTitleIfUnchanged(id.toString(), oldTitle, title)
    }

    suspend fun renameConversation(conversationId: Uuid, title: String) {
        conversationDAO.updateTitle(conversationId.toString(), title)
        messageFtsManager.updateConversationTitle(conversationId.toString(), title)
    }

    private fun conversationSummaryToConversation(entity: LightConversationEntity): Conversation {
        return Conversation(
            id = Uuid.parse(entity.id),
            assistantId = Uuid.parse(entity.assistantId),
            title = entity.title,
            isPinned = entity.isPinned,
            createAt = Instant.ofEpochMilli(entity.createAt),
            updateAt = Instant.ofEpochMilli(entity.updateAt),
            messageNodes = emptyList(),
            folderId = entity.folderId.ifEmpty { null }?.let { Uuid.parse(it) },
        )
    }

    private suspend fun loadMessageNodes(conversationId: String): List<MessageNode> {
        val favoriteNodeIds = favoriteDAO
            .getFavoriteNodeIdsOfConversation(conversationId)
            .mapNotNull { runCatching { Uuid.parse(it) }.getOrNull() }
            .toSet()

        return database.withTransaction {
            val nodes = mutableListOf<MessageNode>()
            var offset = 0
            val pageSize = 64
            while (true) {
                val page = messageNodeDAO.getBoundedNodes(conversationId, pageSize, offset)
                for (node in page) {
                    val messages = JsonInstant.decodeFromString<List<UIMessage>>(
                        payloads.load(node)
                    )
                    val nodeId = Uuid.parse(node.id)
                    nodes.add(
                        MessageNode(
                            id = nodeId,
                            messages = messages,
                            selectIndex = node.selectIndex,
                            isFavorite = nodeId in favoriteNodeIds,
                        )
                    )
                }
                if (page.size < pageSize) break
                offset += page.size
            }
            nodes
        }
    }

    private suspend fun saveMessageNodes(conversationId: String, nodes: List<MessageNode>) {
        nodes.forEachIndexed { index, node ->
            val entity = MessageNodeEntity(
                id = node.id.toString(),
                conversationId = conversationId,
                nodeIndex = index,
                messages = JsonInstant.encodeToString(node.messages),
                selectIndex = node.selectIndex
            )
            messageNodeDAO.insert(payloads.encode(entity))
        }
    }
}

private const val CONVERSATION_REPOSITORY_TAG = "ConversationRepository"

/**
 * Encodes [Conversation.chatModelId] for the `chat_model_id` column. `null` (no override)
 * stores as the empty string, matching the `folder_id` convention.
 */
internal fun encodeChatModelId(chatModelId: Uuid?): String = chatModelId?.toString() ?: ""

/**
 * Decodes the `chat_model_id` column back to [Conversation.chatModelId]. A blank column means
 * "no override". A malformed stored value must not throw - a corrupt row would otherwise make
 * the conversation unopenable - so it decodes to `null` with a warning naming the bad value.
 */
internal fun decodeChatModelId(stored: String): Uuid? {
    if (stored.isEmpty()) return null
    return try {
        Uuid.parse(stored)
    } catch (e: IllegalArgumentException) {
        // android.util.Log is unmocked in JVM unit tests (throws instead of logging), so this
        // testable top-level function uses the Logging facade instead - see the identical note
        // in GeminiProvider.kt's resolveStreamFailureCause.
        Logging.log(CONVERSATION_REPOSITORY_TAG, "Malformed chat_model_id \"$stored\" in conversation row; treating as unset: ${e.message}")
        null
    }
}

/**
 * 轻量级的会话查询结果，不包含 nodes 和 suggestions 字段
 */
data class LightConversationEntity(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val folderId: String = "",
)

data class ConversationPageResult(
    val items: List<Conversation>,
    val nextOffset: Int?,
)
