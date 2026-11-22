package me.rerere.zeroagent.data.ai

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

class AILoggingPrivacyTest {
    @Test
    fun `generation diagnostics only retain allowlisted primitive fields`() {
        val fields = AILogging.Generation::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .associate { it.name to it.type }
        assertEquals(
            mapOf(
                "inputMessageCount" to Int::class.javaPrimitiveType,
                "availableToolCount" to Int::class.javaPrimitiveType,
                "stream" to Boolean::class.javaPrimitiveType,
                "timestampMillis" to Long::class.javaPrimitiveType,
            ),
            fields,
        )
    }
}
