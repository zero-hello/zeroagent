package me.rerere.locallm

import org.junit.Assert.*
import org.junit.Test

class AbiCompatibilityTest {
    @Test fun `x86_64 process never becomes arm64`() {
        assertEquals("x86_64", AbiCompatibility.processAbi("x86_64", null, true))
        assertEquals("x86", AbiCompatibility.processAbi("x86_64", null, false))
    }
    @Test fun `selected APK native directory wins over host kernel under translation`() {
        assertEquals("arm64-v8a", AbiCompatibility.processAbi("x86_64", "/data/app/pkg/lib/arm64", true))
    }
    @Test fun `32 bit process on arm64 is kept distinct`() {
        assertEquals("armeabi-v7a", AbiCompatibility.processAbi("aarch64", null, false))
    }
    @Test fun `unknown process architecture remains unknown`() {
        assertNull(AbiCompatibility.processAbi("riscv64", null, true))
    }
}
