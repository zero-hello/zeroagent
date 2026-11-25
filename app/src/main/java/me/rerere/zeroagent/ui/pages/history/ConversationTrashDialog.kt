package me.rerere.zeroagent.ui.pages.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.zeroagent.data.model.Conversation
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog

@Composable
internal fun ConversationTrashDialog(
    conversations: List<Conversation>,
    locked: (Conversation) -> Boolean,
    loadDetails: suspend (Conversation) -> Conversation?,
    restore: suspend (Conversation) -> Boolean,
    purge: suspend (Conversation) -> Boolean,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Conversation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun act(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "無法完成操作，請稍後再試。" }
            finally { busy = false }
        }
    }
    ZeroAgentAlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("聊天室回收桶") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (conversations.isEmpty()) Text("回收桶是空的。")
                if (error != null) Text(error.orEmpty())
                conversations.forEach { chat ->
                    Text(if (locked(chat)) "已上鎖的聊天室" else chat.title.ifBlank { "未命名聊天室" })
                    FlowRow {
                        TextButton(enabled = !busy, onClick = { act { check(restore(chat)) } }) { Text("還原") }
                        TextButton(enabled = !busy && !locked(chat), onClick = {
                            act { pending = checkNotNull(loadDetails(chat)) }
                        }) { Text("永久刪除") }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onClose) { Text("關閉") } },
    )
    pending?.let { chat ->
        ZeroAgentAlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("永久刪除此聊天室？") },
            text = {
                val files = chat.files.distinct().count { it.scheme == "file" }
                Text("這會刪除聊天紀錄與未被其他聊天室引用的本機附件。此聊天室有 $files 個本機附件。這項操作無法還原。")
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                pending = null
                act { check(purge(chat)) }
            }) { Text("永久刪除") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("取消") } },
        )
    }
}