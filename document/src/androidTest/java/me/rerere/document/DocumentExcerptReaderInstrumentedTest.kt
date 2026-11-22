package me.rerere.document

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocumentExcerptReaderInstrumentedTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun oversizedCompressedDocxEntryReturnsOnlyAnExcerpt() {
        val file = temporary.newFile("large.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("<document><body>".toByteArray())
            repeat(24_000) { zip.write("<p><r><t>abc 123</t></r></p>".toByteArray()) }
            zip.write("</body></document>".toByteArray())
            zip.closeEntry()
        }
        val result = requireNotNull(DocumentExcerptReader.read(
            file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 100,
        ))
        assertTrue(result.truncated)
        assertTrue(result.text.startsWith("abc 123"))
        assertTrue(result.text.length <= 100)
    }
}
