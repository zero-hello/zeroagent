package me.rerere.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderEndpointTest {
    @Test fun `complete endpoints and base URLs resolve consistently`() {
        listOf("https://example.com/v1", "https://example.com/v1/", "https://example.com/v1/chat/completions").forEach {
            assertEquals("https://example.com/v1/chat/completions", resolveProviderEndpoint(it, "/chat/completions"))
        }
        assertEquals("https://example.com/v1/responses", resolveProviderEndpoint("https://example.com/v1/chat/completions", "/responses"))
        assertEquals("https://example.com/v1/models?tenant=a", resolveProviderEndpoint("https://example.com/v1/messages?tenant=a", "/models"))
        assertEquals("https://example.com/v1/models", resolveProviderEndpoint("https://example.com/v1/models", "/models"))
        assertEquals("https://example.com/v2/chat", resolveProviderEndpoint("https://example.com/v1/models", "/v2/chat"))
    }
    @Test(expected = IllegalArgumentException::class) fun `URL credentials are refused`() {
        resolveProviderEndpoint("https://user:password@example.com/v1", "/messages")
    }
}
