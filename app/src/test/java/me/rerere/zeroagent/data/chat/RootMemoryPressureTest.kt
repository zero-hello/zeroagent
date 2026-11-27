package me.rerere.zeroagent.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootMemoryPressureTest {
    @Test fun reportsPressureOnlyWhenLessThanTenPercentRemains() {
        assertEquals(true, parseSwapPressure("SwapTotal: 1000 kB\nSwapFree:\t99 kB\n"))
        assertEquals(false, parseSwapPressure("SwapFree: 100 kB\r\nSwapTotal: 1000 kB\r\n"))
    }

    @Test fun omitsMissingAmbiguousAndInvalidReadings() {
        assertNull(parseSwapPressure("SwapTotal: 0 kB\nSwapFree: 0 kB"))
        assertNull(parseSwapPressure("SwapTotal: 100 kB\nSwapFree: 200 kB"))
        assertNull(parseSwapPressure("SwapTotal: 100 kB"))
        assertNull(parseSwapPressure("SwapTotal: 100 kB\nSwapFree: 0 kB\nSwapFree: 1 kB"))
    }
}
