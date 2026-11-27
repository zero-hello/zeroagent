package me.rerere.zeroagent.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceTextChangesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `diff and restore preserve original bytes`() {
        val file = temporaryFolder.newFile().canonicalFile
        file.writeText("line\r\nold\r\n")
        val before = WorkspaceTextChanges.snapshot(file)
        file.writeText("line\nnew\n")
        val changes = WorkspaceTextChanges()
        changes.record("workspace", file, before, WorkspaceTextChanges.snapshot(file))
        val change = checkNotNull(changes.latest("workspace", file))
        val diff = checkNotNull(change.unifiedDiff("sample.txt"))
        assertTrue(diff.contains("-old"))
        assertTrue(diff.contains("+new"))
        assertEquals(WorkspaceTextChanges.RestoreResult.Restored, changes.restore("workspace", file, change))
        assertEquals("line\r\nold\r\n", file.readText())
        assertNull(changes.latest("workspace", file))
    }

    @Test fun `restore keeps a file changed after preview`() {
        val file = temporaryFolder.newFile().canonicalFile
        file.writeText("before")
        val before = WorkspaceTextChanges.snapshot(file)
        file.writeText("after")
        val changes = WorkspaceTextChanges()
        changes.record("workspace", file, before, WorkspaceTextChanges.snapshot(file))
        val change = checkNotNull(changes.latest("workspace", file))
        assertNull(changes.latest("other workspace", file))
        file.writeText("later edit")
        assertEquals(WorkspaceTextChanges.RestoreResult.Changed, changes.restore("workspace", file, change))
        assertEquals("later edit", file.readText())
    }

    @Test fun `undo creation removes the unchanged new file`() {
        val file = temporaryFolder.newFile().canonicalFile
        file.writeText("new content")
        val changes = WorkspaceTextChanges()
        changes.record("workspace", file, null, WorkspaceTextChanges.snapshot(file))
        val change = checkNotNull(changes.latest("workspace", file))
        assertNotNull(change.unifiedDiff("new.txt"))
        assertEquals(WorkspaceTextChanges.RestoreResult.Removed, changes.restore("workspace", file, change))
        assertTrue(!file.exists())
    }

    @Test fun `binary and oversized files are excluded`() {
        val file = temporaryFolder.newFile().canonicalFile
        file.writeBytes(byteArrayOf(0))
        assertNull(WorkspaceTextChanges.snapshot(file))
        file.writeBytes(ByteArray(128 * 1024 + 1))
        assertNull(WorkspaceTextChanges.snapshot(file))
    }
}
