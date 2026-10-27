package me.rerere.zeroagent.assistant.wake

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WakeProfileStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun `saved PCM and count survive store recreation and language changes`() = runBlocking {
        val root = folder.newFolder(); val sample = WakeTestAudio.phrase()
        WakeProfileStore(root).saveDraft("Hey Zero", "zh-TW", listOf(sample), 10)
        var saved = WakeProfileStore(root).snapshot()
        assertEquals(1, saved.data.count); assertEquals(10, saved.data.target)
        assertArrayEquals(sample, saved.samples.single())
        WakeProfileStore(root).saveDraft("Hey Zero", "en-US", saved.samples, 10)
        saved = WakeProfileStore(root).snapshot()
        assertEquals(1, saved.data.count); assertArrayEquals(sample, saved.samples.single())
    }
    @Test fun `quality failure leaves the committed generation and its count intact`() = runBlocking {
        val root = folder.newFolder(); val store = WakeProfileStore(root); val sample = WakeTestAudio.phrase()
        store.saveDraft("Hey Zero", "en", listOf(sample))
        val pointer = File(root, "current.json").readText()
        try { store.saveDraft("Hey Zero", "en", listOf(sample, ShortArray(16000))); fail("silence must fail") } catch (_: WakeSampleException) { }
        assertEquals(pointer, File(root, "current.json").readText())
        assertEquals(1, store.snapshot().data.count)
    }
    @Test fun `an atomic pointer publication failure preserves the previous dataset`() = runBlocking {
        val root = folder.newFolder(); val store = WakeProfileStore(root); val sample = WakeTestAudio.phrase()
        store.saveDraft("Hey Zero", "en", listOf(sample))
        val pointer = File(root, "current.json").readText()
        assertTrue(File(root, "current.json.tmp").mkdir())
        try { store.saveDraft("Hey Zero", "en", listOf(sample, sample)); fail("publication must fail") } catch (_: java.io.IOException) { }
        assertEquals(pointer, File(root, "current.json").readText())
        assertEquals(1, WakeProfileStore(root).snapshot().data.count)
    }
    @Test fun `saved samples cannot silently change the fixed wake phrase`() = runBlocking {
        val store = WakeProfileStore(folder.newFolder())
        try { store.saveDraft("Other word", "en", listOf(WakeTestAudio.phrase())); fail("fixed phrase required") } catch (_: IllegalArgumentException) { }
        assertEquals(0, store.snapshot().data.count)
    }
    @Test fun `remove publishes zero samples and removes committed PCM`() = runBlocking {
        val root = folder.newFolder(); val store = WakeProfileStore(root)
        store.saveDraft("Hey Zero", "en", listOf(WakeTestAudio.phrase()))
        store.remove()
        assertEquals(0, store.snapshot().data.count)
        assertTrue(root.walkTopDown().none { it.extension == "pcm" })
    }
    @Test fun `legacy AtomicFile backup pointers still restore samples`() = runBlocking {
        val root = folder.newFolder(); val store = WakeProfileStore(root)
        store.saveDraft("Hey Zero", "en", listOf(WakeTestAudio.phrase()))
        assertTrue(File(root, "current.json").renameTo(File(root, "current.json.bak")))
        assertEquals(1, WakeProfileStore(root).snapshot().data.count)
    }

    @Test fun `a legacy backup wins over an interrupted pointer write`() = runBlocking {
        val root = folder.newFolder(); val store = WakeProfileStore(root)
        store.saveDraft("Hey Zero", "en", listOf(WakeTestAudio.phrase()))
        File(root, "current.json").copyTo(File(root, "current.json.bak"))
        File(root, "current.json").writeText("interrupted write")
        assertEquals(1, WakeProfileStore(root).snapshot().data.count)
    }

}
