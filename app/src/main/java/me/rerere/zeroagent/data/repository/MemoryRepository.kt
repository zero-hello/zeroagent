package me.rerere.zeroagent.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import me.rerere.zeroagent.data.db.dao.MemoryDAO
import me.rerere.zeroagent.data.db.entity.MemoryEntity
import me.rerere.zeroagent.data.model.AssistantMemory

class MemoryRepository(private val memoryDAO: MemoryDAO) {
    companion object {
        const val GLOBAL_MEMORY_ID = "__global__"
    }

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    fun allMemoriesFlow(): Flow<List<MemoryEntity>> = memoryDAO.getAllMemoriesFlow().whileActive()

    private fun Flow<List<MemoryEntity>>.whileActive(): Flow<List<MemoryEntity>> = combine(clock) { rows, now ->
        if (rows.any { it.expiresAt?.let { end -> end <= now } == true }) memoryDAO.deleteExpired(now)
        rows.filter { it.expiresAt?.let { end -> end > now } != false }
    }

    suspend fun setExpiry(id: Int, expiresAt: Long?) {
        require(expiresAt == null || expiresAt > System.currentTimeMillis())
        check(memoryDAO.setExpiry(id, expiresAt) > 0) { "找不到這筆記憶。" }
    }

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId).whileActive()
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content) }
            }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        return activeMemories(assistantId)
            .map { AssistantMemory(it.id, it.content) }
    }

    fun getGlobalMemoriesFlow(): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(GLOBAL_MEMORY_ID).whileActive()
            .map { entities ->
                entities.map { AssistantMemory(it.id, it.content) }
            }

    suspend fun getGlobalMemories(): List<AssistantMemory> {
        return activeMemories(GLOBAL_MEMORY_ID)
            .map { AssistantMemory(it.id, it.content) }
    }

    private suspend fun activeMemories(assistantId: String): List<MemoryEntity> {
        val now = System.currentTimeMillis()
        memoryDAO.deleteExpired(now)
        return memoryDAO.getMemoriesOfAssistant(assistantId).filter { it.expiresAt?.let { end -> end > now } != false }
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val old = memoryDAO.getMemoryById(id) ?: error("Memory record #$id not found")
        val newMemory = old.copy(
            content = content
        )
        memoryDAO.updateMemory(newMemory)
        return AssistantMemory(
            id = newMemory.id,
            content = newMemory.content,
        )
    }

    suspend fun addCapturedMemory(assistantId: String, content: String): Boolean =
        memoryDAO.insertCapturedMemory(assistantId, content, System.currentTimeMillis())

    suspend fun addMemory(assistantId: String, content: String): AssistantMemory {
        val memory = AssistantMemory(
            id = 0,
            content = content,
        )
        val newMemory = memory.copy(
            id = memoryDAO.insertMemory(
                MemoryEntity(
                    assistantId = assistantId,
                    content = memory.content
                )
            ).toInt()
        )
        return newMemory
    }

    suspend fun deleteMemory(id: Int) {
        memoryDAO.deleteMemory(id)
    }
}
