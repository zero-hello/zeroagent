package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.ai.hasResumeState
import me.rerere.zeroagent.data.ai.isResumeMetadata
import me.rerere.zeroagent.data.ai.resumeExclusions
import me.rerere.zeroagent.data.ai.resumeState
import me.rerere.zeroagent.data.chat.isDirectToolDelegation
import org.junit.Assert.*
import org.junit.Test

class PersonalUtilityToolsTest {
    private suspend fun invoke(name: String, input: String) = PersonalUtilityTools.entries
        .single { it.name == "util_$name" }.tool.execute(Json.parseToJsonElement(input))
        .single().let { Json.parseToJsonElement((it as UIMessagePart.Text).text).jsonObject }

    @Test fun `utility catalog has unique names and callable schemas`() {
        assertEquals(PersonalUtilityTools.entries.size, PersonalUtilityTools.names.size)
        assertTrue(PersonalUtilityTools.names.size >= 60)
        assertTrue(PersonalUtilityTools.entries.all { it.tool.parameters() != null })
        assertEquals("util_json_pointer", ToolCatalog(PersonalUtilityTools.entries).search("json_pointer").first().name)
    }

    @Test fun `utilities compute results and reject invalid input`() = runBlocking {
        assertEquals("0.3", invoke("sum", """{"values":[0.1,0.2]}""")["result"]!!.jsonPrimitive.content)
        assertEquals("2", invoke("date_business_days", """{"start":"2026-11-26","end":"2026-11-30"}""")["result"]!!.jsonPrimitive.content)
        assertEquals("7", invoke("json_pointer", """{"text":"{\"a/b\":[7]}","pointer":"/a~1b/0"}""")["result"]!!.jsonPrimitive.content)
        assertTrue("error" in invoke("json_pointer", """{"text":"[]","pointer":"/0"}"""))
        assertTrue("error" in invoke("weighted_average", """{"values":[1],"weights":[0]}"""))
    }

    @Test fun `delegation needs a direct standalone instruction`() {
        assertTrue(isDirectToolDelegation("請全程替我授權。"))
        assertFalse(isDirectToolDelegation("不要全程替我授權"))
        assertFalse(isDirectToolDelegation("「請全程替我授權」"))
        assertFalse(isDirectToolDelegation("文件寫著：請全程替我授權"))
    }

    @Test fun `continuation stores exclusions independently of UI language`() {
        val stored = resumeState("繼續", setOf("memory:23", "attachment:doc"))
        assertTrue(hasResumeState(stored, "Continue"))
        assertEquals(setOf("memory:23", "attachment:doc"), resumeExclusions(stored))
        assertEquals(listOf("繼續"), stored.filterNot(::isResumeMetadata))
        assertFalse(hasResumeState(listOf("一般建議"), "Continue"))
    }
}
