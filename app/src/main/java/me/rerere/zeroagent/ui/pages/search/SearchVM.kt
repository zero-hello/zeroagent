package me.rerere.zeroagent.ui.pages.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import me.rerere.zeroagent.data.db.fts.MessageSearchResult
import me.rerere.zeroagent.data.db.fts.MessageSearchSort
import me.rerere.zeroagent.data.repository.ConversationRepository
import me.rerere.zeroagent.ui.hooks.readStringPreference
import me.rerere.zeroagent.ui.hooks.writeStringPreference
import kotlin.uuid.Uuid

private const val SORT_ORDER_PREF_KEY = "search_page_sort_order"

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
        viewModelScope.launch {
            isRebuilding = true
            rebuildProgress = 0 to 0
            try {
                conversationRepo.rebuildAllIndexes { current, total ->
                    rebuildProgress = current to total
                }
            } finally {
                isRebuilding = false
            }
        }
    }

    private suspend fun performSearch(request: SearchRequest) {
        results = emptyList()
        searchError = null
        isLoading = false
        if (request.query.isBlank() ||
            (request.scope == MessageSearchScope.CURRENT_ASSISTANT && request.assistantId == null)
        ) {
            return
        }
        isLoading = true
        searchError = null
        try {
            if (request.debounce) delay(300L)
            val filter = me.rerere.zeroagent.data.productivity.ConversationSearchFilter.parse(request.query)
            val store = me.rerere.zeroagent.data.productivity.ProjectStore.get(context)
            val matches = if (filter.text.isNotBlank()) {
                conversationRepo.searchMessages(filter.text, request.sort, request.assistantId)
            } else {
                conversationRepo.getAllConversations().first().asSequence()
                    .filter { request.assistantId == null || it.assistantId == request.assistantId }
                    .map { me.rerere.zeroagent.data.db.fts.MessageSearchResult("", "", it.id.toString(), it.title, it.updateAt, it.title) }
                    .sortedWith(if (request.sort == MessageSearchSort.OLDEST_FIRST) compareBy { it.updateAt } else compareByDescending { it.updateAt })
                    .take(200).toList()
            }
            val snapshot = settingsStore.settingsFlow.value
            val cache = mutableMapOf<String, me.rerere.zeroagent.data.model.Conversation?>()
            results = matches.filter { result ->
                if (filter.after != null && result.updateAt < filter.after) return@filter false
                if (filter.before != null && result.updateAt >= filter.before) return@filter false
                if (filter.tag != null && !store.tags(result.conversationId).any { it.equals(filter.tag, true) }) return@filter false
                if (filter.model == null && filter.type == null) return@filter true
                val conversation = cache.getOrPut(result.conversationId) {
                    conversationRepo.getConversationById(Uuid.parse(result.conversationId))
                } ?: return@filter false
                val messages = conversation.messageNodes.flatMap { it.messages }.filter {
                    result.messageId.isBlank() || it.id.toString() == result.messageId
                }
                messages.any { message ->
                    val model = snapshot.providers.flatMap { it.models }.firstOrNull { it.id == (message.modelId ?: conversation.chatModelId) }
                    val modelMatches = filter.model == null || model?.let {
                        it.modelId.contains(filter.model, true) || it.displayName.contains(filter.model, true)
                    } == true
                    modelMatches && (filter.type == null || message.parts.any { part ->
                        when (filter.type) {
                            "image" -> part is me.rerere.ai.ui.UIMessagePart.Image
                            "document" -> part is me.rerere.ai.ui.UIMessagePart.Document
                            "audio" -> part is me.rerere.ai.ui.UIMessagePart.Audio
                            "video" -> part is me.rerere.ai.ui.UIMessagePart.Video
                            "text" -> part is me.rerere.ai.ui.UIMessagePart.Text
                            else -> true
                        }
                    })
                }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            searchError = if (failure is IllegalArgumentException) "搜尋條件有誤：日期請用 YYYY-MM-DD，附件 type 請用 image、document、audio、video 或 text。"
                else "搜尋失敗，請稍後重試或重建搜尋索引。"
        } finally {
            isLoading = false
        }
    }
}
