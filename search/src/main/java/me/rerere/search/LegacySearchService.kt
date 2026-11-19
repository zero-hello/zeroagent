package me.rerere.search

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.InputSchema

/** Compatibility decoder only: this provider is absent from the selectable catalog. */
object LegacySearchService : SearchService<SearchServiceOptions.LegacySearchOptions> {
    override val name = "Retired search service"
    @Composable override fun Description() = Unit
    override fun parameters(options: SearchServiceOptions.LegacySearchOptions): InputSchema? = null
    override fun scrapingParameters(options: SearchServiceOptions.LegacySearchOptions): InputSchema? = null
    override suspend fun search(params: JsonObject, commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.LegacySearchOptions): Result<SearchResult> =
        Result.failure(IllegalStateException("Choose a supported search provider in Settings."))
    override suspend fun scrape(params: JsonObject, commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.LegacySearchOptions): Result<ScrapedResult> =
        Result.failure(IllegalStateException("Choose a supported search provider in Settings."))
}
