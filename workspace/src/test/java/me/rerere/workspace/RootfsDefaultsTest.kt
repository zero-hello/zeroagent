package me.rerere.workspace

import org.junit.Assert.*
import org.junit.Test

class RootfsDefaultsTest {
    @Test fun `x86_64 kernel selects amd64 even when APK is translated arm64`() {
        assertEquals("amd64", RootfsDefaults.archFor("x86_64", listOf("arm64-v8a", "x86_64")))
        assertTrue(RootfsDefaults.downloadUrl("x86_64", listOf("arm64-v8a"))!!.endsWith("-amd64.tar.gz"))
    }
    @Test fun `arm64 kernel selects arm64`() {
        assertEquals("arm64", RootfsDefaults.archFor("aarch64", listOf("arm64-v8a")))
    }
    @Test fun `arm32 kernel selects armhf`() {
        assertEquals("armhf", RootfsDefaults.archFor("armv8l", listOf("armeabi-v7a")))
    }
    @Test fun `missing kernel hint falls back to primary device ABI only`() {
        assertEquals("amd64", RootfsDefaults.archFor(null, listOf("x86_64", "arm64-v8a")))
        assertNull(RootfsDefaults.archFor(null, listOf("x86", "arm64-v8a")))
    }
    @Test fun `unsupported known kernel cannot fall back to translated ABI`() {
        assertNull(RootfsDefaults.downloadUrl("i686", listOf("arm64-v8a")))
        assertNull(RootfsDefaults.downloadUrl("riscv64", listOf("arm64-v8a")))
        assertNull(RootfsDefaults.downloadUrl(null, emptyList()))
    }
}
