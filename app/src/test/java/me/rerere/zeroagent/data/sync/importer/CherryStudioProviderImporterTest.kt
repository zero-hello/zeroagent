package me.rerere.zeroagent.data.sync.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CherryStudioProviderImporterTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `data entry is readable within its byte budget`() {
        val sample = "{\"localStorage\":{}}"
        val archive = temp.newFile("backup.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(sample.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        assertEquals(sample, CherryStudioProviderImporter.readDataJson(archive, sample.length))
        assertThrows(IllegalArgumentException::class.java) {
            CherryStudioProviderImporter.readDataJson(archive, sample.length - 1)
        }
    }
}
