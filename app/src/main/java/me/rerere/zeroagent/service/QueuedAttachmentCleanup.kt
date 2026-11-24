package me.rerere.zeroagent.service

import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.zeroagent.data.files.FilesManager
import me.rerere.zeroagent.data.model.localFileUrls
import me.rerere.zeroagent.data.repository.ConversationRepository

internal class QueuedAttachmentCleanup(
    private val appScope: CoroutineScope,
    private val conversationRepo: ConversationRepository,
    private val filesManager: FilesManager,
    private val readSessions: () -> List<ConversationSession>,
) {
    fun schedule(previous: QueuedMessage) {
        val candidates = previous.parts.localFileUrls()
        if (candidates.isEmpty()) return
        appScope.launch {
            try {
                // 未打开的会话及未选中的分支也可能引用同一附件。
                val persistedReferences =
                    candidates.filter { conversationRepo.hasFileReference(it) }.toSet()
                // 数据库查询挂起期间队列可能已推进，删除前重新读取内存引用。
                val currentSessions = readSessions()
                val unusedFiles = unreferencedQueuedAttachmentUrls(
                    previous = previous,
                    conversations = currentSessions.map { it.state.value },
                    pendingMessages = currentSessions.flatMap {
                        it.messageQueue.state.value.messages + listOfNotNull(it.submittingMessage)
                    },
                ) - persistedReferences
                if (unusedFiles.isNotEmpty()) {
                    filesManager.deleteChatFiles(unusedFiles.map { it.toUri() })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 无法确认引用时保留文件，避免误删。
                Log.w("QueuedAttachmentCleanup", "Failed to clean queued attachments", e)
            }
        }
    }
}
