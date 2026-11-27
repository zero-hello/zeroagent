package me.rerere.zeroagent.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/** Migrating external CLI environments must not silently reuse their private directories. */
class WhisperStatusToolTest {
    @Test(expected = IllegalArgumentException::class) fun rejectsExternalCliHome() {
        parseEmbeddedTerminalRequest(buildJsonObject {
            put("command", "pwd"); put("working_dir", "/data/data/com.termux/files/home")
        }, 60_000L)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWorkspaceEscape() {
        parseEmbeddedTerminalRequest(buildJsonObject {
            put("command", "pwd"); put("working_dir", "/workspace/../root")
        }, 60_000L)
    }
    @Test fun acceptsInternalDirectory() {
        assertEquals("audio", parseEmbeddedTerminalRequest(buildJsonObject {
            put("command", "pwd"); put("working_dir", "/workspace/audio")
        }, 60_000L).cwd)
    }
}
