package me.rerere.zeroagent.data.ai

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.db.dao.ConversationDAO
import me.rerere.zeroagent.data.db.dao.MessageNodeDAO
import me.rerere.zeroagent.data.db.ChatPayloadStore
import me.rerere.zeroagent.data.db.toEntity
import me.rerere.zeroagent.utils.JsonInstant

private const val TAG = "OrphanedReasoning"

/**
 * Closes the reasoning parts a dead process left open, once per process start.
 *
 * Why it matters: the chat card renders `now - createdAt` for as long as `finishedAt` is null, so
 * an open part *is* a running "thinking" timer. A turn that was cut off mid-flight - the process
 * reclaimed in the background, an install replacing the app, a crash - persists its partial message
 * with the reasoning part still open, and nothing ever closes it afterwards. Reopening the app then
 * shows "thinking for 3600 seconds" on a turn that died hours ago, and the number keeps climbing;
 * users read that as "the agent is still stuck".
 *
 * The close time is the conversation's own `updateAt`: every streamed chunk is persisted as it
 * arrives, so the last write is exactly where the stream stopped, which is the honest end of the
 * part. It is clamped forward to the part's own start so a settled duration can never be negative.
 *
 * Best-effort throughout - a slow or failing sweep must never hold up app start - and candidates
 * come from a cheap `instr()` scan, so the cost is proportional to the number of conversations that
 * actually need repairing rather than to the size of the database.
 */
class OrphanedReasoningRecovery(
    context: Context,
    private val database: AppDatabase,
    private val conversationDAO: ConversationDAO,
    private val messageNodeDAO: MessageNodeDAO,
) {
    private val payloads = ChatPayloadStore(context, database)

    /**
     * Repair every node holding an open part. Returns the number of nodes rewritten (useful for
     * tests and for the boot log).
     */
    suspend fun runRecovery(): Int {
        var repaired = 0
        val candidateIds = messageNodeDAO.getOpenFinishedAtNodeIds()
        for (id in candidateIds) {
            try {
                if (database.withTransaction { repairNode(id) }) repaired++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the original row intact if it cannot be decoded or updated.
                Log.w(TAG, "Could not close an orphaned reasoning part", e)
            }
        }
        return repaired
    }

    private suspend fun repairNode(id: String): Boolean {
        val node = messageNodeDAO.getBoundedNode(id) ?: return false
        val messages = JsonInstant.decodeFromString<List<UIMessage>>(payloads.load(node))
        val updateAt = conversationDAO.getConversationUpdateAt(node.conversationId) ?: return false
        val closedAt = Instant.fromEpochMilliseconds(updateAt)
        var changed = false
        val repairedMessages = messages.map { message ->
            message.copy(parts = message.parts.map { part ->
                if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                    changed = true
                    part.copy(finishedAt = maxOf(part.createdAt, closedAt))
                } else {
                    part
                }
            })
        }
        if (changed) {
            messageNodeDAO.update(
                payloads.encode(node.toEntity(JsonInstant.encodeToString(repairedMessages)))
            )
        }
        return changed
    }
}
