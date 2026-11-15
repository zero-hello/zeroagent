package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.extract.ExtractMode
import me.rerere.search.extract.ScrapeSchema
import me.rerere.search.extract.WebExtractor
import me.rerere.search.net.hostIsBlockedLiteral
import me.rerere.search.net.withEgressGuard
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Locale

object BingSearchService : SearchService<SearchServiceOptions.BingLocalOptions> {
    override val name: String = "Bing"

    @Composable
    override fun Description() {
        Text(stringResource(R.string.bing_desc))
    }

    override fun parameters(options: SearchServiceOptions.BingLocalOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.BingLocalOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", ScrapeSchema.URL_DESCRIPTION)
                })
                put("mode", buildJsonObject {
                    put("type", "string")
                    put("description", ScrapeSchema.MODE_DESCRIPTION)
                })
            },
            required = listOf("url"),
        )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BingLocalOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        searchResultOf {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8")
            val locale = Locale.getDefault()
            val acceptLanguage = "${locale.language}-${locale.country},${locale.language}"
            val request = Request.Builder().url(url)
                .header("User-Agent", ScrapeSchema.DEFAULT_USER_AGENT)
                .header("Accept-Language", acceptLanguage).get().build()
            val doc = httpClient.newCall(request).await().use { response ->
                require(response.isSuccessful) { "Bing search failed: HTTP ${response.code}" }
                Jsoup.parse(response.body.string(), url)
            }

            // 解析搜索结果
            val results = doc.select("li.b_algo").take(commonOptions.resultSize.coerceIn(1, MAX_SEARCH_RESULTS)).map { element ->
                val title = element.select("h2").text()
                val link = element.select("h2 > a").attr("href")
                val snippet = element.select(".b_caption p").text()
                SearchResultItem(
                    title = title,
                    url = link,
                    text = snippet
                )
            }

            require(results.isNotEmpty()) {
                "Search failed: no results found"
            }

            SearchResult(items = results)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BingLocalOptions
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        searchResultOf {
            val url = params["url"]?.jsonPrimitive?.contentOrNull?.trim()
            require(!url.isNullOrBlank()) { "url is required" }
            require(url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                "url must be an absolute http(s) URL"
            }
            require(!hostIsBlockedLiteral(url.toHttpUrlOrNull()?.host)) {
                "blocked_private_address: $url"
            }
            val mode = when (params["mode"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                "text" -> ExtractMode.TEXT
                "links" -> ExtractMode.LINKS
                "metadata" -> ExtractMode.METADATA
                else -> ExtractMode.ARTICLE
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ScrapeSchema.DEFAULT_USER_AGENT)
                .get()
                .build()

            val guarded = httpClient.withEgressGuard()
            guarded.newCall(request).await().use { resp ->
                require(resp.isSuccessful) { "HTTP ${resp.code} from $url" }
                val html = boundedBody(resp, SCRAPE_BODY_CAP)
                val page = WebExtractor.extract(
                    html = html,
                    baseUrl = url,
                    mode = mode,
                    maxChars = 32 * 1024,
                    startIndex = 0,
                )
                require(page.text.isNotBlank() || mode != ExtractMode.ARTICLE) {
                    "no readable content extracted from $url"
                }
                ScrapedResult(
                    urls = listOf(
                        ScrapedResultUrl(
                            url = url,
                            content = page.text,
                            metadata = ScrapedResultMetadata(
                                title = page.title,
                                description = page.description,
                                language = page.language,
                            ),
                        ),
                    ),
                )
            }
        }
    }
}
