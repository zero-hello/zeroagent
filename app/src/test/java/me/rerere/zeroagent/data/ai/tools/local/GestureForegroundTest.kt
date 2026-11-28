package me.rerere.zeroagent.data.ai.tools.local

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureForegroundTest {
    @Test fun skipsForegroundCheckForLegacyCallsAndAcceptsTheExpectedApp() {
        assertNull(gestureForegroundError(null, "app.example.second"))
        assertNull(gestureForegroundError("app.example.first", "app.example.first"))
    }

    @Test fun refusesToDispatchWhenTheVisibleAppChangesOrIsUnknown() {
        assertEquals("wrong_foreground_app", gestureForegroundError("app.example.first", "app.example.second")?.get("error")?.jsonPrimitive?.contentOrNull)
        assertEquals("wrong_foreground_app", gestureForegroundError("app.example.first", null)?.get("error")?.jsonPrimitive?.contentOrNull)
    }
}
