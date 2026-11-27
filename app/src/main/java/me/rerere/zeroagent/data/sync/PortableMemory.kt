package me.rerere.zeroagent.data.sync

import kotlinx.serialization.Serializable
import me.rerere.zeroagent.data.db.dao.MemoryDAO
import me.rerere.zeroagent.data.db.entity.MemoryEntity
import me.rerere.zeroagent.data.lab.LocalRedaction
import me.rerere.zeroagent.data.repository.MemoryRepository
import kotlin.uuid.Uuid

@Serializable
internal data class PortableMemory(
    val scopes: Map<String, List<String>> = emptyMap(),
    val capturePaused: Boolean = false,
    val blocked: Set<String> = emptySet(),
) {
    val count: Int get() = scopes.values.sumOf { it.size }

    fun validate() {
        require(scopes.size <= 100 && count <= 2_000 && blocked.size <= 5_000 &&
            blocked.all { it.matches(FINGERPRINT) } && scopes.all { (scope, entries) ->
                (scope == MemoryRepository.GLOBAL_MEMORY_ID || runCatching { Uuid.parse(scope) }.isSuccess) &&
                    entries.isNotEmpty() && entries.distinct().size == entries.size && entries.all(::isPortableMemoryText)
            }) { "記憶備份的範圍、大小或內容不符。" }
    }
}

private val FINGERPRINT = Regex("[a-f0-9]{64}")

internal fun isPortableMemoryText(text: String): Boolean = text.isNotBlank() && text.length <= 4_000 &&
    LocalRedaction.find(text).none { it.type.startsWith("憑證") } &&
    !text.contains("PRIVATE KEY-----", ignoreCase = true)

internal fun portableMemoryScopeMap(
    assistantIds: Set<String>,
    projectIds: Map<String, String>,
    incomingProjects: Set<String>,
): Map<String, String> = buildMap {
    put(MemoryRepository.GLOBAL_MEMORY_ID, MemoryRepository.GLOBAL_MEMORY_ID)
    for (id in assistantIds - incomingProjects) put(id, id)
    putAll(projectIds)
}

internal suspend fun restorePortableMemories(
    dao: MemoryDAO,
    memory: PortableMemory,
    scopes: Map<String, String>,
    replace: Boolean,
): Int {
    var skipped = 0
    for ((source, records) in memory.scopes) {
        val target = scopes[source]
        if (target == null) {
            skipped += records.size
            continue
        }
        val existing = if (replace) {
            dao.deleteMemoriesOfAssistant(target)
            hashSetOf()
        } else {
            dao.getMemoriesOfAssistant(target).mapTo(hashSetOf()) { it.content }
        }
        for (text in records) {
            if (existing.add(text)) dao.insertMemory(MemoryEntity(assistantId = target, content = text))
        }
    }
    return skipped
}
