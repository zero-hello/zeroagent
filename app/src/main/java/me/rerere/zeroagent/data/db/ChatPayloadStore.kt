package me.rerere.zeroagent.data.db

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.zeroagent.data.db.dao.BoundedMessageNode
import me.rerere.zeroagent.data.db.dao.readMessagePayload
import me.rerere.zeroagent.data.db.entity.MessageNodeEntity
import me.rerere.zeroagent.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Stores immutable message payloads privately. Room commits the reference after the file is ready. */
internal class ChatPayloadStore(context: Context, private val database: AppDatabase) {
    private val directory = File(context.noBackupFilesDir, "chat_payloads")
    private val dao get() = database.messageNodeDao()

    // Call while holding the same Room transaction used to find the row.
    suspend fun load(node: BoundedMessageNode): String =
        node.payloadSha256?.let(::readFile) ?: dao.readMessagePayload(node)

    fun encode(node: MessageNodeEntity): MessageNodeEntity {
        val raw = node.messages
        if (raw.length <= CHAT_PAYLOAD_BYTES / 3) return node
        val bytes = raw.encodeToByteArray()
        if (bytes.size <= CHAT_PAYLOAD_BYTES) return node
        val summary = summarizeChatPayload(raw)
        val digest = chatPayloadDigest(bytes)
        val target = payloadFile(digest)
        if (!target.isFile) {
            if (!directory.isDirectory) {
                check(directory.mkdirs()) { "Could not create chat payload directory" }
                syncDirectory(checkNotNull(directory.parentFile))
            }
            val temporary = File.createTempFile("pending-", ".tmp", directory)
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                try {
                    syncDirectory(directory)
                } catch (e: Exception) {
                    target.delete()
                    throw e
                }
            } finally {
                temporary.delete()
            }
        }
        return node.copy(messages = summary, payloadSha256 = digest)
    }

    fun readFile(digest: String): String {
        val bytes = payloadFile(digest).readBytes()
        check(chatPayloadDigest(bytes) == digest) { "Chat payload integrity check failed" }
        return bytes.decodeToString(throwOnInvalidSequence = true)
    }

    // Snapshotting may read references to files no longer present in the live DB. Pruning waits
    // until the snapshot contains those payloads itself. Writers do not acquire this lock.
    suspend fun <T> withStableFiles(block: () -> T): T = pruningLock.withLock { block() }

    suspend fun pruneUnreferenced(candidates: Collection<String>) {
        if (candidates.isEmpty()) return
        pruningLock.withLock {
            database.withTransaction {
                for (digest in candidates) {
                    if (!dao.hasPayloadReference(digest)) payloadFile(digest).delete()
                }
            }
        }
    }

    /** Skips undecodable legacy rows, so they remain available to the bounded legacy reader. */
    suspend fun migrateLargeRows() {
        var after = ""
        while (true) {
            val ids = dao.getLargeInlineNodeIds(after, CHAT_PAYLOAD_BYTES)
            if (ids.isEmpty()) break
            for (id in ids) {
                try {
                    database.withTransaction {
                        val row = dao.getBoundedNode(id) ?: return@withTransaction
                        if (row.payloadSha256 != null || row.payloadBytes <= CHAT_PAYLOAD_BYTES) return@withTransaction
                        val migrated = encode(row.toEntity(dao.readMessagePayload(row)))
                        dao.update(migrated)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("ChatPayloadStore", "Could not move a large chat record (${e.javaClass.simpleName})")
                }
            }
            after = ids.last()
            yield()
        }
        pruneUnreferenced(directory.list()?.filter(PAYLOAD_DIGEST::matches).orEmpty())
    }

    private fun syncDirectory(path: File) {
        val descriptor = Os.open(path.absolutePath, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun payloadFile(digest: String): File {
        require(PAYLOAD_DIGEST.matches(digest)) { "Invalid chat payload reference" }
        return File(directory, digest).also {
            check(it.canonicalFile.parentFile == directory.canonicalFile) { "Invalid chat payload path" }
        }
    }

    private companion object {
        val pruningLock = Mutex()
    }
}

internal const val CHAT_PAYLOAD_BYTES = 128 * 1024
private val PAYLOAD_DIGEST = Regex("[0-9a-f]{64}")

internal fun BoundedMessageNode.toEntity(messages: String) = MessageNodeEntity(
    id = id,
    conversationId = conversationId,
    nodeIndex = nodeIndex,
    selectIndex = selectIndex,
    messages = messages,
)

internal fun chatPayloadDigest(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Keep the small parts needed for SQL usage, recovery and attachment reference lookups. */
internal fun summarizeChatPayload(payload: String): String {
    val messages = JsonInstant.parseToJsonElement(payload) as JsonArray
    return JsonArray(messages.map { element ->
        val message = element as JsonObject
        buildJsonObject {
            for (key in listOf("role", "createdAt", "usage")) {
                message[key]?.let { put(key, it) }
            }
            val fileUrls = linkedSetOf<JsonPrimitive>()
            collectFileUrls(message, fileUrls)
            if (fileUrls.isNotEmpty()) put("files", JsonArray(fileUrls.toList()))
            val parts = message["parts"] as? JsonArray
            if (parts?.any { (it as? JsonObject)?.get("finishedAt") == JsonNull } == true) {
                put("finishedAt", JsonNull)
            }
        }
    }).toString()
}

private fun collectFileUrls(value: JsonElement, urls: MutableSet<JsonPrimitive>) {
    when (value) {
        is JsonObject -> value.values.forEach { collectFileUrls(it, urls) }
        is JsonArray -> value.forEach { collectFileUrls(it, urls) }
        is JsonPrimitive -> if (value.isString && value.content.startsWith("file://")) urls.add(value) else Unit
    }
}
