package me.rerere.zeroagent.assistant.wake

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.zeroagent.assistant.VoiceAssistantSettings
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
data class WakeDataset(val phrase: String = VoiceAssistantSettings.FIXED_WAKE_PHRASE, val language: String = "", val count: Int = 0, val profile: WakeProfile? = null, val target: Int = 5)
data class WakeSnapshot(val data: WakeDataset, val samples: List<ShortArray>)

/** PCM and metadata are synced before atomically publishing a generation pointer. */
class WakeProfileStore(private val root: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "voice-assistant-wake"))
    companion object { private val lock = Mutex() }
    private val current = File(root, "current.json")
    private val json = Json { ignoreUnknownKeys = true }
    init { root.mkdirs() }

    private fun folder(): File? {
        val backup = File(root, "current.json.bak")
        if (backup.isFile) Files.move(backup.toPath(), current.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        val pointer = current
        if (!pointer.exists()) return null
        require(pointer.length() in 1..80)
        val id = pointer.readText()
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root, id)
    }
    private fun dataset(folder: File): WakeDataset {
        val file = File(folder, "dataset.json"); require(file.length() in 1..2_000_000)
        return json.decodeFromString<WakeDataset>(file.readText()).let { if (it.count > it.target) it.copy(target = 10) else it }.also { data ->
            require(data.target in setOf(5, 10) && data.count in 0..data.target && data.phrase.isNotBlank() && data.phrase.length <= 64)
            data.profile?.let { p -> require(p.phrase == data.phrase && p.language == data.language && WakeEngines.find(p.engine)?.valid(p) == true && p.templates.size == data.count) }
        }
    }
    private fun readSamples(folder: File, count: Int): List<ShortArray> = (0 until count).map { i ->
        val file = File(folder, "sample-$i.pcm"); require(file.length() in 2..192_000 && file.length() % 2 == 0L)
        val bytes = file.readBytes()
        ShortArray(bytes.size / 2) { at -> ((bytes[at * 2].toInt() and 255) or (bytes[at * 2 + 1].toInt() shl 8)).toShort() }
    }
    suspend fun snapshot(): WakeSnapshot = withContext(Dispatchers.IO) { lock.withLock {
        val folder = folder() ?: return@withLock WakeSnapshot(WakeDataset(), emptyList())
        val data = dataset(folder)
        WakeSnapshot(data, readSamples(folder, data.count))
    } }
    suspend fun data(): WakeDataset = withContext(Dispatchers.IO) { lock.withLock { folder()?.let(::dataset) ?: WakeDataset() } }
    suspend fun load(): WakeProfile? = try { data().profile } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
    suspend fun samples(): List<ShortArray> = snapshot().samples
    suspend fun save(profile: WakeProfile, samples: List<ShortArray>) {
        require(WakeEngines.find(profile.engine)?.valid(profile) == true && samples.size == profile.templates.size)
        saveData(WakeDataset(profile.phrase, profile.language, samples.size, profile, samples.size), samples)
    }
    suspend fun saveDraft(phrase: String, language: String, samples: List<ShortArray>, target: Int = if (samples.size > 5) 10 else 5) =
        saveData(WakeDataset(phrase, language, samples.size, target = target), samples)
    private suspend fun saveData(data: WakeDataset, samples: List<ShortArray>) = withContext(Dispatchers.IO) {
        lock.withLock {
            require(data.target in setOf(5, 10) && data.count == samples.size && samples.size <= data.target && data.phrase == VoiceAssistantSettings.FIXED_WAKE_PHRASE)
            samples.forEach { WakeSampleQuality.inspect(it) }
            check(root.isDirectory || root.mkdirs()) { "Cannot create private wake sample storage" }
            val next = File(root, UUID.randomUUID().toString()).apply { check(mkdir()) }
            var committed = false
            try {
                samples.forEachIndexed { index, pcm ->
                    currentCoroutineContext().ensureActive()
                    val bytes = ByteArray(pcm.size * 2)
                    pcm.forEachIndexed { at, n -> bytes[at * 2] = n.toByte(); bytes[at * 2 + 1] = (n.toInt() shr 8).toByte() }
                    writeAtomic(File(next, "sample-$index.pcm"), bytes)
                }
                writeAtomic(File(next, "dataset.json"), json.encodeToString(data).toByteArray())
                val previous = runCatching { folder() }.getOrNull()
                currentCoroutineContext().ensureActive()
                writeAtomic(current, next.name.toByteArray())
                committed = true
                runCatching {
                    File(root, "current.json.bak").delete()
                    previous?.takeIf { it != next }?.deleteRecursively()
                }
            } finally { if (!committed) next.deleteRecursively() }
        }
    }
    private fun writeAtomic(file: File, bytes: ByteArray) {
        val temp = File(file.absolutePath + ".tmp")
        FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    suspend fun remove() {
        saveData(WakeDataset(), emptyList())
        withContext(Dispatchers.IO) { lock.withLock {
            val retained = folder()
            root.listFiles()?.filter { it.isDirectory && it != retained && it.name.matches(Regex("[a-f0-9-]{36}")) }
                ?.forEach { runCatching { it.deleteRecursively() } }
        } }
    }
}
