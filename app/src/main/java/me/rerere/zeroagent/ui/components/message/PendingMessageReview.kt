package me.rerere.zeroagent.ui.components.message

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.data.model.MessageNode
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog
import kotlin.uuid.Uuid

internal class PendingMessageReview(context: Context, private val conversation: Uuid) {
    private val preferences = context.applicationContext.getSharedPreferences("pending_message_review", Context.MODE_PRIVATE)
    private val key = conversation.toString()
    private var values by mutableStateOf(preferences.getStringSet(key, emptySet()).orEmpty().toSet())

    fun has(id: Uuid) = id.toString() in values

    fun toggle(id: Uuid) {
        val value = id.toString()
        values = if (value in values) values - value else values + value
        preferences.edit().putStringSet(key, values).apply()
    }
}

internal val LocalPendingMessageReview = staticCompositionLocalOf<PendingMessageReview?> { null }

@Composable
internal fun PendingMessageReviewButton(nodes: List<MessageNode>, onJump: (Uuid) -> Unit) {
    val store = LocalPendingMessageReview.current ?: return
    val marked = nodes.filter { store.has(it.id) }
    var show by remember { mutableStateOf(false) }
    if (marked.isNotEmpty()) TextButton(onClick = { show = true }) { Text("待處理（${marked.size}）") }
    if (show) ZeroAgentAlertDialog(
        onDismissRequest = { show = false },
        title = { Text("待處理訊息") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (marked.isEmpty()) Text("目前沒有待處理的訊息。")
                marked.forEach { node ->
                    TextButton(onClick = { show = false; onJump(node.id) }) {
                        Text(node.currentMessage.toText().trim().take(150).ifBlank { "附件或工具訊息" },
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { show = false }) { Text("關閉") } },
    )
}