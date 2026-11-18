package me.rerere.search

import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class PublicSearchProtocolTest {
    @Test fun serpApisUseTheirActualEndpointsAndAuthentication() {
        val serp = PublicSearchProtocol.request(PublicSearchApi.SERPAPI, "台灣 & weather", 20, "fixture-only")
        assertEquals("serpapi.com", serp.url.host)
        assertEquals("台灣 & weather", serp.url.queryParameter("q"))
        assertEquals("fixture-only", serp.url.queryParameter("api_key"))
        assertNull(serp.url.queryParameter("num"))
        val search = PublicSearchProtocol.request(PublicSearchApi.SEARCHAPI, "weather", 20, "fixture-only")
        assertEquals("www.searchapi.io", search.url.host)
        assertEquals("Bearer fixture-only", search.header("Authorization"))
        assertNull(search.url.queryParameter("api_key"))
        assertNull(search.url.queryParameter("num"))
    }

    @Test fun youUsesCurrentPostProtocolWithoutOptionalPaidExtraction() {
        val request = PublicSearchProtocol.request(PublicSearchApi.YOU, "news", 500, "fixture-only")
        assertEquals("https://ydc-index.io/v1/search", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("fixture-only", request.header("X-API-Key"))
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertEquals("{\"query\":\"news\",\"count\":20}", body)
    }

    @Test fun organicResultsAreValidatedDeduplicatedAndLimited() {
        val response = """{"organic_results":[{"title":"A","link":"https://a.example","snippet":"one"},{"title":"duplicate","link":"https://a.example"},{"title":"bad","link":"javascript:alert(1)"},{"title":"B","link":"https://b.example"}]}"""
        val result = PublicSearchProtocol.parse(PublicSearchApi.SERPAPI, response, 1)
        assertEquals(listOf("A"), result.items.map { it.title })
        assertEquals("one", result.items.single().text)
    }

    @Test fun youKeepsNewsPublicationDatesWithoutInventingWebPublicationDates() {
        val response = """{"results":{"web":[{"title":"Web","url":"https://a.example","snippets":["one","two"],"page_age":"2026-10-16"}],"news":[{"title":"News","url":"https://b.example","description":"news","page_age":"2026-10-17T01:00:00Z"}]}}"""
        val result = PublicSearchProtocol.parse(PublicSearchApi.YOU, response, 10)
        assertEquals("one\ntwo", result.items[0].text)
        assertNull(result.items[0].publishedDate)
        assertEquals("2026-10-17T01:00:00Z", result.items[1].publishedDate)
    }

    @Test fun errorsAndMissingSchemaCannotBecomeFakeEmptySuccess() {
        listOf("{}", "{\"error\":\"invalid key\"}").forEach {
            assertTrue(runCatching { PublicSearchProtocol.parse(PublicSearchApi.SERPAPI, it, 10) }.isFailure)
        }
        assertTrue(runCatching { PublicSearchProtocol.request(PublicSearchApi.YOU, "query", 10, "") }.isFailure)
        assertTrue(PublicSearchProtocol.parse(PublicSearchApi.YOU, "{\"results\":{\"web\":[]}}", 10).items.isEmpty())
    }
}
