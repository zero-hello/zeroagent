package me.rerere.zeroagent.service

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.chat.ChatDraftStore
import me.rerere.zeroagent.data.chat.UnsentChatMessage
import kotlin.uuid.Uuid

/** Owns queue admission and dispatch for UI, voice, and scheduled turns. */
internal class ChatOutgoingQueue(
    private val context: Context,
    private val outgoingDrafts: ChatDraftStore,
    private val findSession: (Uuid) -> ConversationSession?,
    private val openSession: (Uuid) -> ConversationSession,
    private val sendQueuedMessage: (ConversationSession, QueuedMessage) -> Job,
    private val cleanupQueuedAttachments: (QueuedMessage) -> Unit,
    private val addError: (Throwable, Uuid) -> Unit,
) {
    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        openSession(conversationId).messageQueue.state

    fun removeQueuedMessage(conversationId: Uuid, messageId: Uuid) {
        val session = findSession(conversationId) ?: return
        synchronized(session) {
            val queue = session.messageQueue
            val pending = queue.state.value.messages.firstOrNull { it.id == messageId } ?: return
            if (pending.reply == null && !outgoingDrafts.removeUnsent(conversationId, messageId, durable = true)) {
                queue.pause()
                addError(IllegalStateException("無法移除待送訊息，請確認裝置儲存空間後再試。"), conversationId)
                return
            }
            queue.remove(messageId)?.let(cleanupQueuedAttachments)
        }
        dispatchNextQueuedMessage(conversationId)
    }

    fun beginEditQueuedMessage(conversationId: Uuid, messageId: Uuid): QueuedMessage? {
        val session = findSession(conversationId) ?: return null
        return synchronized(session) { session.messageQueue.beginEdit(messageId) }
    }

    fun finishEditQueuedMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>? = null
    ) {
        val session = findSession(conversationId) ?: return
        synchronized(session) {
            val queue = session.messageQueue
            val before = queue.state.value.messages.firstOrNull { it.id == messageId && it.isEditing } ?: return
            if (parts != null && parts.isEmptyInputMessage()) return
            if (before.reply == null && parts != null && !recordUnsent(conversationId, before.copy(parts = parts), durable = true)) {
                queue.pause()
                queue.finishEdit(messageId)
                addError(IllegalStateException("無法保存修改，已保留原本的待送訊息。"), conversationId)
                return
            }
            queue.finishEdit(messageId, parts)?.let(cleanupQueuedAttachments)
        }
        dispatchNextQueuedMessage(conversationId)
    }

    fun resumeMessageQueue(conversationId: Uuid) {
        findSession(conversationId)?.messageQueue?.resume()
        dispatchNextQueuedMessage(conversationId)
    }

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        val session = openSession(conversationId)
        synchronized(session) {
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            val message = QueuedMessage(parts = content.toList(), answer = answer)
            if (!recordUnsent(conversationId, message, durable = true)) {
                session.messageQueue.recoverFailedSend(message)
                addError(IllegalStateException("尚未送出，且裝置無法保存這則訊息。請先保留文字，再重新開啟應用程式。"), conversationId)
                return
            }
            session.messageQueue.enqueue(message.parts, answer, messageId = message.id)
            dispatchNextQueuedMessage(conversationId)
        }
    }

    /** Enqueue immediately; the result belongs to this item even after edits or later turns. */
    fun enqueueVoiceMessage(conversationId: Uuid, text: String): Deferred<String?> {
        val session = openSession(conversationId)
        val reply = CompletableDeferred<String?>()
        synchronized(session) {
            check(text.isNotBlank()) { context.getString(R.string.chat_page_voice_empty) }
            check(!session.messageQueue.state.value.paused || session.messageQueue.state.value.messages.isEmpty()) {
                context.getString(R.string.chat_page_voice_resume_queue)
            }
            check(session.state.value.currentMessages.none { message ->
                message.parts.any { it is UIMessagePart.Tool && it.isPending }
            }) { context.getString(R.string.chat_page_voice_tools_before_resume) }
            if (session.messageQueue.state.value.messages.isEmpty()) session.messageQueue.resume()
            session.messageQueue.enqueue(listOf(UIMessagePart.Text(text)), reply = reply)
            dispatchNextQueuedMessage(conversationId)
        }
        return reply
    }

    fun dispatchNextQueuedMessage(conversationId: Uuid): Job? {
        val session = findSession(conversationId) ?: return null
        synchronized(session) {
            // A pending tool approval is still part of the current turn.
            if (session.getJob() != null || session.state.value.currentMessages.any { message ->
                    message.parts.any { it is UIMessagePart.Tool && it.isPending }
                }) return null
            val next = session.messageQueue.takeNext() ?: return null
            session.submittingMessage = next
            return sendQueuedMessage(session, next)
        }
    }

    fun recordUnsent(conversationId: Uuid, queued: QueuedMessage, durable: Boolean = false): Boolean =
        outgoingDrafts.writeUnsent(
            conversationId,
            UnsentChatMessage(queued.id.toString(), queued.parts, queued.answer),
            durable = durable,
        )

}
