package me.rerere.zeroagent.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LabPreviewAssetsTest {
    @Test
    fun acceptsOnlyCredentialFreeHttpsImageTargets() {
        assertEquals("https://cdn.example.org/image.png", previewAssetUrl("https://cdn.example.org/image.png").toString())
        for (candidate in listOf("http://cdn.example.org/image.png", "https://cdn.example.org:8443/image.png",
            "https://user:secret@cdn.example.org/image.png", "https://cdn.example.org/image.png?key=secret",
            "https://cdn.example.org/image.png#fragment", "https://127.0.0.1/image.png",
            "https://169.254.169.254/image.png", "https://[::1]/image.png", "https://a.local/image.png",
            "https://cdn.example.org\\image.png", "https://cdn.example.org/image .png")) {
            assertNull(candidate, previewAssetUrl(candidate))
        }
    }

    @Test
    fun rejectsPrivateLocalAndSpecialPurposeDnsResults() {
        for (literal in listOf("127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.169.254", "192.0.2.1",
            "198.19.0.1", "198.51.100.1", "203.0.113.1", "240.0.0.1", "::1", "fc00::1", "2002::1", "2001:db8::1")) {
            assertFalse(literal, isPublicPreviewAddress(InetAddress.getByName(literal)))
        }
        assertTrue(isPublicPreviewAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(isPublicPreviewAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test
    fun onlyEmbedsValidatedRasterDataAndBoundsRepeatedReferences() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0)
        assertNotNull(previewImage(pngHeader, "image/png"))
        assertNull(previewImage(pngHeader, "image/jpeg"))
        assertNull(previewImage(pngHeader, "image/svg+xml"))
        val asset = checkNotNull(previewImage(pngHeader, "image/png"))
        val page = isolatePreview("<img src=\"{{asset1}}\">", "", "", listOf(asset))
        assertTrue(page.contains("src=\"data:image/png;base64,"))
        assertTrue(page.contains("connect-src 'none'"))
        assertFalse(page.contains("{{asset1}}"))
        assertThrows(IllegalArgumentException::class.java) {
            isolatePreview("{{asset1}}".repeat(12), "", "", listOf(PreviewAsset("image/png", ByteArray(PREVIEW_ASSET_LIMIT))))
        }
    }
}
