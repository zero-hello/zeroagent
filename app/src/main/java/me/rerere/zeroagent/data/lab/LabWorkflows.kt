package me.rerere.zeroagent.data.lab

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.MathContext

internal fun renderRecipe(template: String, values: Map<String, String>): String =
    RecipeSlot.replace(template) { values[it.groupValues[1]] ?: it.value }

internal val RecipeSlot = Regex("\\{\\{([^{}\\r\\n]{1,50})}}")

@Serializable
internal data class LabRecipe(val name: String, val template: String)

@Serializable
internal data class LabArtifact(val content: String, val history: List<String> = emptyList()) {
    fun revise(next: String): LabArtifact = if (next == content) this else LabArtifact(next, (history + content).takeLast(10))
}

internal data class DiffLine(val kind: Char, val text: String)

/** Line-based LCS. The caller gets the exact original line content and a bounded memory cost. */
internal fun labDiff(before: String, after: String): List<DiffLine> {
    require(before.length <= 200_000 && after.length <= 200_000) { "每份內容最多 20 萬字元。" }
    val left = before.lines(); val right = after.lines()
    require(left.size.toLong() * right.size <= 2_000_000) { "行數太多，請分段比較。" }
    val dp = Array(left.size + 1) { IntArray(right.size + 1) }
    for (i in left.indices.reversed()) for (j in right.indices.reversed()) {
        dp[i][j] = if (left[i] == right[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
    }
    return buildList {
        var i = 0; var j = 0
        while (i < left.size || j < right.size) {
            when {
                i < left.size && j < right.size && left[i] == right[j] -> {
                    add(DiffLine(' ', left[i++]))
                    j++
                }
                j < right.size && (i == left.size || dp[i][j + 1] > dp[i + 1][j]) -> add(DiffLine('+', right[j++]))
                else -> add(DiffLine('-', left[i++]))
            }
        }
    }
}

private fun parseCsv(text: String): List<List<String>> = buildList {
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var justClosed = false
    var i = 0
    fun finishCell() { row.add(cell.toString()); cell.clear(); justClosed = false }
    fun finishRow() { finishCell(); add(row); row = mutableListOf() }
    while (i < text.length) {
        val c = text[i++]
        when {
            quoted && c == '"' && text.getOrNull(i) == '"' -> { i++; cell.append('"') }
            quoted && c == '"' -> { quoted = false; justClosed = true }
            quoted -> cell.append(c)
            c == ',' -> finishCell()
            c == '\n' || c == '\r' -> { if (c == '\r' && text.getOrNull(i) == '\n') i++; finishRow() }
            c == '"' && cell.isEmpty() && !justClosed -> quoted = true
            c == '"' || justClosed -> error("第 ${size + 1} 列，第 ${row.size + 1} 欄的引號位置有誤。")
            else -> cell.append(c)
        }
    }
    require(!quoted) { "第 ${size + 1} 列，第 ${row.size + 1} 欄的引號未關閉。" }
    if (cell.isNotEmpty() || row.isNotEmpty() || justClosed || isEmpty()) finishRow()
}

/** Required columns are specified as name:type, separated by line breaks or commas. */
internal fun validateLabOutput(format: String, text: String, schema: String): List<String> {
    require(text.isNotBlank() && text.length <= 200_000) { "請輸入 20 萬字元以內的內容。" }
    val rules = schema.split(',', '\n').map(String::trim).filter(String::isNotBlank).map {
        it.substringBefore(':').trim() to it.substringAfter(':', "").trim().lowercase()
    }
    require(rules.all { (key, type) -> key.isNotBlank() && type in setOf("", "string", "number", "boolean", "array", "object", "null") }) {
        "欄位格式為名稱:類型。類型可用 string、number、boolean、array、object、null。"
    }
    if (format == "CSV") {
        require(rules.none { it.second in setOf("array", "object", "null") }) { "CSV 欄位只接受 string、number 或 boolean。" }
        val rows = parseCsv(text)
        val headers = rows.first()
        return buildList {
            if (headers.any(String::isBlank) || headers.distinct().size != headers.size) add("第 1 列有空白或重複的欄名。")
            rules.filter { it.first !in headers }.forEach { add("第 1 列缺少欄位 ${it.first}。") }
            rows.drop(1).forEachIndexed { i, row ->
                if (row.size != headers.size) add("第 ${i + 2} 列應有 ${headers.size} 欄，實際為 ${row.size} 欄。")
                rules.forEach { (name, type) ->
                    val value = row.getOrNull(headers.indexOf(name)) ?: return@forEach
                    if ((type == "number" && value.toBigDecimalOrNull() == null) ||
                        (type == "boolean" && value !in setOf("true", "false"))) add("第 ${i + 2} 列的 $name 應為 $type。")
                }
            }
        }
    }
    val parsed = Json.parseToJsonElement(text)
    if (rules.isEmpty()) return emptyList()
    val fields = parsed as? JsonObject ?: return listOf("根節點應為 JSON 物件。")
    return buildList {
        rules.forEach { (name, type) ->
            val value = fields[name]
            if (value == null) add("缺少欄位 $name。")
            else if (type.isNotEmpty() && type != jsonType(value)) add("欄位 $name 應為 $type，實際為 ${jsonType(value)}。")
        }
    }
}

private fun jsonType(value: JsonElement): String = when (value) {
    JsonNull -> "null"
    is JsonObject -> "object"
    is JsonArray -> "array"
    is JsonPrimitive -> when {
        value.isString -> "string"
        value.booleanOrNull != null -> "boolean"
        else -> "number"
    }
}

internal fun evaluateLabFormula(input: String, variables: Map<String, BigDecimal>): BigDecimal {
    require(input.length <= 240) { "算式最多 240 字元。" }
    val tokens = Regex("[A-Za-z][A-Za-z0-9_]*|(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)|[+*/()\\-]")
        .findAll(input).map { it.value }.toList()
    require(tokens.joinToString("") == input.filterNot(Char::isWhitespace)) { "算式含有無法辨識的符號。" }
    var index = 0
    fun peek() = tokens.getOrNull(index)
    fun take() = tokens.getOrNull(index++) ?: error("算式不完整。")
    var expression: (() -> BigDecimal)? = null
    fun atom(): BigDecimal {
        val token = take()
        return when (token) {
            "(" -> expression!!.invoke().also { require(take() == ")") { "缺少右括號。" } }
            "-" -> atom().negate()
            "+" -> atom()
            else -> token.toBigDecimalOrNull() ?: variables[token] ?: error("找不到變數 $token。")
        }
    }
    fun product(): BigDecimal {
        var result = atom()
        while (peek() == "*" || peek() == "/") {
            val operation = take(); val next = atom()
            result = if (operation == "*") result.multiply(next, MathContext.DECIMAL128) else result.divide(next, MathContext.DECIMAL128)
        }
        return result
    }
    expression = {
        var result = product()
        while (peek() == "+" || peek() == "-") {
            val operation = take(); val next = product()
            result = if (operation == "+") result.add(next, MathContext.DECIMAL128) else result.subtract(next, MathContext.DECIMAL128)
        }
        result
    }
    return expression().also { require(index == tokens.size) { "請在數字與括號之間加入運算符號。" } }
}

internal fun isolatePreview(html: String, css: String, js: String, assets: List<PreviewAsset> = emptyList()): String {
    require(html.length + css.length + js.length <= 250_000) { "預覽內容最多 25 萬字元。" }
    require(assets.size <= 3) { "最多使用三張外部圖片。" }
    // Limit expansion before substituting asset bytes into user code.
    val assetUrls = assets.map(PreviewAsset::dataUrl)
    var projectedSize = html.length + css.length + js.length
    listOf(html, css, js).forEach { code ->
        Regex("\\{\\{asset([1-3])}}").findAll(code).forEach { match ->
            projectedSize += (assetUrls.getOrNull(match.groupValues[1].toInt() - 1)?.length ?: 0)
            require(projectedSize <= 2_000_000) { "圖片重複引用後超過預覽大小限制。" }
        }
    }
    val pageHtml = applyPreviewAssets(html, assetUrls)
    val pageCss = applyPreviewAssets(css, assetUrls)
    val pageJs = applyPreviewAssets(js, assetUrls)
    // HTML cannot inject in front of this CSP. The WebView independently blocks all network, file and content URLs.
    return """<!doctype html><html lang="zh-Hant"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:; base-uri 'none'; form-action 'none'; frame-src 'none'; connect-src 'none'; object-src 'none'">
        <style>${pageCss.replace(Regex("</style", RegexOption.IGNORE_CASE), "<\\\\/style")}</style></head>
        <body>$pageHtml<script>${pageJs.replace(Regex("</script", RegexOption.IGNORE_CASE), "<\\\\/script")}</script></body></html>
    """.trimIndent()
}

internal data class GroundedEvidence(val sourceId: String, val quote: String)

internal data class GroundedExcerpt(val text: String, val quoteOffset: Int)

/** Preserve nearby wording that may change the meaning of the selected quote. */
internal fun groundedExcerpt(source: String, quote: String): GroundedExcerpt? {
    if (quote.isBlank()) return null
    val offset = source.indexOf(quote).takeIf { it >= 0 } ?: return null
    val currentLine = source.lastIndexOf('\n', offset - 1)
    val start = source.lastIndexOf('\n', currentLine - 1) + 1
    val nextLine = source.indexOf('\n', offset + quote.length - 1)
    val end = if (nextLine >= 0) source.indexOf('\n', nextLine + 1).takeIf { it >= 0 } ?: source.length else source.length
    return GroundedExcerpt(source.substring(start, end), offset - start)
}

/** Only display exact excerpts. Model-written conclusions cannot be verified by a string match. */
internal fun verifiedGroundedEvidence(reply: String, sources: Map<String, String>): List<GroundedEvidence> {
    val content = reply.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val parsed = runCatching { Json.parseToJsonElement(content) }.getOrNull() as? JsonArray ?: return emptyList()
    fun JsonObject.text(name: String): String = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    return parsed.take(12).mapNotNull { row ->
        val item = row as? JsonObject ?: return@mapNotNull null
        val id = item.text("source").trim()
        val quote = item.text("quote").takeIf { it.isNotBlank() && it.length in 3..500 } ?: return@mapNotNull null
        if (sources[id]?.contains(quote) == true) GroundedEvidence(id, quote) else null
    }.distinct()
}

internal data class GroundedDocumentPassage(
    val section: String?,
    val firstLine: Int,
    val text: String,
) {
    fun location(quote: String = text): String {
        val offset = text.indexOf(quote).coerceAtLeast(0)
        val start = firstLine + text.take(offset).count { it == '\n' }
        val end = start + quote.trimEnd('\n', '\r').count { it == '\n' }
        val lines = if (start == end) "第 $start 行" else "第 $start–$end 行"
        return listOfNotNull(section, lines).joinToString(" · ")
    }
}

internal fun groundedDocumentPassages(name: String, text: String): List<GroundedDocumentPassage> {
    val fileType = name.substringAfterLast('.').lowercase()
    val markers = when (fileType) {
        "pdf" -> Regex("(?m)^---Page ([0-9]+):\\r?$")
        "pptx" -> Regex("(?m)^## Slide ([0-9]+)\\r?$")
        "md", "docx", "epub" -> Regex("(?m)^#{1,6} (.+?)\\r?$")
        else -> null
    }
    val passages = mutableListOf<GroundedDocumentPassage>()
    val lineOffsets = buildList {
        add(0)
        text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }
    fun addPassages(start: Int, end: Int, section: String?) {
        var cursor = start
        while (cursor < end) {
            var next = minOf(cursor + 1200, end)
            if (next < end) {
                val newline = text.lastIndexOf('\n', next - 1)
                if (newline > cursor + 600) next = newline + 1
                else if (text[next].isLowSurrogate()) next--
            }
            val lineIndex = lineOffsets.binarySearch(cursor).let { if (it >= 0) it else -it - 2 }
            val passage = text.substring(cursor, next)
            if (passage.isNotBlank()) passages += GroundedDocumentPassage(section, lineIndex + 1, passage)
            // Preserve text across chunk boundaries, including sentences split by a line break.
            if (next == end) break
            cursor = next - 500
            if (text[cursor].isLowSurrogate()) cursor--
        }
    }
    var start = 0
    var section: String? = null
    for (marker in markers?.findAll(text).orEmpty()) {
        addPassages(start, marker.range.first, section)
        val label = marker.groupValues[1].trim().take(100)
        section = when (fileType) {
            "pdf" -> "第 $label 頁"
            "pptx" -> "第 $label 張投影片"
            else -> label
        }
        start = if (fileType == "pdf" || fileType == "pptx") {
            (marker.range.last + 2).coerceAtMost(text.length)
        } else marker.range.first
    }
    val end = if (fileType == "pdf") {
        Regex("(?m)^\\[Truncated: showing [0-9]+ of [0-9]+ pages]\\r?$").find(text, start)?.range?.first ?: text.length
    } else text.length
    addPassages(start, end, section)
    return passages
}
