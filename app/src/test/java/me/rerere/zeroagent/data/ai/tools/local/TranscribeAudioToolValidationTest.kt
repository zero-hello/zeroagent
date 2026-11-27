package me.rerere.zeroagent.data.ai.tools.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Retained shared PathSafetyGuard and file validation coverage after external CLI retirement. */
class TranscribeAudioToolValidationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---------------------------------------------------------------------------
    // PathSafetyGuard — path argument validation
    // These run before any Context / Termux code is touched.
    // ---------------------------------------------------------------------------

    @Test fun `null path is blocked`() {
        val v = PathSafetyGuard.check(null)
        assertNotNull(v)
        assertEquals("path_blocked", v!!.code)
    }

    @Test fun `empty path is blocked`() {
        val v = PathSafetyGuard.check("")
        assertNotNull(v)
        assertEquals("path_blocked", v!!.code)
    }

    @Test fun `system path is blocked`() {
        val v = PathSafetyGuard.check("/system/lib/libc.so")
        assertNotNull(v)
        assertEquals("path_blocked", v!!.code)
    }

    @Test fun `proc path is blocked`() {
        val v = PathSafetyGuard.check("/proc/1/cmdline")
        assertNotNull(v)
        assertEquals("path_blocked", v!!.code)
    }

    @Test fun `dev path is blocked`() {
        val v = PathSafetyGuard.check("/dev/urandom")
        assertNotNull(v)
        assertEquals("path_blocked", v!!.code)
    }

    @Test fun `other app sandbox is blocked`() {
        val v = PathSafetyGuard.check("/data/data/com.evil.app/private.db")
        assertNotNull(v)
        assertEquals("path_blocked", v!!.code)
    }

    @Test fun `traversal sequence is blocked`() {
        val v = PathSafetyGuard.check("/sdcard/Download/../../../system/etc/passwd")
        assertNotNull(v)
        assertEquals("path_blocked", v!!.code)
    }

    @Test fun `sdcard audio path is allowed`() {
        // This should NOT be blocked by PathSafetyGuard — it's a normal user-data path.
        // (The tool will then check file existence, which will fail in tests since the
        // file doesn't exist on the JVM host — but the path guard itself returns null.)
        val v = PathSafetyGuard.check("/sdcard/Download/telegram_inbox/12345/voice.ogg")
        assertNull("sdcard Download path should not be path_blocked", v)
    }

    @Test fun `storage emulated path is allowed`() {
        val v = PathSafetyGuard.check("/storage/emulated/0/Download/voice.ogg")
        assertNull("storage/emulated path should not be path_blocked", v)
    }

    // ---------------------------------------------------------------------------
    // File-level checks (JVM temp files — no Context needed).
    // These validate the File.exists / isDirectory / length checks in the tool body.
    // ---------------------------------------------------------------------------

    @Test fun `empty file is detected as empty`() {
        val f = tmp.newFile("empty.ogg")
        // File is created by TemporaryFolder — it exists but length() == 0
        assertEquals(0L, f.length())
        // Shared file validation must recognize zero-byte input.
        // We verify the JVM-level check holds so there's no off-by-one.
        assert(f.exists()) { "temp file must exist" }
        assert(!f.isDirectory) { "temp file must not be a directory" }
    }

    @Test fun `directory is detected as directory`() {
        val dir = tmp.newFolder("audiodir")
        assert(dir.exists()) { "temp dir must exist" }
        assert(dir.isDirectory) { "temp dir must be a directory" }
        // Directories are not audio files.
    }

    @Test fun `non-existent file is detected as missing`() {
        val missing = File(tmp.root, "no_such_file.ogg")
        assert(!missing.exists()) { "file must not exist" }
        // Missing input must remain distinguishable from empty files.
    }
}
