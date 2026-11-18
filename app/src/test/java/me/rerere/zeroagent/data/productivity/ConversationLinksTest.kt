package me.rerere.zeroagent.data.productivity

import me.rerere.zeroagent.data.sync.s3.AwsSignatureV4
import me.rerere.zeroagent.data.sync.s3.S3Config
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ConversationLinksTest {
    // Public AWS S3 query-signing example (2013-05-24), also computed independently with Python hmac.
    private val example = S3Config(endpoint = "https://s3.amazonaws.com", bucket = "examplebucket",
        region = "us-east-1", pathStyle = false, accessKeyId = "AKIAIOSFODNN7EXAMPLE",
        secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")

    @Test fun `S3 GET presigning matches the published AWS example`() {
        val signed = AwsSignatureV4.presignGet(example, "test.txt", 86400, Instant.parse("2013-05-24T00:00:00Z"))
        assertTrue(signed.startsWith("https://examplebucket.s3.amazonaws.com/test.txt?"))
        assertTrue(signed.endsWith("X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404"))
        assertFalse(signed.contains(example.secretAccessKey))
    }

    @Test fun `signing encodes object paths and preserves path style bucket and port`() {
        val config = example.copy(endpoint = "https://objects.example.com:9000", pathStyle = true)
        val signed = AwsSignatureV4.presignGet(config, "folder/a b+中文.html", 900, Instant.EPOCH)
        assertTrue(signed.startsWith("https://objects.example.com:9000/examplebucket/folder/a%20b%2B%E4%B8%AD%E6%96%87.html?"))
    }

    @Test fun `share generation cannot extend the originally chosen expiry`() {
        val config = ConversationLinks.checkedConfig(example)
        val record = ConversationLink(title = "test", endpoint = config.endpoint, bucket = config.bucket,
            region = config.region, pathStyle = config.pathStyle, createdAt = 0, expiresAt = 3600)
        assertTrue(ConversationLinks.url(config, record, Instant.ofEpochSecond(3000)).contains("X-Amz-Expires=600"))
        assertThrows(IllegalArgumentException::class.java) { ConversationLinks.url(config, record, Instant.ofEpochSecond(3600)) }
        assertThrows(IllegalArgumentException::class.java) { ConversationLinks.url(config.copy(bucket = "otherbucket"), record, Instant.ofEpochSecond(3000)) }
    }

    @Test fun `unsafe endpoint and invalid expiry are rejected before networking`() {
        for (endpoint in listOf("http://s3.amazonaws.com", "https://s3.amazonaws.com/path", "https://user:password@s3.amazonaws.com", "https://s3.amazonaws.com?key=x")) {
            assertThrows(IllegalArgumentException::class.java) { ConversationLinks.checkedConfig(example.copy(endpoint = endpoint)) }
        }
        for (expires in listOf(0, 604801)) {
            assertThrows(IllegalArgumentException::class.java) { AwsSignatureV4.presignGet(example, "file", expires) }
        }
    }

    @Test fun `shared HTML renders hostile message content as literal text`() {
        val content = ConversationLinks.html("</title><script>alert(1)</script>", "<img src=x onerror=alert(2)>").toString(Charsets.UTF_8)
        assertFalse(content.contains("<script>"))
        assertFalse(content.contains("<img"))
        assertTrue(content.contains("&lt;img"))
        assertTrue(content.contains("default-src 'none'"))
    }
}
