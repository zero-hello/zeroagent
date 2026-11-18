package me.rerere.zeroagent.data.productivity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Each request has an independent outcome; cancellation always reaches every child. */
object ComparisonRunner {
    suspend fun <T> run(
        ids: List<String>,
        request: suspend (String) -> T,
        update: (String, Result<T>) -> Unit,
    ) = coroutineScope {
        require(ids.distinct().size in 2..4) { "請選擇 2 至 4 個不同模型。" }
        ids.distinct().forEach { id ->
            launch {
                val result = try { Result.success(request(id)) }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { Result.failure(error) }
                update(id, result)
            }
        }
    }
}
