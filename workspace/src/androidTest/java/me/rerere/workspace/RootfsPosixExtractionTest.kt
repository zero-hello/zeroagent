package me.rerere.workspace

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

/**
 * Real POSIX filesystem assertions live on Android rather than depending on
 * Windows developer mode or SeCreateSymbolicLinkPrivilege on the JVM host.
 * Downloading, regular-file extraction and traversal rejection remain JVM tests.
 */
@RunWith(AndroidJUnit4::class)
class RootfsPosixExtractionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
    )

    @Test
    fun relativeSymlinkAndExecutableFileArePreserved() {
        val archive = createArchive {
            writeTarEntry("bin/", '5')
            writeTarEntry("bin/hello", '0', "echo hello\n".toByteArray())
            writeTarEntry("usr/bin/hello-link", '2', linkName = "../../bin/hello")
        }
        val target = temporaryFolder.newFolder("rootfs")

        createInstaller().extractTar(archive, target) {}

        val executable = File(target, "bin/hello")
        val link = File(target, "usr/bin/hello-link")
        assertEquals("echo hello\n", executable.readText())
        assertTrue(executable.canExecute())
        assertEquals(493, Os.stat(executable.absolutePath).st_mode and 511) // 0755
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals("../../bin/hello", Files.readSymbolicLink(link.toPath()).toString())
        assertEquals("echo hello\n", link.readText())
    }

    @Test
    fun alpineAbsoluteShellSymlinkIsRecognizedAndBashIsPreferredWhenPresent() {
        val archive = createArchive {
            writeTarEntry("./", '5')
            writeTarEntry("./bin/", '5')
            writeTarEntry("./bin/busybox", '0', "busybox".toByteArray())
            writeTarEntry("./bin/sh", '2', linkName = "/bin/busybox")
        }
        val target = temporaryFolder.newFolder("rootfs")

        createInstaller().extractTar(archive, target) {}

        val busybox = File(target, "bin/busybox")
        val shell = File(target, "bin/sh")
        assertEquals("busybox", busybox.readText())
        assertTrue(busybox.canExecute())
        assertTrue(Files.isSymbolicLink(shell.toPath()))
        assertEquals("/bin/busybox", Files.readSymbolicLink(shell.toPath()).toString())
        assertTrue(WorkspaceManager.isUsableRootfs(target))
        assertEquals("/bin/sh", WorkspaceManager.rootfsShell(target))

        File(target, "bin/bash").writeText("bash")
        assertEquals("/bin/bash", WorkspaceManager.rootfsShell(target))
    }

    @Test
    fun relativeSymlinkCannotEscapeRootfs() {
        val outside = temporaryFolder.newFile("outside.txt").apply { writeText("unchanged") }
        val archive = createArchive {
            writeTarEntry("bin/escape", '2', linkName = "../../outside.txt")
        }
        val target = temporaryFolder.newFolder("rootfs")

        val error = assertThrows(IllegalArgumentException::class.java) {
            createInstaller().extractTar(archive, target) {}
        }

        assertTrue(error.message.orEmpty().contains("Symlink escapes rootfs"))
        assertFalse(Files.isSymbolicLink(File(target, "bin/escape").toPath()))
        assertEquals("unchanged", outside.readText())
    }

    private fun createInstaller() = RootfsInstaller(WorkspaceManager(temporaryFolder.newFolder()))

    private fun createArchive(entries: OutputStream.() -> Unit): File =
        temporaryFolder.newFile("rootfs.tar.gz").also { archive ->
            GZIPOutputStream(archive.outputStream()).use { output ->
                output.entries()
                output.write(ByteArray(TAR_BLOCK * 2))
            }
        }

    private fun OutputStream.writeTarEntry(
        name: String,
        type: Char,
        data: ByteArray = ByteArray(0),
        linkName: String = "",
    ) {
        val header = ByteArray(TAR_BLOCK)
        name.toByteArray(Charsets.UTF_8).copyInto(header, 0)
        "0000755".toByteArray(Charsets.UTF_8).copyInto(header, 100)
        data.size.toLong().toString(8).padStart(11, '0').toByteArray(Charsets.UTF_8)
            .copyInto(header, 124)
        header[156] = type.code.toByte()
        linkName.toByteArray(Charsets.UTF_8).copyInto(header, 157)
        write(header)
        write(data)
        write(ByteArray((TAR_BLOCK - data.size % TAR_BLOCK) % TAR_BLOCK))
    }

    companion object {
        private const val TAR_BLOCK = 512
    }
}
