package me.rerere.search

import kotlinx.coroutines.CancellationException

internal const val MAX_SEARCH_RESULTS = 20
internal const val SEARCH_BODY_CAP = 1024 * 1024

internal inline fun <T> searchResultOf(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

