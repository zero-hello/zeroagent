package me.rerere.zeroagent.data.lab

internal data class SensitiveSpan(val range: IntRange, val type: String)

internal object LocalRedaction {
    private val rules = listOf(
        "電子郵件" to Regex("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b"),
        "憑證" to Regex("(?i)\\b(?:gh[pousr]_|github_pat_|sk-(?:proj-|ant-)?)[a-z0-9_-]{12,}\\b|\\bBearer\\s+[a-z0-9_.-]{12,}"),
        "憑證欄位" to Regex("(?im)\\b(?:api[_ -]?key|access[_ -]?token|password|secret)\\s*[=:]\\s*[^\\s,;]{6,}"),
        "電話" to Regex("(?<![\\p{N}/-])(?:\\+\\d{1,3}[ -]?)?\\(?0?\\d{1,4}\\)?[ -]?\\d{3,4}[ -]?\\d{3,4}(?![\\p{N}/-])"),
        "可能的地址" to Regex("[\\p{IsHan}]{2,10}(?:市|縣)[\\p{IsHan}0-9]{2,24}(?:路|街|巷|段)[\\p{IsHan}0-9之-]{0,15}號"),
    )

    fun find(text: String): List<SensitiveSpan> {
        val candidates = rules.flatMap { (label, pattern) ->
            pattern.findAll(text).map { SensitiveSpan(it.range, label) }.toList()
        }.sortedWith(compareBy<SensitiveSpan> { it.range.first }.thenByDescending { it.range.last })
        var last = -1
        return candidates.filter { hit ->
            if (hit.range.first > last) {
                last = hit.range.last
                true
            } else false
        }
    }

    fun mask(text: String, spans: List<SensitiveSpan>): String {
        return StringBuilder(text).apply {
            spans.sortedByDescending { it.range.first }.forEach { hit ->
                replace(hit.range.first, hit.range.last + 1, "〔${hit.type}已遮蔽〕")
            }
        }.toString()
    }
}
