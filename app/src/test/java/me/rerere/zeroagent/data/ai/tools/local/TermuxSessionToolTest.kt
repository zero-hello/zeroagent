package me.rerere.zeroagent.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** Retired external-session flags must produce migration errors, never fake a session. */
class TermuxSessionToolTest {
    @Test(expected = IllegalArgumentException::class) fun redirectsInteractiveCalls() {
        parseEmbeddedTerminalRequest(buildJsonObject { put("command", "bash"); put("interactive", true) }, 60_000L)
    }
    @Test(expected = IllegalArgumentException::class) fun redirectsDetachedCalls() {
        parseEmbeddedTerminalRequest(buildJsonObject { put("command", "sleep 30"); put("background", true) }, 60_000L)
    }
}
