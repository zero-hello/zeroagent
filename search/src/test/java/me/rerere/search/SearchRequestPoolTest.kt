package me.rerere.search

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SearchRequestPoolTest {
    @Test fun uncachedScrapingCanShareTheLimiterWithoutUsingTheSearchCacheType() = runBlocking {
        val pool = SearchRequestPool<Int>(scope = this)
        assertEquals("scraped page", pool.uncached { "scraped page" })
        assertEquals(7, pool.get("search") { 7 })
    }

    @Test fun identicalRequestsShareWorkAndSuccessfulCache() = runBlocking {
        val pool = SearchRequestPool<Int>(scope = this)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loads = 0
        val load: suspend () -> Int = { loads++; started.complete(Unit); release.await(); 7 }
        val first = async(start = CoroutineStart.UNDISPATCHED) { pool.get("same", load) }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { pool.get("same", load) }
        release.complete(Unit)
        assertEquals(7, first.await())
        assertEquals(7, second.await())
        assertEquals(7, pool.get("same") { 9 })
        assertEquals(1, loads)
    }

    @Test fun cancellingOneWaiterKeepsTheOtherAlive() = runBlocking {
        val pool = SearchRequestPool<Int>(scope = this)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async(start = CoroutineStart.UNDISPATCHED) { pool.get("same") { started.complete(Unit); release.await(); 3 } }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { pool.get("same") { error("must share") } }
        first.cancelAndJoin()
        release.complete(Unit)
        assertEquals(3, second.await())
    }

    @Test fun lastCancelledWaiterStopsWorkAndDoesNotPoisonNextRequest() = runBlocking {
        val pool = SearchRequestPool<Int>(scope = this)
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val first = launch { pool.get("same") { try { started.complete(Unit); awaitCancellation() } finally { stopped.complete(Unit) } } }
        started.await(); first.cancelAndJoin(); stopped.await()
        assertEquals(5, pool.get("same") { 5 })
    }

    @Test fun ttlLruAndCredentialKeysStayIsolated() = runBlocking {
        var time = 0L
        val pool = SearchRequestPool<Int>(scope = this, maxEntries = 2, ttlMillis = 100, now = { time })
        assertEquals(1, pool.get("credential-a") { 1 })
        assertEquals(2, pool.get("credential-b") { 2 })
        assertEquals(1, pool.get("credential-a") { error("cached") })
        pool.get("third") { 3 }
        assertEquals(4, pool.get("credential-b") { 4 })
        time = 101
        assertEquals(5, pool.get("credential-a") { 5 })
    }

    @Test fun clearEvictsCachedValuesAndCancelsPendingRequests() = runBlocking {
        val pool = SearchRequestPool<Int>(scope = this)
        pool.get("cached") { 1 }
        val started = CompletableDeferred<Unit>()
        val pending = async {
            runCatching { pool.get("pending") { started.complete(Unit); awaitCancellation() } }
        }
        started.await()
        pool.clear()
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(2, pool.get("cached") { 2 })
        assertEquals(3, pool.get("pending") { 3 })
    }

    @Test fun failuresAreNotCached() = runBlocking {
        val pool = SearchRequestPool<Result<Int>>(scope = this, cacheIf = { it.isSuccess })
        assertTrue(pool.get("q") { Result.failure(Exception("fixture")) }.isFailure)
        assertEquals(8, pool.get("q") { Result.success(8) }.getOrThrow())
    }

    @Test fun concurrencyAndTimeoutAreBounded() = runBlocking {
        val pool = SearchRequestPool<Int>(scope = this, maxConcurrent = 1)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var secondStarted = false
        val first = async { pool.get("first") { started.complete(Unit); release.await(); 1 } }
        started.await()
        val next = async(start = CoroutineStart.UNDISPATCHED) { pool.get("next") { secondStarted = true; 2 } }
        yield()
        assertFalse(secondStarted)
        release.complete(Unit)
        assertEquals(1, first.await())
        assertEquals(2, next.await())
        val short = SearchRequestPool<Int>(scope = this, timeoutMillis = 30)
        assertTrue(runCatching { short.get("slow") { awaitCancellation() } }.exceptionOrNull() is TimeoutCancellationException)
    }
}
