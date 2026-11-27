package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WorldBankToolsTest {
    @Test fun `every selected indicator has a unique callable name, real code, and approval requirement`() = runBlocking {
        val rows = sequenceOf("src/main/assets", "app/src/main/assets")
            .map { File(it, "world_bank_indicators.tsv") }.first(File::isFile).readLines()
        val expected = rows.map { it.substringBefore('\t') }.toSet()
        val requested = mutableSetOf<String>()
        val tools = WorldBankTools(readCatalog = { rows }, retrieve = { request ->
            assertEquals("api.worldbank.org", request.host)
            assertEquals("https", request.scheme)
            val id = request.pathSegments.last()
            requested += id
            """[{"sourceid":"2","lastupdated":"2026-11-11"},[{"indicator":{"id":"$id"},"date":"2022","value":42}]]"""
        }).entries
        assertEquals(945, tools.size)
        assertEquals(tools.size, tools.map { it.name }.toSet().size)
        assertEquals(1_006, tools.size + PersonalUtilityTools.names.size)
        val input = Json.parseToJsonElement("""{"countries":["BRA"],"start_year":2022,"end_year":2022}""")
        for (entry in tools) {
            assertTrue(entry.tool.needsApproval(input))
            val output = entry.tool.execute(input).single() as UIMessagePart.Text
            val rowsOut = Json.parseToJsonElement(output.text).jsonObject["observations"] as JsonArray
            assertEquals("42", rowsOut.single().jsonObject.getValue("value").jsonPrimitive.content)
        }
        assertEquals(expected, requested)
    }

    @Test fun `query rejects untrusted strings and invalid ranges`() {
        for (input in listOf(
            """{"countries":["https://other.example"]}""",
            """{"countries":["AAA"],"token":"test-only"}""",
            """{"countries":["JPN"],"start_year":2010}""",
            """{"countries":["JPN"],"start_year":1990,"end_year":2026}""",
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                worldBankRequest("SP.POP.TOTL", Json.parseToJsonElement(input), 2026)
            }
        }
    }
}
