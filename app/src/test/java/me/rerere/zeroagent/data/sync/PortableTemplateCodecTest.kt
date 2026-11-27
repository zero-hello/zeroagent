package me.rerere.zeroagent.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PortableTemplateCodecTest {
    @Test fun `Traditional Chinese round trips in QR safe ASCII`() {
        val template = PortableTemplate(PortableTemplate.Kind.PROMPT, "寫作助手", prompt = "使用繁體中文。\n把段落分清楚。")
        val code = PortableTemplateCodec.encode(template)
        assertTrue(code.all { it.code < 128 })
        assertEquals(template, PortableTemplateCodec.decode(code))
    }

    @Test fun `provider protocol and custom path survive round trip`() {
        val template = PortableTemplate(
            PortableTemplate.Kind.PROVIDER, "Sample", PortableTemplate.Format.OPENAI_RESPONSES,
            "https://example.test/api", "/v2/responses", listOf("demo/text"),
        )
        assertEquals(template, PortableTemplateCodec.decode(PortableTemplateCodec.encode(template)))
    }

    @Test fun `endpoint cannot carry credentials or unsupported URL schemes`() {
        for (endpoint in listOf(
            "https://user:pass@example.test/mcp", "https://example.test/mcp?token=fake", "https://example.test/mcp#fake",
            "http://example.test/mcp", "file:///example", "https://example.test/%66ake",
        )) assertThrows(IllegalArgumentException::class.java) {
            PortableTemplateCodec.encode(PortableTemplate(PortableTemplate.Kind.MCP, "Sample", PortableTemplate.Format.MCP_HTTP, endpoint))
        }
    }

    @Test fun `import rejects fields beyond allowlist`() {
        val raw = """{"kind":"MCP","name":"Sample","format":"MCP_HTTP","endpoint":"https://example.test","headers":{"Authorization":"fixture"}}"""
        val wire = "ZA1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        assertThrows(IllegalArgumentException::class.java) { PortableTemplateCodec.decode(wire) }
    }

    @Test fun `prompt with credential patterns or saved credential is rejected`() {
        val template = PortableTemplate(PortableTemplate.Kind.PROMPT, "Sample", prompt = "Use fixture-credential-value")
        assertThrows(IllegalArgumentException::class.java) {
            PortableTemplateCodec.encode(template, knownSecrets = listOf("fixture-credential-value"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableTemplateCodec.encode(template.copy(prompt = "Authorization: Bearer not-a-real-credential"))
        }
    }

    @Test fun `oversized and unsupported versions are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PortableTemplateCodec.decode("ZA1:" + "a".repeat(1_901)) }
        assertThrows(IllegalArgumentException::class.java) { PortableTemplateCodec.decode("ZA9:abc") }
    }
}
