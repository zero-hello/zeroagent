package me.rerere.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** Process-only cache. Identical callers share work; the last cancelled waiter cancels its request. */
internal class SearchRequestPool<V>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxEntries: Int = 32,
    private val ttlMillis: Long = 60_000,
    maxConcurrent: Int = 4,
    private val timeoutMillis: Long = 25_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val cacheIf: (V) -> Boolean = { true },
) {
    private data class Cached<V>(val value: V, val expiresAt: Long)
    private class Flight<V>(val work: Deferred<V>, var waiters: Int = 0)
    private val lock = Any()
    private val cache = LinkedHashMap<String, Cached<V>>(16, .75f, true)
    private val pending = mutableMapOf<String, Flight<V>>()
    private val permits = Semaphore(maxConcurrent)

    suspend fun <R> uncached(load: suspend () -> R): R =
        withTimeout(timeoutMillis) { permits.withPermit { load() } }

    suspend fun get(key: String, load: suspend () -> V): V {
        currentCoroutineContext().ensureActive()
        val flight = synchronized(lock) {
            cache[key]?.let {
                if (it.expiresAt > now()) return it.value
                cache.remove(key)
            }
            val existing = pending[key]
            val selected = if (existing != null) existing else {
                require(pending.size < 64) { "Too many pending searches" }
                val work = scope.async(start = CoroutineStart.LAZY) {
                    val value = uncached(load)
                    val workContext = currentCoroutineContext()
                    workContext.ensureActive()
                    if (cacheIf(value)) synchronized(lock) {
                        workContext.ensureActive()
                        cache.entries.removeAll { it.value.expiresAt <= now() }
                        cache[key] = Cached(value, now() + ttlMillis)
                        while (cache.size > maxEntries) cache.remove(cache.keys.first())
                    }
                    value
                }
                val created = Flight(work)
                pending[key] = created
                work.invokeOnCompletion {
                    synchronized(lock) { if (pending[key] === created) pending.remove(key) }
                }
                created
            }
            selected.waiters++
            selected
        }
        flight.work.start()
        try {
            return flight.work.await()
        } finally {
            synchronized(lock) {
                flight.waiters--
                if (flight.waiters == 0 && !flight.work.isCompleted) {
                    if (pending[key] === flight) pending.remove(key)
                    flight.work.cancel()
                }
            }
        }
    }

    fun clear() = synchronized(lock) {
        cache.clear()
        val work = pending.values.map { it.work }
        pending.clear()
        work.forEach { it.cancel() }
    }
}
