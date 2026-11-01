package me.rerere.zeroagent.data.ai.tools.local

import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.zeroagent.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exercise Android's actual /data/data aliases as well as the host JVM fixtures. */
class PathSafetyGuardDeviceTest {
    @Test
    fun ownSandboxAliasesRemainAllowedAndOtherPackagesRemainBlocked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull(PathSafetyGuard.check(context.filesDir.canonicalPath))
        for (root in listOf("/data/data", "/data/user/0")) {
            assertNull(PathSafetyGuard.check("$root/${BuildConfig.APPLICATION_ID}/files"))
            assertEquals(
                "path_blocked",
                PathSafetyGuard.check("$root/${BuildConfig.APPLICATION_ID}.other/files")?.code,
            )
        }
    }

    @Test
    fun systemStorageAndTraversalRemainBlockedOnAndroid() {
        for (path in listOf("/system", "/system_ext", "/vendor", "/proc", "/dev", "/sys", "/apex")) {
            assertEquals(path, "path_blocked", PathSafetyGuard.check(path)?.code)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("path_blocked", PathSafetyGuard.check("${context.filesDir}/../cache")?.code)
        assertNull(PathSafetyGuard.check("/system-backup/file"))
    }
}
