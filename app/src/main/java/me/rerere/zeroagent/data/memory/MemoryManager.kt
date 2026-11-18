package me.rerere.zeroagent.data.memory

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.llamacpp.LlamaEmbeddingRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.sqrt

/** Local, opt-in semantic recall. It extends rather than replaces existing explicit memories. */
class MemoryManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("contextual-memory-v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val directory = File(app.noBackupFilesDir, "contextual-memory").apply { mkdirs() }
    val modelFile = File(directory, "multilingual-e5-small-q8_0.gguf")
    private val dbFile = File(directory, "memory.db")
    private val mutable = MutableStateFlow(MemoryStatus(enabled, toneEnabled))
    val state = mutable.asStateFlow()
    val enabled get() = prefs.getBoolean("enabled", false)
    val toneEnabled get() = prefs.getBoolean("tone", true)
    private var prototypes: List<FloatArray>? = null
    private var validated = false
    data class MemoryStatus(val enabled: Boolean, val tone: Boolean, val ready: Boolean = false,
        val working: Boolean = false, val records: List<RecalledMemory> = emptyList(), val error: Boolean = false)
    data class Recall(val prompt: String = "", val mood: MoodAnalyzer.Mood? = null, val active: Boolean = false)

    fun setEnabled(value: Boolean) {
        check(prefs.edit().putBoolean("enabled", value).putBoolean("offered", true).commit())
        mutable.update { it.copy(enabled = value, error = false) }
        if (!value) WorkManager.getInstance(app).cancelUniqueWork(WORK)
    }
    fun setTone(value: Boolean) { check(prefs.edit().putBoolean("tone", value).commit()); mutable.update { it.copy(tone = value) } }
    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!enabled) return@withLock
            val work = WorkManager.getInstance(app)
            if (work.getWorkInfosForUniqueWork(WORK).get().any { !it.state.isFinished }) return@withLock
            val request = OneTimeWorkRequestBuilder<MemoryModelWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            check(prefs.edit().putString("work-id", request.id.toString()).commit())
            work.enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
            if (!enabled) work.cancelUniqueWork(WORK)
        }
    }
    fun observeInitialization() = WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(WORK).map { infos ->
        val current = prefs.getString("work-id", null)
        infos.filter { it.id.toString() == current || !it.state.isFinished }
    }

    suspend fun validateModel() = withContext(Dispatchers.IO) { mutex.withLock {
        mutable.update { it.copy(working = true, error = false) }
        try {
            verifyFile()
            openEncoder().use { runtime ->
                check(runtime.dimension == 384) { "Unexpected E5 dimensions" }
                val result = runtime.embed("query: ZeroAgent memory initialization")
                check(result.size == runtime.dimension)
                database(runtime.dimension) { }
            }
            validated = true
            mutable.update { it.copy(ready = true) }
            reloadLocked()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { validated = false; mutable.update { it.copy(error = true, ready = false) }; throw e
        } finally { mutable.update { it.copy(working = false) } }
    } }
    private fun openEncoder(): LlamaEmbeddingRuntime {
        val info = android.app.ActivityManager.MemoryInfo()
        app.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
        check(!info.lowMemory && info.availMem > MODEL_SIZE * 2 + 128L * 1024 * 1024) { "Insufficient memory for the semantic encoder" }
        return LlamaEmbeddingRuntime.open(modelFile.absolutePath, 128, 1)
    }
    private suspend fun verifyFile() {
        require(modelFile.isFile && modelFile.length() == MODEL_SIZE) { "Memory model is not installed" }
        val digest = MessageDigest.getInstance("SHA-256")
        modelFile.inputStream().buffered().use { stream -> val buffer = ByteArray(64 * 1024); while (true) { currentCoroutineContext().ensureActive(); val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        require(digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA) { "Memory model verification failed" }
    }
    private inline fun <T> database(dim: Int = 384, block: (Long) -> T): T {
        val handle = MemoryNative.open(dbFile.absolutePath, dim, MODEL_SHA)
        try { return block(handle) } finally { MemoryNative.close(handle) }
    }
    private fun rows(bytes: ByteArray): List<RecalledMemory> {
        val a = JSONArray(bytes.toString(Charsets.UTF_8))
        return (0 until a.length()).map { i -> val o = a.getJSONObject(i)
            RecalledMemory(o.getLong("id"), o.getString("text"), o.getLong("timestamp"), o.getString("mood"), o.getString("conversation"), o.getString("role"), o.optDouble("distance", 1.0), o.optJSONArray("vector")?.let { a -> (0 until a.length()).map { a.getDouble(it).toFloat() } }.orEmpty(), o.optString("external_id")) }
    }
    private fun reloadLocked() {
        val records = if (dbFile.exists()) database { rows(MemoryNative.recent(it, 100)) } else emptyList()
        mutable.update { it.copy(records = records, ready = it.ready && modelFile.length() == MODEL_SIZE) }
    }
    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { try { reloadLocked() } catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.update { it.copy(error = true) } } } }
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) { mutex.withLock { database { MemoryNative.remove(it, id) }; reloadLocked() } }
    suspend fun erase() = withContext(Dispatchers.IO) {
        setEnabled(false)
        mutex.withLock {
        for (suffix in listOf("", "-wal", "-shm")) check(!File(dbFile.path + suffix).exists() || File(dbFile.path + suffix).delete())
        prefs.edit().remove("mood").apply(); prototypes = null
        mutable.update { it.copy(records = emptyList(), error = false) }
    } }
    suspend fun prepare(messages: List<UIMessage>, conversation: String, persistInput: Boolean = true): Recall = withContext(Dispatchers.IO) {
        if (!enabled || !modelFile.isFile) return@withContext Recall()
        mutex.withLock {
            if (!enabled) return@withLock Recall()
            try {
                if (!validated) { verifyFile(); validated = true }
                val user = messages.lastOrNull { it.role == MessageRole.USER } ?: return@withLock Recall()
                val text = user.memoryText(); if (text.isBlank()) return@withLock Recall()
                openEncoder().use { runtime ->
                    val query = embed(runtime, "query: ", text)
                    val hits = database(runtime.dimension) { rows(MemoryNative.query(it, query, 512)) }
                    val recent = messages.lastMemoryTurns().map { it.id.toString() }.toSet()
                    val relevant = hits.filter { it.externalId.substringBefore(':') !in recent && it.distance < .25 }.sortedBy { it.distance }.take(5)
                    val proto = prototypes ?: MoodAnalyzer.PROTOTYPES.map { embed(runtime, "passage: ", it) }.also { prototypes = it }
                    val currentMood = MoodAnalyzer(proto).analyze(query)
                    val now = System.currentTimeMillis()
                    val savedMood = runCatching { JSONObject(prefs.getString("mood", "{}").orEmpty()) }.getOrNull()
                    val previous = savedMood?.takeIf { it.optBoolean("confident") && it.optString("tag") in MoodAnalyzer.LABELS }?.let { obj ->
                        obj.optJSONArray("weights")?.takeIf { it.length() == 5 || it.length() == MoodAnalyzer.LABELS.size }?.let { weights ->
                            val values = MoodAnalyzer.LABELS.indices.map { index ->
                                if (index < weights.length()) weights.optDouble(index, 0.0).toFloat() else 0f
                            }
                            values.takeIf { it.all { weight -> weight.isFinite() && weight in 0f..1f } }?.let { MoodAnalyzer.Mood(obj.getString("tag"), it, true) }
                        }
                    }
                    val mood = contextualMood(currentMood, previous, savedMood?.optLong("at", 0) ?: 0, now)
                    if (currentMood.confident) check(prefs.edit().putString("mood", JSONObject().put("tag", currentMood.tag).put("confident", true).put("weights", JSONArray(currentMood.weights)).put("at", now).toString()).commit())
                    if (persistInput) save(runtime, user, conversation, currentMood.tag.takeIf { currentMood.confident } ?: "unknown")
                    if (!enabled) return@withLock Recall()
                    mutable.update { it.copy(ready = true, error = false) }
                    Recall(recallPrompt(relevant, mood, toneEnabled), mood.takeIf { toneEnabled }, active = true)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                // Recall is optional; an unavailable encoder must not break ordinary chat.
                mutable.update { it.copy(error = true) }
                Recall()
            }
        }
    }
    suspend fun remember(messages: List<UIMessage>, conversation: String) = withContext(Dispatchers.IO) {
        if (!enabled || !modelFile.isFile) return@withContext
        mutex.withLock {
            if (!enabled) return@withLock
            try {
                if (!validated) { verifyFile(); validated = true }
                openEncoder().use { runtime ->
                    messages.takeLast(2).filter { it.role in setOf(MessageRole.USER, MessageRole.ASSISTANT) }.forEach { save(runtime, it, conversation, "unknown") }
                }
                reloadLocked()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutable.update { it.copy(error = true) } }
        }
    }
    private suspend fun save(runtime: LlamaEmbeddingRuntime, message: UIMessage, conversation: String, mood: String) {
        memoryPassages(message.memoryText()).forEachIndexed { index, text ->
            if (text.isBlank()) return@forEachIndexed
            val key = "${message.id}:$index"
            if (database(runtime.dimension) { MemoryNative.contains(it, key) }) return@forEachIndexed
            val vector = embed(runtime, "passage: ", text)
            currentCoroutineContext().ensureActive()
            if (!enabled) return
            database(runtime.dimension) { MemoryNative.put(it, key, text.toByteArray(Charsets.UTF_8), vector, System.currentTimeMillis(), mood, conversation, message.role.name) }
        }
    }
    private suspend fun embed(runtime: LlamaEmbeddingRuntime, prefix: String, text: String): FloatArray {
        val points = text.codePoints().toArray()
        val sum = FloatArray(runtime.dimension)
        // Split into bounded semantic chunks; use every chunk instead of silently dropping a tail.
        suspend fun part(offset: Int, count: Int) {
            currentCoroutineContext().ensureActive()
            val chunk = String(points, offset, count)
            val vector = try { runtime.embed(prefix + chunk) } catch (e: RuntimeException) {
                if (count <= 1 || !e.message.orEmpty().contains("token", true)) throw e
                part(offset, count / 2); part(offset + count / 2, count - count / 2); return
            }
            vector.indices.forEach { sum[it] += vector[it] * count }
        }
        points.indices.step(80).forEach { start -> part(start, minOf(80, points.size - start)) }
        val norm = sqrt(sum.sumOf { (it * it).toDouble() }); require(norm.isFinite() && norm > 0)
        return FloatArray(sum.size) { (sum[it] / norm).toFloat() }
    }
    companion object {
        const val WORK = "contextual-memory-initialization-v1"
        const val MODEL_SIZE = 132439008L
        const val MODEL_SHA = "e011debc1208e31bf7b6aebee2d9fc8bd2ca11694a77ed66ac9d0c9d0a877c93"
        const val MODEL_URL = "https://huggingface.co/TwinSunsLLC/multilingual-e5-small-gguf/resolve/b6cac9615d4ecce28d7f22539b7322d695fc2886/multilingual-e5-small-q8_0.gguf"
        @Volatile private var instance: MemoryManager? = null
        fun get(context: Context): MemoryManager = instance ?: synchronized(this) { instance ?: MemoryManager(context).also { instance = it } }
    }
}
