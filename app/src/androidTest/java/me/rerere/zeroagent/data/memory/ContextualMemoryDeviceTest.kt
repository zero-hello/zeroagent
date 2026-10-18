package me.rerere.zeroagent.data.memory

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.llamacpp.LlamaEmbeddingRuntime
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContextualMemoryDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun nativeSqliteCosineAndUnicodeSurviveReopen() {
        val file = File(context.cacheDir, "v111-vector-test.db"); file.delete()
        var h = MemoryNative.open(file.path, 3, "test")
        try {
            MemoryNative.put(h, "one", "喜歡烏龍茶 🫖".toByteArray(), floatArrayOf(1f, 0f, 0f), 1, "happy", "one", "USER")
            MemoryNative.put(h, "two", "電腦修復".toByteArray(), floatArrayOf(0f, 1f, 0f), 2, "calm", "two", "USER")
            val result = MemoryNative.query(h, floatArrayOf(1f, 0f, 0f), 1).decodeToString()
            assertTrue(result.contains("喜歡烏龍茶 🫖")); assertFalse(result.contains("電腦"))
            MemoryNative.close(h); h = MemoryNative.open(file.path, 3, "test")
            assertTrue(MemoryNative.recent(h, 10).decodeToString().contains("電腦"))
        } finally { MemoryNative.close(h); file.delete() }
    }
    @Test fun realPinnedModelEmbedsAndRecallsChinese() = runBlocking {
        assertTrue("Device test model must be provisioned by test harness", MemoryManager.get(context).modelFile.isFile)
        val manager = MemoryManager.get(context)
        try {
            manager.validateModel()
            LlamaEmbeddingRuntime.open(manager.modelFile.path, maxTokens = 128, nThreads = 1).use { encoder ->
                val query = encoder.embed("query: 我最喜歡喝什麼茶？")
                val tea = encoder.embed("passage: 我最喜歡喝無糖烏龍茶。")
                val unrelated = encoder.embed("passage: 電腦網路封包與系統故障的診斷程序。")
                fun cosine(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }
                val related = cosine(query, tea); val other = cosine(query, unrelated)
                println("Real GGUF semantic contrast: tea=$related unrelated=$other")
                assertTrue("Related Chinese text must rank above an unrelated topic", related > other + .02)
            }
            manager.setEnabled(true)
            val user = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("我最喜歡喝烏龍茶，不喜歡加糖。")))
            val answer = UIMessage(role = MessageRole.ASSISTANT, parts = listOf(UIMessagePart.Text("你偏好無糖烏龍茶。")))
            manager.remember(listOf(user, answer), "memory-device-fixture")
            val query = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("我喜歡喝什麼茶？")))
            val recall = manager.prepare(listOf(query), "new-device-fixture")
            assertTrue(recall.prompt.contains("烏龍茶"))
            val alreadyInContext = manager.prepare(listOf(user, answer, query), "same-context-device-fixture")
            assertFalse(alreadyInContext.prompt.contains("烏龍茶"))
            manager.refresh(); assertTrue(manager.state.value.records.isNotEmpty())
            manager.setEnabled(false)
            val stopped = manager.prepare(listOf(query), "disabled-device-fixture")
            assertFalse(stopped.active); assertTrue(stopped.prompt.isEmpty())
            manager.erase(); assertFalse(manager.enabled); assertTrue(manager.state.value.records.isEmpty())
        } finally { manager.erase() }
    }
}
