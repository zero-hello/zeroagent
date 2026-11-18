package me.rerere.zeroagent.data.productivity

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class ConversationSearchFilter(
    val text: String,
    val after: Instant? = null,
    val before: Instant? = null,
    val model: String? = null,
    val type: String? = null,
    val tag: String? = null,
) {
    companion object {
        fun parse(query: String): ConversationSearchFilter {
            val values = mutableMapOf<String, String>()
            val text = Regex("(after|before|model|type|tag):(?:\"([^\"]+)\"|(\\S+))", RegexOption.IGNORE_CASE)
                .replace(query) { match ->
                    values[match.groupValues[1].lowercase()] = match.groupValues[2].ifBlank { match.groupValues[3] }
                    " "
                }.trim()
            fun date(key: String): Instant? = values[key]?.let {
                require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(it)) { "日期格式請使用 YYYY-MM-DD。" }
                LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC)
            }
            val type = values["type"]?.lowercase()
            require(type == null || type in setOf("image", "document", "audio", "video", "text")) {
                "附件類型請使用 image、document、audio、video 或 text。"
            }
            return ConversationSearchFilter(text, date("after"), date("before"), values["model"], type, values["tag"])
        }
    }
}
