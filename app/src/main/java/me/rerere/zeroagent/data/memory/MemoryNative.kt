package me.rerere.zeroagent.data.memory

/** Each call is owned by MemoryManager's mutex; vectors stay in app-private memory.db. */
internal object MemoryNative {
    init { System.loadLibrary("contextualmemory") }
    external fun open(path: String, dimensions: Int, fingerprint: String): Long
    external fun close(handle: Long)
    external fun put(handle: Long, id: String, text: ByteArray, vector: FloatArray, timestamp: Long,
        mood: String, conversation: String, role: String)
    external fun query(handle: Long, vector: FloatArray, k: Int): ByteArray
    external fun contains(handle: Long, id: String): Boolean
    external fun recent(handle: Long, limit: Int): ByteArray
    external fun remove(handle: Long, id: Long)
}
