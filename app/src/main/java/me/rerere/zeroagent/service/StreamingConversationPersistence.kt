package me.rerere.zeroagent.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.zeroagent.data.model.Conversation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.Uuid

/** One owner for stream dirty markers, throttling and the lock also used by explicit saves. */
internal class StreamingConversationPersistence(
    private val snapshot: (Uuid) -> Conversation,
    private val writeSnapshot: suspend (Uuid, Conversation, Boolean) -> Boolean,
    private val nowMs: () -> Long,
    private val onFailure: (Uuid, Throwable) -> Unit,
) {
    private val mutexes = ConcurrentHashMap<Uuid, Mutex>()
    private val sequence = AtomicLong(0L)
    private val pending = ConcurrentHashMap<Uuid, Long>()
    private val lastPersistAt = ConcurrentHashMap<Uuid, Long>()

    suspend fun <T> withLock(id: Uuid, block: suspend () -> T): T =
        mutexes.getOrPut(id) { Mutex() }.withLock { block() }

    fun markDirty(id: Uuid) {
        pending[id] = sequence.incrementAndGet()
    }

    fun isDirty(id: Uuid): Boolean = pending.containsKey(id)

    suspend fun persistIfDue(id: Uuid) {
        val now = nowMs()
        val last = lastPersistAt[id]
        if (last == null || now - last >= 500L) persistNow(id)
    }

    suspend fun persistNow(id: Uuid, updateSearchIndex: Boolean = false) {
        val observedMarker = pending[id] ?: return
        val persisted = runCatching {
            // Read after acquiring the same lock as explicit saves, so user edits/approvals
            // cannot be overwritten by a snapshot captured before the lock was available.
            withLock(id) { writeSnapshot(id, snapshot(id), updateSearchIndex) }
        }.onFailure { onFailure(id, it) }.getOrDefault(false)
        if (persisted) {
            lastPersistAt[id] = nowMs()
            // A newer chunk can arrive while the Room transaction is suspended.
            pending.remove(id, observedMarker)
        }
    }

    suspend fun flush(generatingIds: Collection<Uuid>) {
        (pending.keys.toSet() + generatingIds).forEach { persistNow(it) }
    }

    fun removeIfClean(id: Uuid) {
        if (!isDirty(id)) {
            mutexes.remove(id)
            lastPersistAt.remove(id)
        }
    }

    fun drop(id: Uuid) {
        pending.remove(id)
        lastPersistAt.remove(id)
        mutexes.remove(id)
    }

    fun clear() {
        mutexes.clear()
        pending.clear()
        lastPersistAt.clear()
    }
}
