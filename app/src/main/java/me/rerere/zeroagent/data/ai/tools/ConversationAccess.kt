package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

enum class ConversationAccessMode { ASK, ASSISTED, FULL }

internal data class ConversationAccessSnapshot(val mode: ConversationAccessMode, val revision: Long)

/** Explicit grants last only for this app process and never leak into exports or other chats. */
object ConversationAccess {
    private val lock = Any()
    private val revisions = mutableMapOf<Uuid, Long>()
    private val mutable = MutableStateFlow<Map<Uuid, ConversationAccessMode>>(emptyMap())
    val modes = mutable.asStateFlow()
    fun mode(id: Uuid): ConversationAccessMode = mutable.value[id] ?: ConversationAccessMode.ASK
    internal fun snapshot(id: Uuid): ConversationAccessSnapshot = synchronized(lock) {
        ConversationAccessSnapshot(mode(id), revisions[id] ?: 0)
    }

    fun set(id: Uuid, mode: ConversationAccessMode, highRiskConfirmed: Boolean = false): Boolean = synchronized(lock) {
        if (mode == ConversationAccessMode.FULL && !highRiskConfirmed) return false
        if (mode(id) == mode) return true
        ToolApprovalAllowList.clearChat(id)
        revisions[id] = (revisions[id] ?: 0) + 1
        mutable.update { it + (id to mode) }
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
