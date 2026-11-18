package me.rerere.zeroagent.data.terminal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertArrayEquals(arrayOf("-i"), AndroidShellEnvironment.interactiveArguments())
        assertEquals("/system/bin/sh", environment["SHELL"])
        assertEquals(home.absolutePath, environment["HOME"])
        assertEquals(temp.absolutePath, environment["TMPDIR"])
        assertEquals("ZeroAgent", environment["USER"])
        assertEquals("ZeroAgent $ ", environment["PS1"])
        assertFalse(environment.containsKey("PROOT_LOADER"))
        assertFalse(environment["PATH"].orEmpty().contains("/usr/bin"))
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
