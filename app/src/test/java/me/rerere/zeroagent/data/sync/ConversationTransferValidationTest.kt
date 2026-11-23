package me.rerere.zeroagent.data.sync

import kotlinx.coroutines.runBlocking
import me.rerere.zeroagent.data.lab.AcceptanceRow
import me.rerere.zeroagent.data.productivity.ChatProject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.uuid.Uuid

class ConversationTransferValidationTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun writesManifestWithUtf8ByteBudget() {
        val manifest = ConversationTransferArchive(format = "zeroagent-conversations-1", labPreferences = mapOf("example" to "測試"))
        val expected = me.rerere.zeroagent.utils.JsonInstant.encodeToString(ConversationTransferArchive.serializer(), manifest).encodeToByteArray()
        val exact = ByteArrayOutputStream()
        assertEquals(expected.size.toLong(), writePortableManifest(exact, manifest, expected.size.toLong()))
        assertArrayEquals(expected, exact.toByteArray())

        val short = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            writePortableManifest(short, manifest, expected.size.toLong() - 1)
        }
    }

    @Test
    fun validatesAndStagesExactBytes() {
        val bytes = "可攜對話資料".encodeToByteArray()
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("attachments/test.txt"))
            output.write(bytes)
            output.closeEntry()
        }
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("attachments/test.txt")!!
            val staged = ByteArrayOutputStream()
            val count = runBlocking { verifyPortableEntry(zip, entry, 1_000, staged) }
            assertEquals(bytes.size.toLong(), count)
            assertArrayEquals(bytes, staged.toByteArray())
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { verifyPortableEntry(zip, entry, bytes.size.toLong() - 1) }
            }
            val wrongChecksum = ZipEntry(entry).apply { crc = crc xor 1L }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { verifyPortableEntry(zip, wrongChecksum, 1_000) }
            }
        }
    }

    @Test
    fun acceptanceMergePreservesLocalRevisionAndAddsNewRows() {
        val local = AcceptanceRow(id = "same", requirement = "本機版本")
        val incomingRevision = local.copy(requirement = "匯入版本")
        val added = AcceptanceRow(id = "new", requirement = "新需求")
        assertEquals(listOf(local, added), mergeAcceptanceRows(listOf(local), listOf(incomingRevision, added)))
    }

    @Test
    fun detachedDraftOrProjectLinkIsRejected() {
        val included = Uuid.random().toString()
        val detached = Uuid.random().toString()
        val draftBackup = PortableLabData(draftTexts = mapOf(detached to "草稿"))
        val projectBackup = PortableLabData(projects = listOf(ChatProject(name = "測試", conversationIds = setOf(detached))))
        for (backup in listOf(draftBackup, projectBackup)) {
            assertThrows(IllegalArgumentException::class.java) { backup.validate(setOf(included)) }
            backup.validate(setOf(detached))
        }
    }

    @Test
    fun acceptanceRestoresOnlySafeWebLinks() {
        val row = AcceptanceRow(id = "1", requirement = "查驗結果")
        PortableLabData(acceptance = listOf(row.copy(sourceUrl = "https://example.test/report"))).validate(emptySet())
        assertThrows(IllegalArgumentException::class.java) {
            PortableLabData(acceptance = listOf(row.copy(sourceUrl = "file:///data/report"))).validate(emptySet())
        }
    }
}
