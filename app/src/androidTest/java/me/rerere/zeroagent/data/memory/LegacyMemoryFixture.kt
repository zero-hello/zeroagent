package me.rerere.zeroagent.data.memory

// These records exercise the view and delete path for data saved by previous versions.
internal fun seedLegacyMemory(manager: MemoryManager, id: String, text: String, conversation: String, role: String) {
    val file = manager.modelFile.resolveSibling("memory.db")
    val vector = FloatArray(384).apply { this[0] = 1f }
    val handle = MemoryNative.open(file.absolutePath, vector.size, MemoryManager.MODEL_SHA)
    try {
        MemoryNative.put(handle, id, text.toByteArray(), vector, System.currentTimeMillis(), "neutral", conversation, role)
    } finally {
        MemoryNative.close(handle)
    }
}
