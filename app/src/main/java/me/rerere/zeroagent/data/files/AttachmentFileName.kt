package me.rerere.zeroagent.data.files

/** Untrusted attachment labels are basenames, never storage paths. */
internal fun sanitizeAttachmentFilename(raw: String?, fileId: String, fallbackExt: String): String {
    var name = raw.orEmpty().replace('\\', '/').substringAfterLast('/').trimStart('.')
        .filterNot(Char::isISOControl).trim()
    if (name.length > 200) {
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0) "." + name.substring(dot + 1).take(32) else ""
        name = (if (dot > 0) name.substring(0, dot) else name).take(200 - extension.length) + extension
    }
    if (name.isNotEmpty()) return name
    val extension = fallbackExt.trimStart('.').takeIf { it.matches(Regex("[A-Za-z0-9]{1,16}")) } ?: "bin"
    val suffix = fileId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.takeLast(8).ifEmpty { "file" }
    return "attachment-${System.currentTimeMillis()}-$suffix.$extension"
}
