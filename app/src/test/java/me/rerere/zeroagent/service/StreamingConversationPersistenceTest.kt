package me.rerere.zeroagent.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.zeroagent.data.model.Conversation
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class StreamingConversationPersistenceTest {
    private val id = Uuid.random()
    private var now = 0L
    private var current = Conversation.ofId(id, Uuid.random())
    private val saved = mutableListOf<Conversation>()

    private fun persistence(write: suspend (Uuid, Conversation, Boolean) -> Boolean = { _, value, _ ->
        saved += value
        true
    }) = StreamingConversationPersistence(
        snapshot = { current },
        writeSnapshot = write,
        nowMs = { now },
        onFailure = { _, _ -> },
    )

    @Test
    fun `stream writes are throttled but a lifecycle flush saves the final chunk`() = runBlocking {
        val persistence = persistence()
        persistence.markDirty(id)
        persistence.persistIfDue(id)
        current = current.copy(title = "latest")
        now = 499
        persistence.markDirty(id)
        persistence.persistIfDue(id)
        assertEquals(1, saved.size)
        persistence.flush(emptyList())
        assertEquals("latest", saved.last().title)
        assertFalse(persistence.isDirty(id))
    }

    @Test
    fun `failed writes keep their dirty marker and can be retried immediately`() = runBlocking {
        var attempts = 0
        val persistence = persistence { _, _, _ ->
            if (++attempts == 1) error("disk unavailable")
            true
        }
        persistence.markDirty(id)
        persistence.persistIfDue(id)
        assertTrue(persistence.isDirty(id))
        persistence.persistIfDue(id)
        assertEquals(2, attempts)
        assertFalse(persistence.isDirty(id))
    }

    @Test
    fun `rejected empty snapshots stay dirty for a later hydrated write`() = runBlocking {
        val persistence = persistence { _, _, _ -> false }
        persistence.markDirty(id)
        persistence.persistNow(id)
        assertTrue(persistence.isDirty(id))
    }

    @Test
    fun `chunk arriving during a write remains dirty after the older write completes`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val persistence = persistence { _, value, _ ->
            saved += value
            if (saved.size == 1) {
                entered.complete(Unit)
                release.await()
            }
            true
        }
        persistence.markDirty(id)
        val writing = launch { persistence.persistNow(id) }
        entered.await()
        current = current.copy(title = "new chunk")
        persistence.markDirty(id)
        release.complete(Unit)
        writing.join()
        assertTrue(persistence.isDirty(id))
        persistence.flush(emptyList())
        assertEquals("new chunk", saved.last().title)
        assertFalse(persistence.isDirty(id))
    }

    @Test
    fun `stream snapshot is read after an explicit save releases the same lock`() = runBlocking {
        val locked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val persistence = persistence()
        val explicitSave = launch {
            persistence.withLock(id) {
                locked.complete(Unit)
                release.await()
                current = current.copy(title = "approved and edited")
            }
        }
        locked.await()
        persistence.markDirty(id)
        val streaming = launch { persistence.persistNow(id) }
        release.complete(Unit)
        explicitSave.join()
        streaming.join()
        assertEquals("approved and edited", saved.single().title)
    }

    @Test
    fun `idle eviction preserves dirty work while an explicit drop discards it`() = runBlocking {
        val persistence = persistence()
        persistence.markDirty(id)
        persistence.removeIfClean(id)
        assertTrue(persistence.isDirty(id))
        persistence.flush(emptyList())
        assertEquals(1, saved.size)
        persistence.markDirty(id)
        persistence.drop(id)
        persistence.flush(emptyList())
        assertEquals(1, saved.size)
        assertFalse(persistence.isDirty(id))
    }

    @Test
    fun `conversations have independent throttles and clear discards pending work`() = runBlocking {
        val persistence = persistence()
        val other = Uuid.random()
        persistence.markDirty(id)
        persistence.persistIfDue(id)
        persistence.markDirty(other)
        persistence.persistIfDue(other)
        assertEquals(2, saved.size)
        now = 500
        persistence.markDirty(id)
        persistence.persistIfDue(id)
        assertEquals(3, saved.size)
        persistence.markDirty(id)
        persistence.clear()
        persistence.flush(listOf(id, other))
        assertEquals(3, saved.size)
    }
}
