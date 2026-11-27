package me.rerere.zeroagent.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceChatContextTest {
    private val unknown = DeviceChatStatus(
        batteryPercent = null,
        externalPowerConnected = null,
        severeThermalLoad = false,
        powerSave = false,
        metered = false,
        networkIssue = null,
        lowStorage = false,
        lowMemory = false,
    )

    @Test
    fun `unknown fields are not reported as normal readings`() {
        assertNull(unknown.toPromptContext())
    }

    @Test
    fun `observations are supplied as background even for a normal battery level`() {
        val snapshot = unknown.copy(batteryPercent = 64, externalPowerConnected = false)
        val background = checkNotNull(snapshot.toPromptContext())
        assertTrue(background.contains("battery level: 64%"))
        assertTrue(background.contains("external power disconnected"))
        assertFalse(background.contains("thermal load"))
        assertTrue(background.contains("其他回覆一律省略"))
    }

    @Test
    fun `swap observation only accompanies actual low memory`() {
        assertNull(unknown.toPromptContext(highSwapUsage = true))
        val pressure = unknown.copy(lowMemory = true)
        assertFalse(checkNotNull(pressure.toPromptContext()).contains("swap space"))
        assertTrue(checkNotNull(pressure.toPromptContext(highSwapUsage = true)).contains("swap space"))
    }
}
