package me.rerere.zeroagent.data.ai.tools.local

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.zeroagent.data.terminal.TermuxInstaller
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Requires an explicitly configured, owned test emulator with standard Termux installed. */
class TermuxBridgeDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val bin = "/data/data/com.termux/files/usr/bin"
    private val home = "/data/data/com.termux/files/home"

    @Test fun standardBashAndPackageManagersAreActuallyAvailable() = runBlocking {
        assertEquals(TermuxIntegration.State.READY, TermuxIntegration.state(context))
        assertEquals(TermuxIntegration.VerifyResult.Ok, TermuxIntegration.verify(context, 15_000))
    }

    @Test fun commandReturnsRealUnicodeStreamsAndNonzeroExit() = runBlocking {
        val result = runCommandCapture(context, "$bin/bash",
            arrayOf("-c", "printf 'stdout-測試\\n'; printf 'stderr-測試\\n' >&2; exit 7"), home, 15_000)
        assertTrue(result is CaptureResult.Success)
        result as CaptureResult.Success
        assertEquals("stdout-測試\n", result.stdout)
        assertEquals("stderr-測試\n", result.stderr)
        assertEquals(7, result.exitCode)
    }

    @Test fun interactiveSessionCanStartReceiveInputBeReadAndStop() = runBlocking {
        val name = TmuxOps.sessionName("device_" + UUID.randomUUID().toString().replace("-", "").take(12))
        suspend fun tmux(arguments: Array<String>): CaptureResult.Success {
            val result = runCommandCapture(context, "$bin/tmux", arguments, home, 15_000)
            assertTrue(result.toString(), result is CaptureResult.Success)
            result as CaptureResult.Success
            assertEquals(result.stderr, 0, result.exitCode)
            return result
        }
        tmux(TmuxOps.startArgv(name, 80, 24))
        try {
            tmux(TmuxOps.sendTextArgv(name, "printf '%s%s\\n' ZEROAGENT_DEVICE_ SESSION_OK"))
            tmux(TmuxOps.enterArgv(name))
            var screen = ""
            var attempts = 0
            while (attempts++ < 10 && !screen.contains("ZEROAGENT_DEVICE_SESSION_OK")) {
                screen = tmux(TmuxOps.capturePaneArgv(name, 40)).stdout
                if (!screen.contains("ZEROAGENT_DEVICE_SESSION_OK")) kotlinx.coroutines.delay(100)
            }
            assertTrue(screen, screen.contains("ZEROAGENT_DEVICE_SESSION_OK"))
        } finally { tmux(TmuxOps.killArgv(name)) }
        val remaining = runCommandCapture(context, "$bin/tmux", TmuxOps.listArgv(), home, 15_000)
        assertTrue(remaining.toString(), remaining is CaptureResult.Success)
        remaining as CaptureResult.Success
        if (remaining.exitCode == 0) assertFalse(parseSessions(remaining.stdout).any { it.name == name })
        else assertTrue(remaining.stderr, isSessionNotFound(remaining.stderr))
    }

    @Test fun cancelledCallerDoesNotDispatchACommand() = runBlocking {
        var dispatched = 0
        val tracked = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                dispatched++
                return super.startForegroundService(service)
            }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineContext.cancel()
            runCommandCapture(tracked, "$bin/true", emptyArray(), home, 1000)
        }.join()
        assertEquals(0, dispatched)
    }

    @Test fun installerChecksTheExactOfficialPackage() = runBlocking {
        val file = TermuxInstaller.validatedFile(context)
        assertEquals(TermuxInstaller.BYTES, file.length())
        val intent = TermuxInstaller.installIntent(context, file)
        assertEquals("content", intent.data?.scheme)
        assertEquals(context.packageName + ".fileprovider", intent.data?.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}
