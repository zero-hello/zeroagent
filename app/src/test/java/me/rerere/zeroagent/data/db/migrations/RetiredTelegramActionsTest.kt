package me.rerere.zeroagent.data.db.migrations

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RetiredTelegramActionsTest {
    @Test fun directActionsKeepOtherToolsButScrubRetiredArguments() {
        val original = """[{"tool":"telegram_set_token","args":{"token":"fixture-only"}},{"tool":"post_notification","args":{"title":"keep"}}]"""
        val retired = retireTelegramActions(original)!!
        val actions = Json.parseToJsonElement(retired).jsonArray
        assertEquals("{}", actions[0].jsonObject["args"].toString())
        assertEquals("keep", actions[1].jsonObject["args"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertFalse(retired.contains("fixture-only"))
    }
    @Test fun workflowsAreDisabledWithoutLosingTriggersOrOtherActions() {
        val original = """{"name":"keep","enabled":true,"trigger":{"type":"manual"},"actions":[{"tool":"telegram_send_message","args":{"chat_id":123}},{"tool":"post_notification","args":{}}]}"""
        val retired = Json.parseToJsonElement(retireTelegramActions(original)!!).jsonObject
        assertFalse(retired["enabled"]!!.jsonPrimitive.boolean)
        assertEquals("manual", retired["trigger"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(2, retired["actions"]!!.jsonArray.size)
    }
    @Test fun unrelatedAndMalformedDataIsPreserved() {
        assertNull(retireTelegramActions("not-json"))
        assertNull(retireTelegramActions("""[{"tool":"post_notification","args":{"text":"telegram_send_message"}}]"""))
    }
}
