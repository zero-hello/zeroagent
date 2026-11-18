package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema

internal class PublicSearchService<T : SearchServiceOptions>(
    private val api: PublicSearchApi,
    private val apiKey: (T) -> String,
) : SearchService<T> {
    override val name = api.label

    @Composable override fun Description() {
        val handler = LocalUriHandler.current
        TextButton(onClick = { handler.openUri(api.console) }) { Text(stringResource(R.string.click_to_get_api_key)) }
    }

    override fun parameters(options: T) = InputSchema.Obj(
        properties = buildJsonObject {
            put("query", buildJsonObject { put("type", "string"); put("description", "Focused web search query") })
        }, required = listOf("query"),
    )
    override fun scrapingParameters(options: T): InputSchema? = null

    override suspend fun search(params: JsonObject, commonOptions: SearchCommonOptions, serviceOptions: T): Result<SearchResult> =
        withContext(Dispatchers.IO) {
            searchResultOf {
                val key = SearchService.keyRoulette.next(apiKey(serviceOptions), serviceOptions.id.toString())
                val request = PublicSearchProtocol.request(api, params["query"]?.jsonPrimitive?.content.orEmpty(), commonOptions.resultSize, key)
                SearchService.httpClient.newCall(request).await().use { response ->
                    require(response.isSuccessful) { "${api.label} search failed: HTTP ${response.code}" }
                    PublicSearchProtocol.parse(api, response.body.string(), commonOptions.resultSize)
                }
            }
        }

    override suspend fun scrape(params: JsonObject, commonOptions: SearchCommonOptions, serviceOptions: T): Result<ScrapedResult> =
        Result.failure(UnsupportedOperationException("${api.label} does not provide scraping"))
}
