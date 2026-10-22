package me.rerere.zeroagent.data.permissions

internal object PermissionLabelResolution {
    fun label(curated: String?, platform: String?, fallback: String): String =
        curated?.trim()?.takeIf { it.isNotEmpty() }
            ?: platform?.trim()?.takeIf { it.isNotEmpty() && !(it.contains('.') && it.none(Char::isWhitespace)) }
            ?: fallback

    fun description(curated: String?, platform: String?, fallback: String): String =
        curated?.trim()?.takeIf { it.isNotEmpty() }
            ?: platform?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallback
}
