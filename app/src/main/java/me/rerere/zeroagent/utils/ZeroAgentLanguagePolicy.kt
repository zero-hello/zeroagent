package me.rerere.zeroagent.utils

import java.util.Locale

object ZeroAgentLanguagePolicy {
    val supportedTags = listOf("", "en", "zh-TW", "zh-CN", "ja", "ko", "ru", "ar")

    /** Accept platform language lists and regional/script variants, retaining our resource tags. */
    fun normalize(tag: String): String {
        for (entry in tag.split(',')) {
            val candidate = entry.trim().replace('_', '-')
            supportedTags.firstOrNull { it.isNotEmpty() && it.equals(candidate, ignoreCase = true) }
                ?.let { return it }
            val locale = Locale.forLanguageTag(candidate)
            when (locale.language) {
                "zh" -> return if (locale.script.equals("Hant", true) || locale.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
                "en", "ja", "ko", "ru", "ar" -> return locale.language
            }
        }
        return ""
    }
}
