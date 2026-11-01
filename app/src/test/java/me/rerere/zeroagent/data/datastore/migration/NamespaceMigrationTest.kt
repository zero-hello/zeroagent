package me.rerere.zeroagent.data.datastore.migration

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*
import me.rerere.zeroagent.utils.JsonInstant

class NamespaceMigrationTest {
    @Test fun convertsNestedLegacyTypesWithoutRewritingUserContent() {
        val raw = """{"avatar":{"type":"me.rerere.rikkahub.data.model.Avatar.Emoji","content":"me.rerere.rikkahub. hello"},"items":[{"type":"me.rerere.rikkahub.Example"}],"prompt":"rikkahub","type":"third.party.Type"}"""
        val result = JsonInstant.parseToJsonElement(migrateNamespaceJson(raw)).jsonObject
        assertEquals("me.rerere.zeroagent.data.model.Avatar.Emoji", result["avatar"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("me.rerere.rikkahub. hello", result["avatar"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("third.party.Type", result["type"]!!.jsonPrimitive.content)
        assertEquals("rikkahub", result["prompt"]!!.jsonPrimitive.content)
        assertEquals("me.rerere.zeroagent.Example", result["items"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
    }
    @Test fun preservesInvalidOrUnrelatedPayloads() {
        val raw = "not JSON me.rerere.rikkahub."
        assertEquals(raw, migrateNamespaceJson(raw))
        assertEquals("{}", migrateNamespaceJson("{}"))
        val text = """{"prompt":"me.rerere.rikkahub.UserText"}"""
        assertEquals(text, migrateNamespaceJson(text))
    }
}
