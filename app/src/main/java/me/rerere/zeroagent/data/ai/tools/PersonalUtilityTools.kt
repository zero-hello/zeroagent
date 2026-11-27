package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import kotlin.math.*

/** Local tools that operate only on values passed in the call. No files, permissions or network. */
internal object PersonalUtilityTools {
    private const val MAX_INPUT = 65_536
    private const val MAX_ITEMS = 4_096
    private val context = MathContext.DECIMAL128
    private val word = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")

    private class Args(val fields: JsonObject) {
        fun text(name: String): String = (fields[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("Provide text for $name.")
        fun number(name: String): BigDecimal = number(fields[name])
        fun values(name: String): JsonArray = (fields[name] as? JsonArray)?.takeIf { it.size <= MAX_ITEMS }
            ?: throw IllegalArgumentException("Provide an array of up to $MAX_ITEMS items for $name.")
        fun numbers() = values("values").map(::number).also { require(it.isNotEmpty()) { "Provide at least one number." } }
        fun lines() = text("text").lineSequence().take(MAX_ITEMS + 1).toList().also {
            require(it.size <= MAX_ITEMS) { "Too many lines." }
        }
        private fun number(value: JsonElement?): BigDecimal {
            val raw = (value as? JsonPrimitive)?.takeUnless { it.isString }?.content
            val number = raw?.takeIf { it.length <= 128 }?.toBigDecimalOrNull()
            require(number != null && number.scale() in -128..128 && number.precision() - number.scale() <= 140) { "Provide a number with a supported scale." }
            return number
        }
    }

    private data class Field(val name: String, val type: String, val description: String, val items: String? = null)
    private class Operation(
        val name: String,
        val summary: String,
        val fields: List<Field>,
        val run: (Args) -> Any,
    ) {
        fun entry(): ToolCatalogEntry {
            val tool = Tool(name = name, description = summary, parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        fields.forEach { field ->
                            put(field.name, buildJsonObject {
                                put("type", field.type)
                                put("description", field.description)
                                field.items?.let { put("items", buildJsonObject { put("type", it) }) }
                            })
                        }
                    },
                    required = fields.map { it.name },
                )
            }, execute = { raw ->
                val result = try {
                    require(raw.toString().length <= MAX_INPUT) { "Input is too large." }
                    val args = raw as? JsonObject ?: throw IllegalArgumentException("Expected a JSON object.")
                    require(args.keys == fields.map { it.name }.toSet()) { "Unexpected or missing parameters." }
                    val value = run(Args(args))
                    require(value.toString().length <= MAX_INPUT) { "Result is too large." }
                    buildJsonObject { put("result", jsonValue(value)) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    buildJsonObject { put("error", error.message?.take(240) ?: "Invalid input.") }
                }
                listOf(UIMessagePart.Text(result.toString()))
            })
            return ToolCatalogEntry(name, summary, ToolCatalogSource.LOCAL, tool)
        }
    }

    private fun jsonValue(value: Any?): JsonElement = when (value) {
        is JsonElement -> value
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }.mapValues { jsonValue(it.value) })
        is Iterable<*> -> JsonArray(value.map(::jsonValue))
        null -> JsonNull
        else -> JsonPrimitive(value.toString())
    }

    private fun text(name: String = "text", description: String = "Text to process") = Field(name, "string", description)
    private fun number(name: String, description: String) = Field(name, "number", description)
    private val numbers = Field("values", "array", "Numbers to process", "number")
    private val values = Field("values", "array", "Values to compare", "string")
    private val dates = listOf(text("start", "Start date in YYYY-MM-DD"), text("end", "End date in YYYY-MM-DD"))
    private val datetime = text("timestamp", "ISO-8601 timestamp with an offset, for example 2026-11-26T09:00:00+08:00")
    private val money = number("amount", "Base amount")
    private val percent = number("percent", "Percentage, for example 15 for 15 percent")
    private fun utility(name: String, summary: String, vararg fields: Field, run: (Args) -> Any) =
        Operation("util_$name", summary, fields.toList(), run)
    private fun transform(name: String, summary: String, run: (String) -> Any) =
        utility(name, summary, text()) { run(it.text("text")) }
    private fun stats(name: String, summary: String, run: (List<BigDecimal>) -> Any) =
        utility(name, summary, numbers) { run(it.numbers()) }
    private fun ratio(numerator: BigDecimal, denominator: BigDecimal) = numerator.divide(denominator, context)
    private fun fraction(percent: BigDecimal) = percent.movePointLeft(2)
    private fun median(numbers: List<BigDecimal>): BigDecimal {
        val sorted = numbers.sorted()
        val index = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[index] else ratio(sorted[index - 1] + sorted[index], BigDecimal(2))
    }
    private fun mean(numbers: List<BigDecimal>) = ratio(numbers.reduce(BigDecimal::add), BigDecimal(numbers.size))
    private fun variance(numbers: List<BigDecimal>, divisor: Int): BigDecimal {
        require(divisor > 0) { "Provide at least two numbers for sample variance." }
        val center = mean(numbers)
        return ratio(numbers.map { (it - center).pow(2, context) }.reduce(BigDecimal::add), BigDecimal(divisor))
    }

    private val operations = listOf(
        transform("text_measure", "Count characters, words, lines and UTF-8 bytes.") {
            mapOf("codePoints" to it.codePointCount(0, it.length), "words" to word.findAll(it).count(),
                "lines" to it.lineSequence().count(), "utf8Bytes" to it.toByteArray().size)
        },
        utility("text_unique_lines", "Remove duplicate lines and preserve their first occurrence.", text()) {
            it.lines().distinct().joinToString("\n")
        },
        utility("text_sort_lines", "Sort lines in Unicode order.", text()) { it.lines().sorted().joinToString("\n") },
        utility("text_remove_blank_lines", "Remove empty or whitespace-only lines.", text()) {
            it.lines().filterNot(String::isBlank).joinToString("\n")
        },
        utility("text_number_lines", "Add a line number to each line of supplied text.", text()) {
            it.lines().mapIndexed { index, line -> "${index + 1}. $line" }.joinToString("\n")
        },
        utility("text_extract_lines", "Return matching lines that contain an exact search string.", text(), text("query", "Exact search text")) {
            val query = it.text("query")
            require(query.isNotEmpty()) { "Search text is empty." }
            it.lines().mapIndexedNotNull { index, line ->
                if (query in line) mapOf("line" to index + 1, "text" to line) else null
            }
        },
        transform("text_word_frequencies", "Count word occurrences and return the 50 most frequent words.") {
            word.findAll(it.lowercase(Locale.ROOT)).groupingBy { match -> match.value }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { entry -> entry.value }.thenBy { entry -> entry.key })
                .take(50).associate { entry -> entry.key to entry.value }
        },
        utility("text_literal_replace", "Replace exact text, without regular expressions.", text(), text("find", "Text to match"), text("replacement", "Replacement text")) {
            val from = it.text("find")
            val subject = it.text("text")
            val to = it.text("replacement")
            require(from.isNotEmpty()) { "Text to match is empty." }
            var index = 0
            var estimatedLength = subject.length.toLong()
            while (index < subject.length) {
                val next = subject.indexOf(from, index)
                if (next < 0) break
                estimatedLength += to.length - from.length
                require(estimatedLength <= MAX_INPUT) { "Result is too large." }
                index = next + from.length
            }
            subject.replace(from, to)
        },
        transform("text_collapse_spacing", "Trim text and collapse repeated whitespace to single spaces.") { it.trim().replace(Regex("\\s+"), " ") },
        transform("text_normalize_unicode", "Normalize composed Unicode text to NFC for consistent comparison.") { Normalizer.normalize(it, Normalizer.Form.NFC) },
        transform("text_split_paragraphs", "Split text at blank lines into paragraphs.") { it.trim().split(Regex("\\r?\\n[ \\t]*\\r?\\n+")) },
        transform("markdown_headings", "Extract Markdown ATX headings outside fenced code blocks.") { markdownHeadings(it) },
        transform("markdown_tasks", "List Markdown checkbox tasks and whether each one is complete.") {
            val task = Regex("^\\s*[-*+]\\s+\\[([ xX])]\\s+(.*)$")
            val result = mutableListOf<Map<String, Any>>()
            visitMarkdownLines(it) { index, line ->
                task.matchEntire(line)?.let { match -> result += mapOf("line" to index, "done" to (match.groupValues[1] != " "), "text" to match.groupValues[2]) }
            }
            result
        },
        transform("json_pretty_print", "Validate JSON and format its indentation.") { Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), parseJson(it)) },
        transform("json_minify", "Validate JSON and remove formatting whitespace.") { parseJson(it).toString() },
        utility("json_pointer", "Read one value at an RFC 6901 JSON Pointer.", text(), text("pointer", "JSON Pointer such as /items/0/name")) {
            jsonPointer(parseJson(it.text("text")), it.text("pointer"))
        },
        transform("json_describe_shape", "Summarize a JSON value's type and top-level fields.") {
            when (val value = parseJson(it)) {
                is JsonObject -> mapOf("type" to "object", "fields" to value.mapValues { entry -> jsonType(entry.value) })
                is JsonArray -> mapOf("type" to "array", "items" to value.size, "itemTypes" to value.map(::jsonType).distinct())
                else -> mapOf("type" to jsonType(value))
            }
        },
        utility("json_compare_fields", "Compare the keys of two JSON objects and report changed values.", text("before", "First object as JSON"), text("after", "Second object as JSON")) {
            val before = parseJson(it.text("before")) as? JsonObject ?: error("First value must be an object.")
            val after = parseJson(it.text("after")) as? JsonObject ?: error("Second value must be an object.")
            mapOf("added" to (after.keys - before.keys), "removed" to (before.keys - after.keys),
                "changed" to (before.keys intersect after.keys).filter { key -> before[key] != after[key] })
        },
        transform("base64_encode", "Encode UTF-8 text as Base64.") { Base64.getEncoder().encodeToString(it.toByteArray()) },
        transform("base64_decode_text", "Decode Base64 containing valid UTF-8 text.") {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(it.trim()))).toString()
        },
        transform("sha256_text", "Calculate the SHA-256 fingerprint of exact UTF-8 text.") {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        },
        transform("url_components", "Inspect the parts of a URL without visiting it. User credentials are not returned.") {
            val uri = URI(it)
            require(uri.scheme in setOf("http", "https") && uri.host != null) { "Provide an HTTP or HTTPS URL." }
            mapOf("scheme" to uri.scheme, "host" to uri.host, "port" to uri.port.takeIf { port -> port >= 0 },
                "path" to uri.rawPath, "hasQuery" to (uri.rawQuery != null), "hasFragment" to (uri.rawFragment != null))
        },
        utility("set_compare", "Compare two lists of strings for common and unique entries.", values.copy(name = "left"), values.copy(name = "right")) {
            fun strings(name: String) = it.values(name).map { item ->
                (item as? JsonPrimitive)?.takeIf { value -> value.isString }?.content ?: error("Array items must be strings.")
            }.toSet()
            val left = strings("left")
            val right = strings("right")
            mapOf("common" to (left intersect right), "leftOnly" to (left - right), "rightOnly" to (right - left))
        },
        utility("list_duplicates", "Find repeated strings and their occurrence counts.", values) {
            it.values("values").map { item ->
                (item as? JsonPrimitive)?.takeIf { value -> value.isString }?.content ?: error("Array items must be strings.")
            }.groupingBy { value -> value }.eachCount().filterValues { count -> count > 1 }
        },
        stats("sum", "Add decimal numbers without binary floating-point rounding.") { it.reduce(BigDecimal::add) },
        stats("mean", "Calculate the arithmetic mean of numbers.", ::mean),
        stats("median", "Calculate the median, including even-sized lists.", ::median),
        stats("min_max", "Find the minimum, maximum and range of numbers.") {
            val min = it.min()
            val max = it.max()
            mapOf("min" to min, "max" to max, "range" to (max - min))
        },
        stats("variance", "Calculate population variance and standard deviation.") {
            val value = variance(it, it.size)
            mapOf("variance" to value, "standardDeviation" to sqrt(value.toDouble()))
        },
        stats("sample_variance", "Estimate variance and standard deviation from a sample.") {
            val value = variance(it, it.size - 1)
            mapOf("variance" to value, "standardDeviation" to sqrt(value.toDouble()))
        },
        stats("quartiles", "Find quartiles and the interquartile range using medians of the lower and upper halves.") {
            require(it.size >= 2) { "Provide at least two numbers." }
            val sorted = it.sorted()
            val q1 = median(sorted.take(sorted.size / 2))
            val q3 = median(sorted.takeLast(sorted.size / 2))
            mapOf("q1" to q1, "median" to median(sorted), "q3" to q3, "iqr" to (q3 - q1))
        },
        stats("numeric_modes", "Find all numbers tied for maximum frequency.") {
            val counts = it.map(BigDecimal::stripTrailingZeros).groupingBy { value -> value }.eachCount()
            val frequency = counts.values.max()
            mapOf("values" to counts.filterValues { count -> count == frequency }.keys.sorted(), "frequency" to frequency)
        },
        utility("weighted_average", "Compute a weighted mean with one nonnegative weight per value.", numbers,
            Field("weights", "array", "One weight per value", "number")) {
            val raw = it.numbers()
            val weights = Args(JsonObject(mapOf("values" to it.values("weights")))).numbers()
            require(raw.size == weights.size && weights.all { weight -> weight.signum() >= 0 }) { "Provide matching, nonnegative weights." }
            val sum = weights.reduce(BigDecimal::add)
            require(sum.signum() > 0) { "At least one weight must be positive." }
            ratio(raw.zip(weights) { value, weight -> value * weight }.reduce(BigDecimal::add), sum)
        },
        utility("percent_of", "Calculate an amount's percentage share.", money, percent) { it.number("amount") * fraction(it.number("percent")) },
        utility("percent_change", "Calculate percentage change from a nonzero starting value.", number("before", "Starting value"), number("after", "Ending value")) {
            val before = it.number("before")
            require(before.signum() != 0) { "Starting value must not be zero." }
            ratio(it.number("after") - before, before.abs()).movePointRight(2)
        },
        utility("discount_total", "Calculate a discounted amount and the savings.", money, percent) {
            val amount = it.number("amount")
            val rate = it.number("percent")
            require(amount.signum() >= 0 && rate in BigDecimal.ZERO..BigDecimal(100)) { "Provide a nonnegative amount and a discount from 0 to 100." }
            val savings = amount * fraction(rate)
            mapOf("savings" to savings, "total" to (amount - savings))
        },
        utility("tax_total", "Calculate tax and total from an exclusive amount and supplied rate.", money, percent) {
            val amount = it.number("amount")
            val tax = amount * fraction(it.number("percent"))
            mapOf("tax" to tax, "total" to (amount + tax))
        },
        utility("split_bill", "Split an amount into equal shares rounded to cents, with exact remainder handling.", money, number("people", "Number of people")) {
            val people = it.number("people").intValueExact()
            val cents = it.number("amount").setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact()
            require(people in 1..MAX_ITEMS && cents >= 0) { "Provide a nonnegative amount and a valid group size." }
            val base = cents / people
            mapOf("standardShare" to BigDecimal.valueOf(base, 2),
                "higherShare" to (if (cents % people == 0L) null else BigDecimal.valueOf(base + 1, 2)),
                "peoplePayingHigherShare" to cents % people)
        },
        utility("round_decimal", "Round a decimal using half-even rounding to the requested places.", number("value", "Decimal to round"), number("places", "Decimal places from 0 to 24")) {
            val places = it.number("places").intValueExact()
            require(places in 0..24) { "Decimal places must be between 0 and 24." }
            it.number("value").setScale(places, RoundingMode.HALF_EVEN)
        },
        utility("date_days_between", "Count signed calendar days between two dates.", *dates.toTypedArray()) {
            ChronoUnit.DAYS.between(LocalDate.parse(it.text("start")), LocalDate.parse(it.text("end")))
        },
        utility("date_add_days", "Add or subtract calendar days from a date.", text("date", "ISO date"), number("days", "Signed number of days")) {
            LocalDate.parse(it.text("date")).plusDays(it.number("days").longValueExact()).toString()
        },
        utility("date_add_months", "Add calendar months and clamp to the last valid day when needed.", text("date", "ISO date"), number("months", "Signed number of months")) {
            LocalDate.parse(it.text("date")).plusMonths(it.number("months").longValueExact()).toString()
        },
        utility("date_week_information", "Find weekday, ISO week number and week-based year for a date.", text("date", "ISO date")) {
            val date = LocalDate.parse(it.text("date"))
            val iso = java.time.temporal.WeekFields.ISO
            mapOf("weekday" to date.dayOfWeek.name, "isoWeek" to date[iso.weekOfWeekBasedYear()], "isoWeekYear" to date[iso.weekBasedYear()])
        },
        utility("date_business_days", "Count Monday to Friday dates, excluding the end date. Holidays are not excluded.", *dates.toTypedArray()) {
            val start = LocalDate.parse(it.text("start"))
            val end = LocalDate.parse(it.text("end"))
            val days = ChronoUnit.DAYS.between(start, end)
            require(days >= 0) { "End must be on or after start." }
            val remainder = (0 until (days % 7).toInt()).count { day -> start.plusDays(day.toLong()).dayOfWeek.value <= 5 }
            days / 7 * 5 + remainder
        },
        utility("time_convert_zone", "Convert a timestamp with offset to an IANA time zone, accounting for daylight saving.", datetime, text("zone", "IANA time zone, such as Asia/Taipei")) {
            OffsetDateTime.parse(it.text("timestamp")).atZoneSameInstant(ZoneId.of(it.text("zone"))).toString()
        },
        utility("time_elapsed", "Calculate elapsed seconds between two timestamps with offsets.", datetime.copy(name = "start"), datetime.copy(name = "end")) {
            val duration = Duration.between(OffsetDateTime.parse(it.text("start")), OffsetDateTime.parse(it.text("end")))
            BigDecimal.valueOf(duration.seconds).add(BigDecimal.valueOf(duration.nano.toLong(), 9))
        },
        utility("time_to_epoch", "Convert an ISO offset timestamp to Unix seconds and milliseconds.", datetime) {
            val value = OffsetDateTime.parse(it.text("timestamp")).toInstant()
            mapOf("seconds" to value.epochSecond, "milliseconds" to value.toEpochMilli())
        },
        utility("time_from_epoch", "Convert Unix seconds to an ISO timestamp in a chosen zone.", number("seconds", "Unix epoch seconds"), text("zone", "IANA time zone")) {
            Instant.ofEpochSecond(it.number("seconds").longValueExact()).atZone(ZoneId.of(it.text("zone"))).toString()
        },
        utility("temperature_convert", "Convert between Celsius, Fahrenheit and Kelvin.", number("value", "Temperature"), text("from", "Source unit C, F or K"), text("to", "Target unit C, F or K")) {
            val value = it.number("value")
            val celsius = when (it.text("from").uppercase(Locale.ROOT)) {
                "C" -> value
                "F" -> ratio((value - BigDecimal(32)) * BigDecimal(5), BigDecimal(9))
                "K" -> value - BigDecimal("273.15")
                else -> error("Source unit must be C, F or K.")
            }
            require(celsius >= BigDecimal("-273.15")) { "Temperature is below absolute zero." }
            when (it.text("to").uppercase(Locale.ROOT)) {
                "C" -> celsius
                "F" -> ratio(celsius * BigDecimal(9), BigDecimal(5)) + BigDecimal(32)
                "K" -> celsius + BigDecimal("273.15")
                else -> error("Target unit must be C, F or K.")
            }
        },
        utility("date_weekday_occurrences", "Count one weekday in a date range, excluding the end date.", *dates.toTypedArray(), text("weekday", "English weekday such as MONDAY")) {
            val start = LocalDate.parse(it.text("start"))
            val end = LocalDate.parse(it.text("end"))
            val wanted = DayOfWeek.valueOf(it.text("weekday").uppercase(Locale.ROOT))
            val span = ChronoUnit.DAYS.between(start, end)
            require(span >= 0) { "End must be on or after start." }
            val offset = (wanted.value - start.dayOfWeek.value + 7) % 7
            span / 7 + if (offset < span % 7) 1 else 0
        },
        utility("ratio_reduce", "Reduce an integer fraction to canonical numerator and denominator.", number("numerator", "Integer numerator"), number("denominator", "Nonzero integer denominator")) {
            val numerator = it.number("numerator").toBigIntegerExact()
            val denominator = it.number("denominator").toBigIntegerExact()
            require(denominator.signum() != 0) { "Denominator must not be zero." }
            val factor = numerator.gcd(denominator).let { gcd -> if (denominator.signum() < 0) gcd.negate() else gcd }
            mapOf("numerator" to numerator.divide(factor), "denominator" to denominator.divide(factor))
        },
        conversion("length_convert", "length", "mm cm m km in ft yd mi nmi", "0.001 0.01 1 1000 0.0254 0.3048 0.9144 1609.344 1852"),
        conversion("mass_convert", "mass", "mg g kg t oz lb st", "0.000001 0.001 1 1000 0.028349523125 0.45359237 6.35029318"),
        conversion("area_convert", "area", "cm2 m2 km2 ha in2 ft2 acre", "0.0001 1 1000000 10000 0.00064516 0.09290304 4046.8564224"),
        conversion("volume_convert", "volume", "ml l m3 us_floz us_cup us_gal uk_gal", "0.001 1 1000 0.0295735295625 0.2365882365 3.785411784 4.54609"),
        conversion("duration_convert", "duration", "ms s min h day week", "0.001 1 60 3600 86400 604800"),
        conversion("data_size_convert", "data size", "b kb mb gb tb kib mib gib tib", "1 1000 1000000 1000000000 1000000000000 1024 1048576 1073741824 1099511627776"),
        conversion("energy_convert", "energy", "j kj wh kwh kcal", "1 1000 3600 3600000 4184"),
        conversion("pressure_convert", "pressure", "pa hpa kpa bar atm psi", "1 100 1000 100000 101325 6894.757293168"),
        conversion("speed_convert", "speed", "m/s km/h mph knot ft/s", "1 0.2777777777777777777777777777777778 0.44704 0.5144444444444444444444444444444444 0.3048"),
        utility("geo_distance", "Estimate great-circle distance between latitude and longitude pairs.",
            number("lat1", "Latitude of first point"), number("lon1", "Longitude of first point"),
            number("lat2", "Latitude of second point"), number("lon2", "Longitude of second point")) {
            fun radians(name: String, limit: Double): Double {
                val degrees = it.number(name).toDouble()
                require(degrees in -limit..limit) { "Coordinate is outside the valid range." }
                return Math.toRadians(degrees)
            }
            val lat1 = radians("lat1", 90.0)
            val lat2 = radians("lat2", 90.0)
            val delta = radians("lon2", 180.0) - radians("lon1", 180.0)
            val h = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) * sin(delta / 2).pow(2)
            mapOf("kilometers" to 12_742.0176 * asin(sqrt(h.coerceIn(0.0, 1.0))), "method" to "great circle on mean-radius Earth")
        },
    )

    val names: Set<String> = operations.mapTo(linkedSetOf()) { it.name }
    val entries: List<ToolCatalogEntry> by lazy { operations.map { it.entry() } }

    private fun conversion(name: String, quantity: String, names: String, factors: String): Operation {
        val units = names.split(' ').zip(factors.split(' ').map(String::toBigDecimal)).toMap()
        return utility(name, "Convert $quantity between $names.", number("value", "Quantity"),
            text("from", "Source unit, one of $names"), text("to", "Target unit, one of $names")) {
            val from = units[it.text("from").lowercase(Locale.ROOT)] ?: error("Unknown source unit.")
            val to = units[it.text("to").lowercase(Locale.ROOT)] ?: error("Unknown target unit.")
            ratio(it.number("value") * from, to)
        }
    }

    private fun parseJson(input: String): JsonElement {
        var depth = 0
        var quoted = false
        var escaped = false
        input.forEach { character ->
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> require(++depth <= 40) { "JSON is nested too deeply." }
                '}', ']' -> depth--
            }
        }
        return Json.parseToJsonElement(input)
    }

    private fun jsonType(value: JsonElement): String = when (value) {
        is JsonObject -> "object"
        is JsonArray -> "array"
        JsonNull -> "null"
        is JsonPrimitive -> when {
            value.isString -> "string"
            value.booleanOrNull != null -> "boolean"
            else -> "number"
        }
    }

    private fun jsonPointer(root: JsonElement, pointer: String): JsonElement {
        if (pointer.isEmpty()) return root
        require(pointer.startsWith('/')) { "A nonempty JSON Pointer starts with /." }
        val invalidEscape = Regex("~(?:[^01]|$)")
        return pointer.drop(1).split('/').fold(root) { node, segment ->
            require(!invalidEscape.containsMatchIn(segment)) { "Invalid JSON Pointer escape." }
            val key = segment.replace("~1", "/").replace("~0", "~")
            when (node) {
                is JsonObject -> node[key]
                is JsonArray -> key.takeIf { it == "0" || (it.firstOrNull()?.let { first -> first in '1'..'9' } == true && it.all(Char::isDigit)) }
                    ?.toIntOrNull()?.let(node::getOrNull)
                else -> null
            } ?: error("JSON Pointer does not resolve.")
        }
    }

    private fun visitMarkdownLines(input: String, visit: (Int, String) -> Unit) {
        val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
        var openFence: String? = null
        input.lineSequence().forEachIndexed { index, line ->
            val match = fence.matchEntire(line)
            val found = match?.groupValues?.get(1)
            val current = openFence
            if (current == null) {
                if (found != null) openFence = found else visit(index + 1, line)
            } else if (found != null && found.first() == current.first() && found.length >= current.length &&
                match?.groupValues?.get(2)?.isBlank() == true) {
                openFence = null
            }
        }
    }

    private fun markdownHeadings(input: String): List<Map<String, Any>> {
        val heading = Regex("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
        val result = mutableListOf<Map<String, Any>>()
        visitMarkdownLines(input) { index, line ->
            heading.matchEntire(line)?.let { match ->
                result += mapOf("line" to index, "level" to match.groupValues[1].length, "text" to match.groupValues[2])
            }
        }
        return result
    }
}
