package me.rerere.zeroagent.data.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidShellEnvironmentTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun interactiveAndAgentCommandsUseAndroidShellWithAStablePrivateHome() {
        val home = temporary.newFolder("home")
        val temp = temporary.newFolder("tmp")
        val environment = AndroidShellEnvironment.environment(home, temp)
        val directory = File("/data/user/0/app.zeroagent/files/workspace")
        assertArrayEquals(arrayOf(AndroidShellEnvironment.SHELL, "-i"), AndroidShellEnvironment.interactiveArguments(directory, root = false))
        val rootArguments = AndroidShellEnvironment.interactiveArguments(directory, root = true)
        assertArrayEquals(arrayOf(AndroidShellEnvironment.SHELL, "-c"), rootArguments.take(2).toTypedArray())
        assertTrue(rootArguments[2].startsWith("exec su -c "))
        assertEquals("/system/bin/sh", environment["SHELL"])
        assertEquals(home.absolutePath, environment["HOME"])
        assertEquals(temp.absolutePath, environment["TMPDIR"])
        assertEquals("ZeroAgent", environment["USER"])
        assertEquals("$ ", environment["PS1"])
        assertFalse(environment.containsKey("PROOT_LOADER"))
        assertFalse(environment["PATH"].orEmpty().contains("/usr/bin"))
    }

    @Test fun startupInstallsRealSystemUtilitiesAndKeepsThePrivatePathOutOfThePrompt() {
        val home = temporary.newFolder("home with ' quote")
        AndroidShellEnvironment.prepare(home)
        val rc = File(home, ".zeroagent-shellrc").readText()
        org.junit.Assert.assertTrue(rc.contains("sysinfo()"))
        org.junit.Assert.assertTrue(rc.contains("getprop ro.product.model"))
        org.junit.Assert.assertTrue(rc.contains("PS1='# '"))
        org.junit.Assert.assertFalse(rc.contains(home.absolutePath))
        org.junit.Assert.assertTrue(AndroidShellEnvironment.rootInteractiveCommand(home).contains("id -u"))
        assertEquals("'a'\\''b'", AndroidShellEnvironment.quote("a'b"))
        assertEquals(File(home, ".zeroagent-shellrc").absolutePath,
            AndroidShellEnvironment.environment(home, File(home, ".tmp"))["ENV"])
    }

    @Test fun commandDirectoryUsesTheSameHomeAndAllowsExistingChildren() {
        val home = temporary.newFolder("home")
        val project = File(home, "project").apply { mkdirs() }
        assertEquals(home.canonicalFile, AndroidShellEnvironment.workingDirectory(home, ""))
        assertEquals(project.canonicalFile, AndroidShellEnvironment.workingDirectory(home, "project"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCommandDirectoriesOutsideHome() {
        val home = temporary.newFolder("home")
        AndroidShellEnvironment.workingDirectory(home, "../")
    }

    @Test(expected = IllegalArgumentException::class)
    fun reportsMissingCommandDirectoryBeforeStartingAShell() {
        AndroidShellEnvironment.workingDirectory(temporary.newFolder("home"), "missing")
    }
}
