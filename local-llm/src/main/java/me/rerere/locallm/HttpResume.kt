package me.rerere.locallm

/** Only append a response that proves both its offset and representation identity. */
object HttpResume {
    data class Range(val start: Long, val end: Long, val total: Long)

    fun parseRange(header: String?): Range? {
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header.orEmpty()) ?: return null
        val (start, end, total) = match.destructured
        val s = start.toLongOrNull() ?: return null
        val e = end.toLongOrNull() ?: return null
        val t = total.toLongOrNull() ?: return null
        return Range(s, e, t).takeIf { s <= e && e < t }
    }

    fun validator(etag: String?, lastModified: String?): String? =
        etag?.takeIf { it.startsWith('"') && it.endsWith('"') }
            ?: lastModified?.takeIf { it.isNotBlank() }

    fun canAppend(offset: Long, range: Range?, savedValidator: String?, responseValidator: String?): Boolean =
        offset > 0 && range?.start == offset && savedValidator != null && savedValidator == responseValidator
}
