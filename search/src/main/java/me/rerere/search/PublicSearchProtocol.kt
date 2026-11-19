package me.rerere.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal enum class PublicSearchApi(val label: String, val endpoint: String, val console: String) {
    SERPAPI("SerpApi", "https://serpapi.com/search.json", "https://serpapi.com/manage-api-key"),
    SEARCHAPI("SearchApi", "https://www.searchapi.io/api/v1/search", "https://www.searchapi.io/dashboard"),
    YOU("You.com", "https://ydc-index.io/v1/search", "https://you.com/platform/api-keys"),
}

/** Protocols checked against each provider's public search documentation, October 2026. */
internal object PublicSearchProtocol {
    fun request(api: PublicSearchApi, query: String, size: Int, key: String): Request {
        require(query.isNotBlank()) { "query is required" }
        require(key.isNotBlank()) { "${api.label} API key is required" }
        return if (api == PublicSearchApi.YOU) {
            Request.Builder().url(api.endpoint).header("X-API-Key", key)
                .post(buildJsonObject { put("query", query); put("count", size.coerceIn(1, 20)) }
                    .toString().toRequestBody("application/json".toMediaType())).build()
        } else {
            val url = api.endpoint.toHttpUrl().newBuilder().addQueryParameter("engine", "google")
                .addQueryParameter("q", query)
            val request = Request.Builder()
            if (api == PublicSearchApi.SERPAPI) url.addQueryParameter("api_key", key)
            else request.header("Authorization", "Bearer $key")
            // Google's standard SERP no longer supports num; avoid extra pages and extra billable calls.
            request.url(url.build()).get().build()
        }
    }

    fun parse(api: PublicSearchApi, body: String, size: Int): SearchResult {
        val root = Json.parseToJsonElement(body) as? JsonObject ?: error("Invalid ${api.label} response")
        require(root["error"] == null || root["error"] is kotlinx.serialization.json.JsonNull) { "${api.label} rejected the search request" }
        fun JsonObject.text(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        val rows: List<Pair<JsonObject, Boolean>> = if (api == PublicSearchApi.YOU) {
            val results = root["results"] as? JsonObject ?: error("Missing You.com results")
            listOf("web", "news").flatMap { section ->
                (results[section] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { row -> row to (section == "news") } }
            }
        } else {
            val results = root["organic_results"] as? JsonArray
            require(results != null || root["search_information"] is JsonObject) { "Missing ${api.label} results" }
            results.orEmpty().mapNotNull { (it as? JsonObject)?.let { row -> row to false } }
        }
        val items = rows.asSequence().mapNotNull { (row, isNews) ->
            val url = row.text(if (api == PublicSearchApi.YOU) "url" else "link")
                ?.takeIf { it.toHttpUrlOrNull() != null } ?: return@mapNotNull null
            val snippets = (row["snippets"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.take(5)
            SearchResult.SearchResultItem(
                title = row.text("title").orEmpty(), url = url,
                text = (snippets.takeIf { it.isNotEmpty() }?.joinToString("\n")
                    ?: row.text(if (api == PublicSearchApi.YOU) "description" else "snippet")).orEmpty().take(8_000),
                publishedDate = if (isNews) row.text("page_age") else null,
                highlights = snippets,
            )
        }.distinctBy { it.url }.take(size.coerceIn(1, 20)).toList()
        return SearchResult(items = items)
    }
}
