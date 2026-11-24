package me.rerere.zeroagent.data.repository

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import me.rerere.zeroagent.utils.generateUnifiedDiff
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.security.MessageDigest

internal class WorkspaceTextChanges {
    internal enum class RestoreResult { Restored, Removed, Changed }

    internal class Change(
        val previous: String?,
        val current: String,
        val expected: Snapshot,
    ) {
        fun unifiedDiff(path: String): String? = when {
            previous == null && current.isEmpty() -> "--- /dev/null\n+++ b/$path"
            previous == null -> {
                val empty = emptyList<String>()
                val patch = DiffUtils.diff(empty, current.lines())
                UnifiedDiffUtils.generateUnifiedDiff("/dev/null", "b/$path", empty, patch, 3).joinToString("\n")
            }
            else -> generateUnifiedDiff(previous, current, path)
        }
    }

    internal class Snapshot(
        val text: String,
        val hash: ByteArray,
        val fileKey: Any?,
        val modifiedAt: FileTime,
    ) {
        fun isSameVersion(other: Snapshot): Boolean =
            fileKey == other.fileKey && modifiedAt == other.modifiedAt && hash.contentEquals(other.hash)
    }
    private data class Key(val workspace: String, val file: File)
    private val recent = LinkedHashMap<Key, Change>()

    // Repository serializes access. Keep only the newest edit for each file, in memory.
    fun record(workspace: String, file: File, before: Snapshot?, after: Snapshot?) {
        val key = Key(workspace, file)
        if (after != null && before?.isSameVersion(after) == true) return
        recent.remove(key)
        if (after == null) return
        if (before != null && before.text != after.text && before.text.lines() == after.text.lines()) return
        if (before?.text == after.text) return
        recent[key] = Change(before?.text, after.text, after)
        if (recent.size > MAX_CHANGES) recent.remove(recent.keys.first())
    }

    fun invalidate(workspace: String, file: File) {
        recent.keys.removeAll { it.workspace == workspace && it.file.toPath().startsWith(file.toPath()) }
    }

    fun latest(workspace: String, file: File): Change? = recent[Key(workspace, file)]

    fun restore(workspace: String, file: File, expected: Change): RestoreResult {
        val key = Key(workspace, file)
        if (recent[key] !== expected || !matches(file, expected)) return RestoreResult.Changed
        val previous = expected.previous
        if (previous == null) {
            Files.delete(file.toPath())
            recent.remove(key)
            return RestoreResult.Removed
        }
        val target = file.toPath()
        val pending = Files.createTempFile(target.parent, ".workspace-restore-", ".tmp")
        try {
            if (Files.getFileAttributeView(target, PosixFileAttributeView::class.java, NOFOLLOW_LINKS) != null) {
                Files.setPosixFilePermissions(pending, Files.getPosixFilePermissions(target, NOFOLLOW_LINKS))
            }
            pending.toFile().outputStream().use { out ->
                out.write(previous.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!matches(file, expected)) return RestoreResult.Changed
            Files.move(pending, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(pending)
        }
        recent.remove(key)
        return RestoreResult.Restored
    }

    private fun matches(file: File, change: Change): Boolean =
        snapshot(file)?.isSameVersion(change.expected) == true

    companion object {
        private const val MAX_BYTES = 128 * 1024
        private const val MAX_LINES = 3_000
        private const val MAX_CHANGES = 16

        fun snapshot(file: File): Snapshot? = runCatching {
            val path = file.toPath()
            if (file.canonicalFile != file) return null
            val before = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (!before.isRegularFile || before.size() > MAX_BYTES) return null
            val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(MAX_BYTES + 1)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                buffer.copyOf(count)
            }
            if (bytes.size > MAX_BYTES) return null
            val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            if ('\u0000' in text || text.lineSequence().take(MAX_LINES + 1).count() > MAX_LINES) return null
            val after = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (!after.isRegularFile || after.fileKey() != before.fileKey() || after.size() != before.size() ||
                after.lastModifiedTime() != before.lastModifiedTime() || file.canonicalFile != file
            ) return null
            Snapshot(text, MessageDigest.getInstance("SHA-256").digest(bytes), after.fileKey(), after.lastModifiedTime())
        }.getOrNull()
    }
}

/** A capped snapshot of regular files in the workspace files area. */
internal fun captureWorkspaceTextTree(root: File): Map<File, WorkspaceTextChanges.Snapshot?>? = runCatching {
    val path = root.toPath()
    if (root.canonicalFile != root || !Files.isDirectory(path, NOFOLLOW_LINKS)) return null
    val result = linkedMapOf<File, WorkspaceTextChanges.Snapshot?>()
    var visited = 0
    Files.walk(path, 12).use { stream ->
        val entries = stream.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            if (++visited > 192) return null
            if (path.relativize(entry).any { it.toString().startsWith('.') }) continue
            if (!Files.isRegularFile(entry, NOFOLLOW_LINKS)) continue
            val file = entry.toFile()
            result[file] = WorkspaceTextChanges.snapshot(file)
        }
    }
    result
}.getOrNull()
