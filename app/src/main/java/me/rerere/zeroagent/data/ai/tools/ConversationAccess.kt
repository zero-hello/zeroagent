package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

enum class ConversationAccessMode { ASK, ASSISTED, FULL }

/** Explicit grants last only for this app process and never leak into exports or other chats. */
object ConversationAccess {
    private val mutable = MutableStateFlow<Map<Uuid, ConversationAccessMode>>(emptyMap())
    val modes = mutable.asStateFlow()
    fun mode(id: Uuid): ConversationAccessMode = mutable.value[id] ?: ConversationAccessMode.ASK
    fun set(id: Uuid, mode: ConversationAccessMode, highRiskConfirmed: Boolean = false): Boolean {
        if (mode == ConversationAccessMode.FULL && !highRiskConfirmed) return false
        if (mode == ConversationAccessMode.ASK) ToolApprovalAllowList.clearChat(id)
        mutable.update { it + (id to mode) }
        return true
    }
    fun requiresApproval(id: Uuid?, name: String): Boolean = id != null &&
        !HeadlessConversations.shouldAutoApprove(id) && name != ToolApprovalPolicy.ASK_USER_TOOL_NAME
}
