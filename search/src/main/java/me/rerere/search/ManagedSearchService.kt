package me.rerere.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.security.MessageDigest
import java.util.Locale

internal object SearchRuntime {
    val pool = SearchRequestPool<Result<SearchResult>>(cacheIf = { it.isSuccess })
}

internal class ManagedSearchService<T : SearchServiceOptions>(
    private val delegate: SearchService<T>,
) : SearchService<T> by delegate {
    override suspend fun search(params: JsonObject, commonOptions: SearchCommonOptions, serviceOptions: T): Result<SearchResult> {
        return try {
            require(params.toString().length <= 32_768) { "Search parameters are too large" }
            val options = commonOptions.copy(resultSize = commonOptions.resultSize.coerceIn(1, MAX_SEARCH_RESULTS))
            val load: suspend () -> Result<SearchResult> = {
                delegate.search(params, options, serviceOptions).map { result ->
                    result.copy(
                        answer = result.answer?.take(16_000),
                        items = result.items.distinctBy { it.url }.take(options.resultSize).map {
                            it.copy(title = it.title.take(1_000), text = it.text.take(8_000),
                                highlights = it.highlights.take(5).map { text -> text.take(1_200) })
                        },
                        images = result.images.distinct().take(MAX_SEARCH_RESULTS),
                    )
                }
            }
            val result = if (serviceOptions is SearchServiceOptions.CustomJsOptions) {
                SearchRuntime.pool.uncached(load)
            } else {
                // Include credentials, endpoint, filters, size and locale, but retain only a digest as the key.
                val rawKey = SearchService.json.encodeToString<SearchServiceOptions>(serviceOptions) +
                    "\n" + params + "\n" + options.resultSize + "\n" + Locale.getDefault().toLanguageTag()
                val key = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                SearchRuntime.pool.get(key, load)
            }
            currentCoroutineContext().ensureActive()
            result
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Result.failure(java.io.IOException("Search timed out", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun scrape(params: JsonObject, commonOptions: SearchCommonOptions, serviceOptions: T): Result<ScrapedResult> =
        try {
            require(params.toString().length <= 32_768) { "Scrape parameters are too large" }
            params["urls"]?.let { require(it.jsonArray.size in 1..MAX_SEARCH_RESULTS) { "Scrape 1–20 URLs at a time" } }
            // Extraction stays fresh; never cache arbitrary authenticated pages or custom scripts.
            val result = SearchRuntime.pool.uncached {
                delegate.scrape(params, commonOptions.copy(resultSize = commonOptions.resultSize.coerceIn(1, MAX_SEARCH_RESULTS)), serviceOptions)
                    .map { value -> value.copy(urls = value.urls.take(MAX_SEARCH_RESULTS).map { it.copy(content = it.content.take(32_768)) }) }
            }
            currentCoroutineContext().ensureActive()
            result
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Result.failure(java.io.IOException("Scrape timed out", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
