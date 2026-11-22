package me.rerere.zeroagent.data.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.uuid.Uuid

class PortableDraftTest {
    @Test
    fun attachmentOnlyDraftRemapsUrlsAndDropsProviderMetadata() = runBlocking {
        val key = "attachments/file.png"
        val original = PortableDraft("", listOf(UIMessagePart.Image(key, metadata = buildJsonObject { put("opaque", "hidden") })))
        val draft = Json.decodeFromString<PortableDraft>(Json.encodeToString(original))
        draft.validate()
        val restored = draft.remap {
            assertEquals(key, it)
            "file:///private/upload/restored.png"
        }.attachments.single() as UIMessagePart.Image
        assertEquals("file:///private/upload/restored.png", restored.url)
        assertNull(restored.metadata)
    }

    @Test
    fun olderTextOnlyDraftsRemainValidAndDuplicateChatDraftsAreRejected() {
        val id = Uuid.random().toString()
        val older = PortableLabData(draftTexts = mapOf(id to "舊草稿"))
        older.validate(setOf(id))
        val duplicate = older.copy(drafts = mapOf(id to PortableDraft("新草稿", emptyList())))
        assertThrows(IllegalArgumentException::class.java) { duplicate.validate(setOf(id)) }
        assertThrows(IllegalArgumentException::class.java) {
            PortableLabData(drafts = mapOf(id to PortableDraft("未選取的草稿", emptyList()))).validate(emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableDraft("文字", listOf(UIMessagePart.Text("並非檔案"))).validate()
        }
    }
}
