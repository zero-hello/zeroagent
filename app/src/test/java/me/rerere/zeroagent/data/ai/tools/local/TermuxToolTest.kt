package me.rerere.zeroagent.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/** Legacy command identity must retain real command text and bounded capture behavior. */
class TermuxToolTest {
    @Test fun capturesCommandWithoutChangingShellSyntax() {
        val request = parseEmbeddedTerminalRequest(buildJsonObject { put("command", "printf '%s' hello && pwd") }, 60_000L)
        assertEquals("printf '%s' hello && pwd", request.command)
        assertEquals("", request.cwd)
        assertEquals(60_000L, request.timeoutMillis)
    }
    @Test fun keepsSavedTimeoutAndClampsExplicitOverride() {
        assertEquals(90_000L, parseEmbeddedTerminalRequest(buildJsonObject {
            put("command", "pwd"); put("timeout_seconds", 0)
        }, 90_000L).timeoutMillis)
        assertEquals(600_000L, parseEmbeddedTerminalRequest(buildJsonObject {
            put("command", "pwd"); put("timeout_seconds", 999999)
        }, 90_000L).timeoutMillis)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyCommand() {
        parseEmbeddedTerminalRequest(buildJsonObject { put("command", " ") }, 60_000L)
    }
}
