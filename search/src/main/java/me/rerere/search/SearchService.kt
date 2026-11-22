package me.rerere.search

import android.content.Context
import androidx.compose.runtime.Composable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

interface SearchService<T : SearchServiceOptions> {
    val name: String

    fun parameters(options: T): InputSchema?

    fun scrapingParameters(options: T): InputSchema?

    @Composable
    fun Description()

    suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: T
    ): Result<SearchResult>

    suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: T
    ): Result<ScrapedResult>

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : SearchServiceOptions> getService(options: T): SearchService<T> {
            val service = when (options) {
                is SearchServiceOptions.TavilyOptions -> TavilySearchService
                is SearchServiceOptions.ExaOptions -> ExaSearchService
                is SearchServiceOptions.ZhipuOptions -> ZhipuSearchService
                is SearchServiceOptions.DoubaoOptions -> DoubaoSearchService
                is SearchServiceOptions.BingLocalOptions -> BingSearchService
                is SearchServiceOptions.DuckDuckGoOptions -> DuckDuckGoSearchService
                is SearchServiceOptions.SearXNGOptions -> SearXNGService
                is SearchServiceOptions.LinkUpOptions -> LinkUpService
                is SearchServiceOptions.BraveOptions -> BraveSearchService
                is SearchServiceOptions.MetasoOptions -> MetasoSearchService
                is SearchServiceOptions.OllamaOptions -> OllamaSearchService
                is SearchServiceOptions.PerplexityOptions -> PerplexitySearchService
                is SearchServiceOptions.FirecrawlOptions -> FirecrawlSearchService
                is SearchServiceOptions.JinaOptions -> JinaSearchService
                is SearchServiceOptions.BochaOptions -> BochaSearchService
                is SearchServiceOptions.LegacySearchOptions -> LegacySearchService
                is SearchServiceOptions.GrokOptions -> GrokSearchService
                is SearchServiceOptions.TinyfishOptions -> TinyfishSearchService
                is SearchServiceOptions.SerperOptions -> SerperSearchService
                is SearchServiceOptions.SerpApiOptions -> PublicSearchService<SearchServiceOptions.SerpApiOptions>(PublicSearchApi.SERPAPI) { it.apiKey }
                is SearchServiceOptions.SearchApiOptions -> PublicSearchService<SearchServiceOptions.SearchApiOptions>(PublicSearchApi.SEARCHAPI) { it.apiKey }
                is SearchServiceOptions.YouOptions -> PublicSearchService<SearchServiceOptions.YouOptions>(PublicSearchApi.YOU) { it.apiKey }
                is SearchServiceOptions.CustomJsOptions -> CustomJsSearchService
            } as SearchService<T>
            return ManagedSearchService(service)
        }

        @Volatile
        internal var httpClient: OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

        @Volatile
        internal var keyRoulette: KeyRoulette = KeyRoulette.default()

        fun init(client: OkHttpClient, context: Context? = null) {
            SearchRuntime.pool.clear()
            httpClient = client.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()
            keyRoulette = if (context != null) KeyRoulette.lru(context) else KeyRoulette.default()
        }

        internal val json by lazy {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
    }
}

@Serializable
sealed class SearchServiceOptions {
    abstract val id: Uuid

    open val displayName: String
        get() = TYPES[this::class] ?: "Unknown"

    companion object {
        val DEFAULT = DuckDuckGoOptions()

        val TYPES = mapOf(
            BingLocalOptions::class to "Bing",
            DuckDuckGoOptions::class to "DuckDuckGo",
            TavilyOptions::class to "Tavily",
            ExaOptions::class to "Exa",
            SearXNGOptions::class to "SearXNG",
            LinkUpOptions::class to "LinkUp",
            BraveOptions::class to "Brave",
            OllamaOptions::class to "Ollama",
            PerplexityOptions::class to "Perplexity",
            FirecrawlOptions::class to "Firecrawl",
            JinaOptions::class to "Jina",
            GrokOptions::class to "Grok",
            TinyfishOptions::class to "Tinyfish",
            SerperOptions::class to "Serper",
            SerpApiOptions::class to "SerpApi",
            SearchApiOptions::class to "SearchApi",
            YouOptions::class to "You.com",
            CustomJsOptions::class to "Custom JS",
        )

        /**
         * No-arg constructors for everything listed in [TYPES], so callers can build
         * a fresh default instance without reflection.
         *
         * The "add search provider" dialog used to do
         * `type.primaryConstructor!!.callBy(emptyMap())`, which asks kotlin-reflect
         * to fill in every default argument. That needs the compiler-generated
         * default-argument constructor — a method nothing calls directly, so R8
         * dropped it from release builds and adding a provider always crashed with
         * "This callable does not support a default call" (reported 2026-10-08;
         * exposed by 2fd33c78's `allowshrinking` @Serializable keep rule, which is
         * otherwise fine). Spelling the constructors out keeps the picker working
         * no matter what the shrinker decides to keep.
         *
         * `SearchServiceOptionsFactoryTest` fails if this map and [TYPES] drift
         * apart.
         */
        private val CONSTRUCTORS: Map<KClass<out SearchServiceOptions>, () -> SearchServiceOptions> = mapOf(
            BingLocalOptions::class to { BingLocalOptions() },
            DuckDuckGoOptions::class to { DuckDuckGoOptions() },
            TavilyOptions::class to { TavilyOptions() },
            ExaOptions::class to { ExaOptions() },
            SearXNGOptions::class to { SearXNGOptions() },
            LinkUpOptions::class to { LinkUpOptions() },
            BraveOptions::class to { BraveOptions() },
            OllamaOptions::class to { OllamaOptions() },
            PerplexityOptions::class to { PerplexityOptions() },
            FirecrawlOptions::class to { FirecrawlOptions() },
            JinaOptions::class to { JinaOptions() },
            GrokOptions::class to { GrokOptions() },
            TinyfishOptions::class to { TinyfishOptions() },
            SerperOptions::class to { SerperOptions() },
            SerpApiOptions::class to { SerpApiOptions() },
            SearchApiOptions::class to { SearchApiOptions() },
            YouOptions::class to { YouOptions() },
            CustomJsOptions::class to { CustomJsOptions() },
        )

        /** A new instance of [type] with every option left at its default value. */
        fun create(type: KClass<out SearchServiceOptions>): SearchServiceOptions =
            CONSTRUCTORS[type]?.invoke()
                ?: error("Unsupported search service type: ${type.qualifiedName}")
    }

    @Serializable
    @SerialName("bing_local")
    class BingLocalOptions(
        override val id: Uuid = Uuid.random()
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("duckduckgo")
    data class DuckDuckGoOptions(
        override val id: Uuid = Uuid.random(),
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("zhipu")
    data class ZhipuOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("doubao")
    data class DoubaoOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val mode: DoubaoSearchMode = DoubaoSearchMode.CUSTOM,
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("tavily")
    data class TavilyOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val depth: String = "advanced",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("exa")
    data class ExaOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("searxng")
    data class SearXNGOptions(
        override val id: Uuid = Uuid.random(),
        val url: String = "",
        val engines: String = "",
        val language: String = "",
        val username: String = "",
        val password: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("linkup")
    data class LinkUpOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val depth: String = "standard",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("brave")
    data class BraveOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("metaso")
    data class MetasoOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("ollama")
    data class OllamaOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("perplexity")
    data class PerplexityOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val maxTokens: Int? = null,
        val maxTokensPerPage: Int? = null,
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("firecrawl")
    data class FirecrawlOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("jina")
    data class JinaOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val searchUrl: String = "https://s.jina.ai/",
        val scrapeUrl: String = "https://r.jina.ai/",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("bocha")
    data class BochaOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val summary: Boolean = true,
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("rikkahub")
    data class LegacySearchOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val depth: String = "standard",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("grok")
    data class GrokOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
        val model: String = "grok-4-1-fast-non-reasoning",
        val customUrl: String = "https://api.x.ai/v1/responses",
        val systemPrompt: String = "You are a helpful search assistant. Search the web to find accurate and up-to-date information for the user's query. Provide a comprehensive answer with citations.",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("tinyfish")
    data class TinyfishOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("serper")
    data class SerperOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("serpapi")
    data class SerpApiOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("searchapi")
    data class SearchApiOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("you")
    data class YouOptions(
        override val id: Uuid = Uuid.random(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("custom_js")
    data class CustomJsOptions(
        override val id: Uuid = Uuid.random(),
        val name: String = "",
        val searchScript: String = DEFAULT_SEARCH_SCRIPT,
        val scrapeScript: String = "",
    ) : SearchServiceOptions() {
        override val displayName: String
            get() = name.ifBlank { "Custom JS" }
        companion object {
            const val DEFAULT_SCRAPE_SCRIPT = """// Implement scrape(urls) function
// urls is an array of URL strings
// Use fetch(url, options?) for HTTP requests
// fetch() returns { status, ok, text(), json() }
// Return { urls: [{ url, content, metadata?: { title?, description?, language? } }] }

function scrape(urls) {
  return {
    urls: urls.map(function(url) {
      const res = fetch(url);
      const body = res.text();
      return { url: url, content: body };
    })
  };
}"""

            const val DEFAULT_SEARCH_SCRIPT = """// Implement search(query, resultSize) function
// Use fetch(url, options?) for HTTP requests
// fetch() returns { status, ok, text(), json() }
// Return { items: [{ title, url, text }], answer?: string }

function search(query, resultSize) {
  const encoded = encodeURIComponent(query);
  const res = fetch("https://example.com/search?q=" + encoded + "&limit=" + resultSize);
  const data = res.json();
  return {
    items: data.results.map(function(r) {
      return { title: r.title, url: r.url, text: r.snippet };
    })
  };
}"""
        }
    }
}

@Serializable
enum class DoubaoSearchMode {
    @SerialName("global")
    GLOBAL,

    @SerialName("custom")
    CUSTOM,
}
