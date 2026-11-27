package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

enum class ConversationAccessMode { ASK, ASSISTED, FULL }

internal data class ConversationAccessSnapshot(val mode: ConversationAccessMode, val revision: Long)

internal data class SavedConversationAccess(
    val defaultMode: ConversationAccessMode = ConversationAccessMode.ASK,
    val modes: Map<Uuid, ConversationAccessMode> = emptyMap(),
)

internal interface ConversationAccessStore {
    fun load(): SavedConversationAccess
    fun save(id: Uuid, mode: ConversationAccessMode): Boolean
    fun saveChatOnly(id: Uuid, mode: ConversationAccessMode): Boolean = false
}

/** Saved choices survive restarts; individual tool approvals remain transient. */
object ConversationAccess {
    private val lock = Any()
    private val revisions = mutableMapOf<Uuid, Long>()
    private val mutable = MutableStateFlow<Map<Uuid, ConversationAccessMode>>(emptyMap())
    @Volatile private var defaultMode = ConversationAccessMode.ASK
    private var store: ConversationAccessStore? = null
    val modes = mutable.asStateFlow()
    fun initialize(context: android.content.Context) = initialize(SharedPreferencesConversationAccessStore(context))

    internal fun initialize(storage: ConversationAccessStore) = synchronized(lock) {
        val saved = storage.load()
        store = storage
        defaultMode = saved.defaultMode
        revisions.clear()
        mutable.value = saved.modes
    }

    fun mode(id: Uuid): ConversationAccessMode = mutable.value[id] ?: defaultMode
    internal fun snapshot(id: Uuid): ConversationAccessSnapshot = synchronized(lock) {
        ConversationAccessSnapshot(mode(id), revisions[id] ?: 0)
    }

    fun set(id: Uuid, mode: ConversationAccessMode, highRiskConfirmed: Boolean = false): Boolean = synchronized(lock) {
        if (mode == ConversationAccessMode.FULL && !highRiskConfirmed) return false
        if (store?.save(id, mode) == false) return false
        defaultMode = mode
        if (mutable.value[id] == mode) return true
        ToolApprovalAllowList.clearChat(id)
        revisions[id] = (revisions[id] ?: 0) + 1
        mutable.update { it + (id to mode) }
        true
    }

    /** A direct chat instruction changes only this session and cannot downgrade an existing FULL grant. */
    internal fun delegateCurrentChat(id: Uuid): Boolean = synchronized(lock) {
        if (mode(id) == ConversationAccessMode.FULL) return true
        if (store?.saveChatOnly(id, ConversationAccessMode.ASSISTED) == false) return false
        if (mutable.value[id] != ConversationAccessMode.ASSISTED) {
            ToolApprovalAllowList.clearChat(id)
            revisions[id] = (revisions[id] ?: 0) + 1
            mutable.update { it + (id to ConversationAccessMode.ASSISTED) }
        }
        true
    }

    /** A delayed UI response cannot restore a grant revoked by a mode change. */
    internal fun grantIfUnchanged(id: Uuid, expected: ConversationAccessSnapshot, toolName: String) = synchronized(lock) {
        if (snapshot(id) == expected && expected.mode != ConversationAccessMode.ASSISTED) {
            ToolApprovalAllowList.grantForChat(id, toolName)
        }
    }
    fun requiresApproval(id: Uuid?, name: String): Boolean = id != null &&
        !HeadlessConversations.shouldAutoApprove(id) && name != ToolApprovalPolicy.ASK_USER_TOOL_NAME
}
