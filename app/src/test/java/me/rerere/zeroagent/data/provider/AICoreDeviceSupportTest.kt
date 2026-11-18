package me.rerere.zeroagent.data.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AICoreDeviceSupportTest {
    @Test fun acceptsPixel8AndNewerPhoneNames() {
        listOf("Pixel 8", "Pixel 8 Pro", "Pixel 8a", "Pixel 9", "Pixel 9a", "Pixel 9 Pro XL", "Pixel 9 Pro Fold", "Pixel 10 Pro")
            .forEach { assertTrue(it, supportsPixelAICoreDevice("Google", it)) }
    }

    @Test fun rejectsOlderUnknownAndPartialNames() {
        listOf("Pixel 7 Pro", "Pixel 7a", "Pixel Fold", "Pixel Tablet", "Pixel 8 Tablet", "Pixel 8 unofficial", "Google Pixel 8", "Pixel 8 Pro XL extra", "Pixel", "")
            .forEach { assertFalse(it, supportsPixelAICoreDevice("Google", it)) }
        assertFalse(supportsPixelAICoreDevice("Samsung", "Pixel 8"))
        assertFalse(supportsPixelAICoreDevice(null, "Pixel 8"))
        assertFalse(supportsPixelAICoreDevice("Google", null))
    }
}
