package me.rerere.zeroagent.assistant.wake

/** Main-thread owner publishes this snapshot only after the durable write returns. */
internal class WakeEnrollment {
    var samples: List<ShortArray> = emptyList(); private set
    fun restore(saved: List<ShortArray>) { samples = saved.map { it.copyOf() } }
    suspend fun commit(next: List<ShortArray>, persist: suspend (List<ShortArray>) -> Unit) {
        require(next.size <= 10)
        val snapshot = next.map { it.copyOf() }
        persist(snapshot)
        samples = snapshot
    }
}
