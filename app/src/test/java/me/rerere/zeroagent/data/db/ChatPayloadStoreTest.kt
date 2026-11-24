package me.rerere.zeroagent.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPayloadStoreTest {
    @Test
    fun metadataRetainsUsageOpenReasoningAndAttachmentsWithoutTheLargeContent() {
        val raw = """[{"role":"assistant","createdAt":"2026-11-24T01:02:03","usage":{"promptTokens":31},"parts":[{"finishedAt":null,"reasoning":"private reasoning"},{"output":[{"url":"file:///upload/one.png"}]}]}]"""
        val summary = summarizeChatPayload(raw)
        assertEquals(
            """[{"role":"assistant","createdAt":"2026-11-24T01:02:03","usage":{"promptTokens":31},"files":["file:///upload/one.png"],"finishedAt":null}]""",
            summary,
        )
        assertFalse(summary.contains("private reasoning"))
    }

    @Test
    fun fileNamesHaveStableChecksums() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            chatPayloadDigest("abc".encodeToByteArray()),
        )
        assertTrue(chatPayloadDigest("測試".encodeToByteArray()).matches(Regex("[a-f0-9]{64}")))
    }
}
