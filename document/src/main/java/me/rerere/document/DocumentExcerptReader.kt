package me.rerere.document

import com.artifex.mupdf.fitz.PDFDocument
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

/** Bounded attachment extraction for chat. Full document views use the original parsers. */
object DocumentExcerptReader {
    private const val MAX_PDF_PAGES = 24
    private const val MAX_ZIP_SECTIONS = 24
    private const val MAX_ZIP_ENTRIES = 16_000
    private const val MAX_ZIP_BYTES = 2 * 1024 * 1024
    private const val MAX_ZIP_ENTRY_BYTES = 512 * 1024
    private val slidePattern = Regex("ppt/slides/slide(\\d+)\\.xml")

    data class Result(val text: String, val truncated: Boolean, val inspected: String? = null)

    /** A null result indicates missing, invalid, or unsupported text, including image-only pages. */
    fun read(file: File, mime: String, characterLimit: Int, checkCancellation: () -> Unit = {}): Result? {
        require(characterLimit in 1..24_000)
        if (!file.isFile) return null
        val out = ExcerptBuffer(characterLimit)
        val inspected = when (mime.substringBefore(';').trim().lowercase()) {
            "application/pdf" -> readPdf(file, out, checkCancellation)
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                readZip(file, out) { archive ->
                    archive.read("word/document.xml", allowLargeEntry = true) { input ->
                        readDocx(input, out, checkCancellation)
                    }
                    null
                }
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                readZip(file, out) { readSlides(it, out, checkCancellation) }
            "application/epub+zip" -> readZip(file, out) { readEpub(it, out, checkCancellation) }
            else -> {
                file.bufferedReader().use { reader ->
                    val buffer = CharArray(minOf(2048, characterLimit + 1))
                    while (!out.truncated) {
                        checkCancellation()
                        val count = reader.read(buffer, 0, minOf(buffer.size, out.remaining + 1))
                        if (count <= 0) break
                        out.add(String(buffer, 0, count))
                    }
                }
                null
            }
        }
        val text = out.text.toString().trimEnd()
        return if (text.isBlank()) null else Result(text, out.truncated, inspected)
    }

    private fun readPdf(file: File, out: ExcerptBuffer, checkCancellation: () -> Unit): String {
        val document = PDFDocument.openDocument(file.absolutePath).asPDF()
        try {
            val pageCount = document.countPages()
            var inspected = 0
            for (i in 0 until minOf(pageCount, MAX_PDF_PAGES)) {
                checkCancellation()
                if (out.remaining == 0) break
                val page = document.loadPage(i)
                try {
                    val structured = page.toStructuredText()
                    try {
                        val text = structured.asText()
                        if (text.isNotBlank()) out.add("第 ${i + 1} 頁\n$text\n\n")
                    } finally {
                        structured.destroy()
                    }
                } finally {
                    page.destroy()
                }
                inspected++
                if (out.truncated) break
            }
            if (inspected < pageCount) out.truncated = true
            return "已讀取 $inspected／$pageCount 頁"
        } finally {
            document.destroy()
        }
    }

    private fun readZip(file: File, out: ExcerptBuffer, read: (BoundedArchive) -> String?): String? =
        ZipFile(file).use { zip ->
            require(zip.size() <= MAX_ZIP_ENTRIES) { "Too many archive entries" }
            try {
                read(BoundedArchive(zip))
            } catch (_: ExtractionLimit) {
                out.truncated = true
                null
            }
        }

    private fun readDocx(input: InputStream, out: ExcerptBuffer, checkCancellation: () -> Unit) {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(input, null)
        var textDepth = -1
        var visited = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (++visited % 256 == 0) checkCancellation()
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "t" -> textDepth = parser.depth
                    "br", "cr" -> out.add("\n")
                    "tab" -> out.add("\t")
                }
                XmlPullParser.TEXT -> if (textDepth > 0) out.add(parser.text.orEmpty())
                XmlPullParser.END_TAG -> {
                    if (parser.depth == textDepth) textDepth = -1
                    if (parser.name == "p" || parser.name == "tr") out.add("\n")
                }
            }
            if (out.truncated) break
            parser.next()
        }
    }

    private fun readSlides(archive: BoundedArchive, out: ExcerptBuffer, checkCancellation: () -> Unit): String {
        val slides = archive.zip.entries().asSequence().mapNotNull { entry ->
            slidePattern.matchEntire(entry.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to entry.name }
        }.sortedBy { it.first }.toList()
        var inspected = 0
        for ((number, name) in slides.take(MAX_ZIP_SECTIONS)) {
            checkCancellation()
            if (out.remaining == 0) break
            val slide = archive.read(name, parse = PptxParser::parseSlideXml)
            if (slide.isNotBlank()) {
                out.add("第 $number 張投影片\n")
                out.addParsed(slide)
            }
            val notes = "ppt/notesSlides/notesSlide$number.xml"
            if (out.remaining > 0 && archive.zip.getEntry(notes) != null) {
                val text = archive.read(notes) { PptxParser.parseNotesXml(it, strict = true) }
                if (text.isNotBlank()) {
                    out.add("講者備註\n")
                    out.addParsed(text)
                }
            }
            inspected++
            if (out.truncated) break
        }
        if (inspected < slides.size) out.truncated = true
        return "已讀取 $inspected／${slides.size} 張投影片"
    }

    private fun readEpub(archive: BoundedArchive, out: ExcerptBuffer, checkCancellation: () -> Unit): String {
        val packagePath = archive.read("META-INF/container.xml") { input ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(input, null)
            var path: String? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "rootfile") {
                    path = parser.getAttributeValue(null, "full-path")
                    break
                }
                parser.next()
            }
            requireNotNull(path) { "Missing EPUB package path" }
        }
        val (manifest, spine) = archive.read(packagePath, parse = EpubParser::parseOpf)
        val sections = spine.mapNotNull { id ->
            manifest[id]?.takeIf { it.mediaType.contains("html", ignoreCase = true) }
        }
        var inspected = 0
        for (section in sections.take(MAX_ZIP_SECTIONS)) {
            checkCancellation()
            if (out.remaining == 0) break
            val path = java.net.URI(null, null, packagePath, null)
                .resolve(section.href).normalize().path.trimStart('/')
            if (archive.zip.getEntry(path) == null) {
                out.truncated = true
                continue
            }
            out.addParsed(archive.read(path) { EpubParser.parseXhtml(it, strict = true) })
            inspected++
            if (out.remaining == 0) break
        }
        if (inspected < sections.size) out.truncated = true
        return "已讀取 $inspected／${sections.size} 個章節"
    }

    private class ExcerptBuffer(private val limit: Int) {
        val text = StringBuilder()
        var truncated = false
        val remaining get() = limit - text.length

        fun add(value: String) {
            if (value.length > remaining) truncated = true
            var end = minOf(value.length, remaining)
            if (end < value.length && end > 0 && value[end - 1].isHighSurrogate()) end--
            text.append(value, 0, end)
        }

        fun addParsed(value: String) {
            if (value.startsWith("Error parsing ")) throw IOException("Invalid document XML")
            if (value.isNotBlank()) {
                add(value.trimEnd())
                // Separators are presentation, not content trimmed by the budget.
                if (remaining >= 2) add("\n\n")
            }
        }
    }

    private class ExtractionLimit : IOException("Document extraction limit reached")

    private class BoundedArchive(val zip: ZipFile) {
        private var totalRemaining = MAX_ZIP_BYTES

        fun <T> read(path: String, allowLargeEntry: Boolean = false, parse: (InputStream) -> T): T {
            val entry = zip.getEntry(path) ?: throw IOException("Missing required document section")
            val limit = minOf(totalRemaining, MAX_ZIP_ENTRY_BYTES)
            if (limit <= 0 || (!allowLargeEntry && entry.size > limit)) throw ExtractionLimit()
            var remaining = limit
            var hitLimit = false
            val result = zip.getInputStream(entry).use { source ->
                val bounded = object : FilterInputStream(source) {
                    override fun read(): Int {
                        if (remaining == 0) return checkEof()
                        return super.read().also { if (it >= 0) account(1) }
                    }

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (length == 0) return 0
                        if (remaining == 0) return checkEof()
                        return super.read(buffer, offset, minOf(length, remaining)).also { if (it > 0) account(it) }
                    }

                    override fun skip(count: Long): Long = super.skip(count.coerceIn(0, remaining.toLong()))
                        .also { account(it.toInt()) }

                    private fun checkEof(): Int {
                        if (super.read() == -1) return -1
                        hitLimit = true
                        throw ExtractionLimit()
                    }

                    private fun account(count: Int) {
                        remaining -= count
                        totalRemaining -= count
                    }
                }
                parse(bounded)
            }
            // Legacy parsers may convert reader failures into an error string.
            if (hitLimit) throw ExtractionLimit()
            return result
        }
    }
}
