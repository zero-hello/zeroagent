package me.rerere.zeroagent.ui.pages.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.getCurrentAssistant
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.db.fts.MESSAGE_SEARCH_PAGE_SIZE
import me.rerere.zeroagent.data.db.fts.MessageSearchResult
import me.rerere.zeroagent.data.db.fts.MessageSearchSort
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.data.productivity.ConversationSearchFilter
import me.rerere.zeroagent.data.productivity.ProjectStore
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.ui.components.ui.ChatPrivacy
import me.rerere.zeroagent.ui.hooks.readStringPreference
import me.rerere.zeroagent.ui.hooks.writeStringPreference
import java.time.DateTimeException
import kotlin.uuid.Uuid

private const val SORT_ORDER_PREF_KEY = "search_page_sort_order"
private const val FILTER_ONLY_RESULT_LIMIT = 200

enum class MessageSearchScope {
    CURRENT_ASSISTANT,
    ALL_ASSISTANTS,
}

private data class SearchRequest(
    val query: String,
    val sort: MessageSearchSort,
    val scope: MessageSearchScope,
    val assistantId: Uuid?,
    val debounce: Boolean,
)

class SearchVM(
    private val context: Application,
    private val conversationRepo: ConversationRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val searchRequests = Channel<SearchRequest>(Channel.CONFLATED)
    private var currentAssistantId: Uuid? = null

    var searchQuery by mutableStateOf("")
        private set
    var searchScope by mutableStateOf(MessageSearchScope.ALL_ASSISTANTS)
        private set
    var sortOrder by mutableStateOf(
        runCatching {
            MessageSearchSort.valueOf(
                context.readStringPreference(SORT_ORDER_PREF_KEY, MessageSearchSort.RELEVANCE.name)!!
            )
        }.getOrDefault(MessageSearchSort.RELEVANCE)
    )
        private set
    var results by mutableStateOf<List<MessageSearchResult>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var rebuildError by mutableStateOf<String?>(null)
        private set
    var isRebuilding by mutableStateOf(false)
        private set
    var rebuildProgress by mutableStateOf(0 to 0)
        private set

    init {
        viewModelScope.launch {
            searchRequests.receiveAsFlow().collectLatest { request -> performSearch(request) }
        }
        viewModelScope.launch {
            settingsStore.settingsFlow
                .map { it.getCurrentAssistant().id }
                .distinctUntilChanged()
                .collect { assistantId ->
                    currentAssistantId = assistantId
                    if (searchScope == MessageSearchScope.CURRENT_ASSISTANT) {
                        search()
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        rebuildError = null
        searchQuery = query
        requestSearch(debounce = true)
    }

    fun onScopeChange(scope: MessageSearchScope) {
        if (searchScope == scope) return
        searchScope = scope
        search()
    }

    fun onSortChange(sort: MessageSearchSort) {
        if (sortOrder == sort) return
        sortOrder = sort
        context.writeStringPreference(SORT_ORDER_PREF_KEY, sort.name)
        search()
    }

    fun search() {
        requestSearch()
    }

    private fun requestSearch(debounce: Boolean = false) {
        val assistantId = when (searchScope) {
            MessageSearchScope.CURRENT_ASSISTANT -> currentAssistantId
            MessageSearchScope.ALL_ASSISTANTS -> null
        }
        searchRequests.trySend(
            SearchRequest(
                query = searchQuery,
                sort = sortOrder,
                scope = searchScope,
                assistantId = assistantId,
                debounce = debounce,
            )
        )
    }

    fun rebuildIndex() {
        if (isRebuilding) return
        isRebuilding = true
        rebuildError = null
        rebuildProgress = 0 to 0
        viewModelScope.launch {
            try {
                conversationRepo.rebuildAllIndexes { current, total ->
                    // Room runs the rebuild on its transaction dispatcher.
                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                        rebuildProgress = current to total
                    }
                }
                search()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                rebuildError = "無法重建搜尋索引，請稍後再試。"
            } finally {
                isRebuilding = false
            }
        }
    }

    private suspend fun performSearch(request: SearchRequest) {
        results = emptyList()
        searchError = null
        if (request.query.isBlank() ||
            (request.scope == MessageSearchScope.CURRENT_ASSISTANT && request.assistantId == null)
        ) {
            return
        }
        isLoading = true
        try {
            if (request.debounce) delay(300L)
            val filter = ConversationSearchFilter.parse(request.query)
            val store = ProjectStore.get(context)
            val projectConversationIds = filter.project?.let { name ->
                store.projects.value.asSequence().filter { it.name.equals(name, ignoreCase = true) }
                    .flatMap { it.conversationIds.asSequence() }.toSet()
            }
            val models = settingsStore.settingsFlow.value.providers.flatMap { it.models }.associateBy { it.id }
            val cache = object : LinkedHashMap<String, Conversation?>(16, .75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Conversation?>?): Boolean = size > 16
            }
            val conversationMatches = mutableMapOf<String, Boolean>()

            suspend fun matches(result: MessageSearchResult): Boolean {
                if (projectConversationIds != null && result.conversationId !in projectConversationIds) return false
                if (!conversationMatches.getOrPut(result.conversationId) {
                    !ChatPrivacy.chatLocked(context, Uuid.parse(result.conversationId)) &&
                        (filter.tag == null || store.tags(result.conversationId).any { it.equals(filter.tag, true) })
                }) return false
                if (filter.after != null && result.updateAt < filter.after) return false
                if (filter.before != null && result.updateAt >= filter.before) return false
                if (filter.model == null && filter.type == null) return true
                val conversation = cache.getOrPut(result.conversationId) {
                    conversationRepo.getConversationById(Uuid.parse(result.conversationId))
                } ?: return false
                return conversation.messageNodes.asSequence().flatMap { it.messages.asSequence() }.any { message ->
                    if (result.messageId.isNotBlank() && message.id.toString() != result.messageId) return@any false
                    val model = models[message.modelId ?: conversation.chatModelId]
                    val modelMatches = filter.model == null || model?.let {
                        it.modelId.contains(filter.model, true) || it.displayName.contains(filter.model, true)
                    } == true
                    modelMatches && (filter.type == null || message.parts.any { part ->
                        when (filter.type) {
                            "image" -> part is UIMessagePart.Image
                            "document" -> part is UIMessagePart.Document
                            "audio" -> part is UIMessagePart.Audio
                            "video" -> part is UIMessagePart.Video
                            "text" -> part is UIMessagePart.Text
                            else -> false
                        }
                    })
                }
            }

            val titles = conversationRepo.getAllConversations().first().asSequence()
                .filter { request.assistantId == null || it.assistantId == request.assistantId }
                .filter { it.title.contains(filter.text, ignoreCase = true) }
                .map { MessageSearchResult("", "", it.id.toString(), it.title, it.updateAt, it.title) }
                .sortedWith(compareSearchDates(request.sort))
            val matchesByTitle = mutableListOf<MessageSearchResult>()
            val matchedMessages = if (filter.text.isBlank()) emptyList() else collectMatchingMessages(
                limit = MESSAGE_SEARCH_PAGE_SIZE,
                pageSize = MESSAGE_SEARCH_PAGE_SIZE,
                load = { offset -> conversationRepo.searchMessages(filter.text, request.sort, request.assistantId, offset) },
                matches = ::matches,
            )
            val conversationsWithMatchedMessages = matchedMessages.mapTo(hashSetOf()) { it.conversationId }
            val limit = if (filter.text.isBlank()) FILTER_ONLY_RESULT_LIMIT else MESSAGE_SEARCH_PAGE_SIZE
            for (title in titles) {
                if (title.conversationId !in conversationsWithMatchedMessages && matches(title)) matchesByTitle += title
                if (matchesByTitle.size >= limit) break
            }
            val combined = matchesByTitle + matchedMessages
            results = when (request.sort) {
                MessageSearchSort.RELEVANCE -> combined
                else -> combined.sortedWith(compareSearchDates(request.sort))
            }.take(limit)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            searchError = if (failure is IllegalArgumentException || failure is DateTimeException) {
                "搜尋條件有誤。日期請用 YYYY-MM-DD，附件類型可用 image、document、audio、video 或 text。"
            } else {
                "搜尋失敗，請稍後重試或重建搜尋索引。"
            }
        } finally {
            isLoading = false
        }
    }
}

private fun compareSearchDates(sort: MessageSearchSort): Comparator<MessageSearchResult> =
    if (sort == MessageSearchSort.OLDEST_FIRST) compareBy { it.updateAt } else compareByDescending { it.updateAt }

/** Apply filters before the result cap, keeping the database order. */
internal suspend fun <T> collectMatchingMessages(
    limit: Int,
    pageSize: Int,
    load: suspend (offset: Int) -> List<T>,
    matches: suspend (T) -> Boolean,
): List<T> {
    val results = mutableListOf<T>()
    var offset = 0
    while (results.size < limit) {
        val page = load(offset)
        for (entry in page) {
            if (matches(entry)) results += entry
            if (results.size == limit) return results
        }
        if (page.size < pageSize) break
        offset += pageSize
    }
    return results
}
