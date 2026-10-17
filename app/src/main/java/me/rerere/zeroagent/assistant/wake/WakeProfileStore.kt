package me.rerere.zeroagent.assistant.wake

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class WakeDataset(val phrase: String = "Hey Zero", val language: String = "", val count: Int = 0, val profile: WakeProfile? = null)

/** Samples are private, excluded from backups, and committed before switching the atomic pointer. */
class WakeProfileStore(context: Context) {
    companion object { private val lock = Mutex() }
    private val root = File(context.noBackupFilesDir, "voice-assistant-wake").apply { mkdirs() }
    private val current = AtomicFile(File(root, "current.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private fun folder(): File? {
        if (!current.baseFile.exists()) return null
        require(current.baseFile.length() in 1..80)
        val id = current.openRead().use { it.readBytes().decodeToString() }
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root, id)
    }
    private fun dataset(folder: File): WakeDataset {
        val file = File(folder, "dataset.json"); require(file.length() in 1..2_000_000)
        return json.decodeFromString<WakeDataset>(file.readText()).also { data ->
            require(data.count in 0..10 && data.phrase.isNotBlank() && data.phrase.length <= 64)
            data.profile?.let { p -> require(p.phrase == data.phrase && p.language == data.language && WakeEngines.find(p.engine)?.valid(p) == true && p.templates.size == data.count) }
        }
    }
    suspend fun data(): WakeDataset = withContext(Dispatchers.IO) { lock.withLock { folder()?.let(::dataset) ?: WakeDataset(count = 0) } }
    suspend fun load(): WakeProfile? = try { data().profile } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
    suspend fun samples(): List<ShortArray> = withContext(Dispatchers.IO) {
        lock.withLock {
            val folder = folder() ?: return@withLock emptyList()
            val data = dataset(folder)
            (0 until data.count).map { i ->
                val file = File(folder, "sample-$i.pcm"); require(file.length() in 2..192_000 && file.length() % 2 == 0L)
                val bytes = file.readBytes()
                ShortArray(bytes.size / 2) { at -> ((bytes[at * 2].toInt() and 255) or (bytes[at * 2 + 1].toInt() shl 8)).toShort() }
            }
        }
    }
    suspend fun save(profile: WakeProfile, samples: List<ShortArray>) {
        require(WakeEngines.find(profile.engine)?.valid(profile) == true && samples.size == profile.templates.size)
        saveData(WakeDataset(profile.phrase, profile.language, samples.size, profile), samples)
    }
    /** Editing samples invalidates the old model until the user explicitly retrains. */
    suspend fun saveDraft(phrase: String, language: String, samples: List<ShortArray>) = saveData(WakeDataset(phrase, language, samples.size), samples)
    private suspend fun saveData(data: WakeDataset, samples: List<ShortArray>) = withContext(Dispatchers.IO) {
        lock.withLock {
            require(data.count == samples.size && samples.size <= 10 && data.phrase.isNotBlank() && data.phrase.length <= 64)
            val next = File(root, UUID.randomUUID().toString()).apply { check(mkdir()) }
            try {
                samples.forEachIndexed { index, pcm ->
                    require(pcm.size <= 96000)
                    File(next, "sample-$index.pcm").outputStream().buffered().use { out -> pcm.forEach { n -> out.write(n.toInt() and 255); out.write((n.toInt() shr 8) and 255) } }
                }
                val metadata = AtomicFile(File(next, "dataset.json")); val stream = metadata.startWrite()
                try { stream.write(json.encodeToString(data).toByteArray()); metadata.finishWrite(stream) }
                catch (e: Throwable) { metadata.failWrite(stream); throw e }
                val previous = runCatching { folder() }.getOrNull()
                val pointer = current.startWrite()
                try { pointer.write(next.name.toByteArray()); current.finishWrite(pointer) }
                catch (e: Throwable) { current.failWrite(pointer); throw e }
                previous?.takeIf { it != next }?.deleteRecursively()
            } catch (e: Throwable) { next.deleteRecursively(); throw e }
        }
    }
    suspend fun remove() = withContext(Dispatchers.IO) {
        lock.withLock {
            current.delete()
            root.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}")) }?.forEach { check(it.deleteRecursively()) }
        }
    }
}
