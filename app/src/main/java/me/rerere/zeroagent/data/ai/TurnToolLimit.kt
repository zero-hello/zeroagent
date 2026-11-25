package me.rerere.zeroagent.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

internal fun toolsAlreadyStartedInTurn(messages: List<UIMessage>): Set<String> = messages.asReversed()
    .takeWhile { it.role != MessageRole.USER || it.isSynthetic }
    .flatMap(UIMessage::getTools)
    .filter { it.executionStartedAt != null }
    .mapTo(hashSetOf()) { it.toolCallId }

internal fun denyToolAboveTurnLimit(tool: UIMessagePart.Tool): UIMessagePart.Tool = tool.copy(
    output = listOf(UIMessagePart.Text("這次任務已達工具呼叫上限，這項操作未執行。")),
)