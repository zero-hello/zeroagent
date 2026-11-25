package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Year
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Public World Development Indicators. Each tool is bound to a single published indicator code. */
internal class WorldBankTools(
    readCatalog: () -> List<String>,
    private val retrieve: suspend (HttpUrl) -> String = ::fetchWorldBankData,
) {
    private data class Indicator(val code: String, val label: String, val topics: String) {
        val toolName: String = "wdi_" + code.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
        val source: String = "https://data.worldbank.org/indicator/$code"
    }

    val entries: List<ToolCatalogEntry> by lazy {
        readCatalog().filter(String::isNotBlank).map { line ->
            val fields = line.split('\t', limit = 3)
            require(fields.size == 3 && fields[0].matches(INDICATOR_CODE) && fields[1].isNotBlank())
            Indicator(fields[0], fields[1], fields[2]).entry()
        }.also { tools -> check(tools.distinctBy { it.name }.size == tools.size) }
    }

    private fun Indicator.entry(): ToolCatalogEntry {
        val indicator = this
        val summary = if (topics.isBlank()) label else "$label · $topics"
        val tool = Tool(
            name = toolName,
            description = "World Bank indicator $code: $label. Get annual public statistics for up to 5 countries or regions. The result includes values, years, update date and source. Some places or years have no observations.",
            parameters = { indicatorSchema },
            needsApproval = { true },
            execute = { arguments ->
                val result = try {
                    val request = worldBankRequest(indicator.code, arguments, Year.now().value)
                    val first = decodeWorldBankData(indicator.code, indicator.label, indicator.source, retrieve(request), request.toString())
                    if ("error" !in first) first else {
                        val explicit = request.newBuilder().addQueryParameter("source", "2").build()
                        decodeWorldBankData(indicator.code, indicator.label, indicator.source, retrieve(explicit), explicit.toString())
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    worldBankError(error.message ?: "World Bank is temporarily unavailable.", indicator.source)
                } catch (_: IllegalArgumentException) {
                    worldBankError("Use up to five ISO country or World Bank region codes and an optional year range of up to 26 years.", indicator.source)
                }
                listOf(UIMessagePart.Text(result.toString()))
            },
        )
        return ToolCatalogEntry(toolName, summary, ToolCatalogSource.PUBLIC_DATA, tool)
    }
}

private val INDICATOR_CODE = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{2,45}")
private val REGION_CODE = Regex("[A-Za-z]{2,3}")
private val YEAR = Regex("[0-9]{4}")
private val BASE_URL = HttpUrl.Builder().scheme("https").host("api.worldbank.org").addPathSegment("v2").build()
private const val MAX_WORLD_BANK_BYTES = 256 * 1024L
private val allowedFields = setOf("countries", "start_year", "end_year")
private val indicatorSchema = InputSchema.Obj(
    properties = buildJsonObject {
        put("countries", buildJsonObject {
            put("type", "array")
            put("items", buildJsonObject { put("type", "string") })
            put("description", "Up to five ISO country codes or World Bank aggregate codes. For example JPN, BRA, WLD. Use codes only.")
        })
        listOf("start_year", "end_year").forEach { name ->
            put(name, buildJsonObject {
                put("type", "integer")
                put("description", "Optional inclusive year. Provide both years to select a range of up to 26 years.")
            })
        }
    },
    required = listOf("countries"),
)

internal fun worldBankRequest(code: String, input: JsonElement, currentYear: Int): HttpUrl {
    require(code.matches(INDICATOR_CODE))
    val fields = input as? JsonObject ?: throw IllegalArgumentException("Expected an object.")
    require(fields.keys.all { it in allowedFields })
    val countries = fields["countries"] as? JsonArray ?: throw IllegalArgumentException("Missing countries.")
    require(countries.size in 1..5)
    val codes = countries.map { value ->
        val country = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(country != null && country.matches(REGION_CODE))
        country.uppercase(Locale.ROOT)
    }.distinct()
    fun year(name: String): Int? {
        if (name !in fields) return null
        val number = (fields[name] as? JsonPrimitive)?.takeUnless { it.isString }?.content
        require(number != null && number.matches(YEAR))
        return number.toInt()
    }
    val start = year("start_year")
    val end = year("end_year")
    require((start == null) == (end == null))
    if (start != null && end != null) require(start >= 1960 && end <= currentYear && end - start in 0..25)
    val firstYear = start ?: currentYear - 9
    val lastYear = end ?: currentYear
    return BASE_URL.newBuilder()
        .addPathSegment("country").addPathSegment(codes.joinToString(";"))
        .addPathSegment("indicator").addPathSegment(code)
        .addQueryParameter("format", "json")
        .addEncodedQueryParameter("date", if (firstYear == lastYear) "$firstYear" else "$firstYear:$lastYear")
        .apply {
            if ((lastYear - firstYear + 1) * codes.size > 50) addQueryParameter("per_page", "150")
        }
        .build()
}

internal fun decodeWorldBankData(code: String, label: String, source: String, body: String, request: String): JsonObject {
    val parsed = runCatching { Json.parseToJsonElement(body) as? JsonArray }.getOrNull()
    val meta = parsed?.getOrNull(0) as? JsonObject
    val data = parsed?.getOrNull(1) as? JsonArray
    fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    if (meta == null || meta.text("sourceid") != "2") {
        return worldBankError("World Bank did not return results for these parameters. Check the country or region codes.", source)
    }
    val observations = data.orEmpty().mapNotNull { row ->
        val record = row as? JsonObject ?: return@mapNotNull null
        if ((record["indicator"] as? JsonObject)?.text("id") != code) return@mapNotNull null
        val year = record.text("date")?.takeIf { it.matches(YEAR) } ?: return@mapNotNull null
        val value = (record["value"] as? JsonPrimitive)?.takeIf { !it.isString && it.doubleOrNull?.isFinite() == true }
            ?: return@mapNotNull null
        buildJsonObject {
            put("year", year.toInt())
            put("value", value)
            record.text("countryiso3code")?.takeIf(String::isNotBlank)?.let { put("code", it) }
            (record["country"] as? JsonObject)?.text("value")?.let { put("country", it) }
            record.text("unit")?.takeIf(String::isNotBlank)?.let { put("unit", it) }
        }
    }
    return buildJsonObject {
        put("indicator", code)
        put("label", label)
        put("publisher", "World Bank · World Development Indicators")
        put("source", source)
        put("query", request)
        meta.text("lastupdated")?.let { put("dataset_updated", it) }
        put("observations", JsonArray(observations))
        val omitted = (data?.size ?: 0) - observations.size
        if (omitted > 0) put("unavailable_rows", omitted)
        if (observations.isEmpty()) put("status", "no_observations")
    }
}

private fun worldBankError(message: String, source: String) = buildJsonObject {
    put("error", message)
    put("source", source)
}

// No shared provider headers, cookies, or redirects. Requests only use the fixed HTTPS origin.
private val worldBankClient by lazy {
    OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS).build()
}

private suspend fun fetchWorldBankData(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
    val call = worldBankClient.newCall(Request.Builder().url(url).header("Accept", "application/json").get().build())
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(IOException("World Bank connection failed or timed out."))
        }

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching {
                response.use {
                    if (it.code == 429) throw IOException("World Bank is rate limiting requests. Try again shortly.")
                    if (!it.isSuccessful) throw IOException("World Bank returned HTTP ${it.code}.")
                    val reader = it.body.source()
                    if (reader.request(MAX_WORLD_BANK_BYTES + 1)) throw IOException("World Bank response exceeds the size limit.")
                    reader.readUtf8()
                }
            }
            continuation.resumeWith(result)
        }
    })
}
