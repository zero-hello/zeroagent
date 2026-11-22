package me.rerere.zeroagent.ui.pages.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.zeroagent.data.lab.LocalRedaction
import me.rerere.zeroagent.data.lab.SensitiveSpan

private data class DraftSection(val content: String?, val spans: List<SensitiveSpan>, val name: String)

@Composable
internal fun DraftRedactionDialog(
    parts: List<UIMessagePart>,
    onSend: (List<UIMessagePart>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var sections by remember(parts) { mutableStateOf<List<DraftSection>?>(null) }
    var selected by remember(parts) { mutableStateOf<Map<Int, Set<SensitiveSpan>>>(emptyMap()) }
    var includeUnscanned by remember(parts) { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(parts) {
        val items = withContext(Dispatchers.IO) {
            parts.map { part ->
                val text = when (part) {
                    is UIMessagePart.Text -> part.text.takeIf { it.length <= 200_000 }
                    is UIMessagePart.Document -> if (part.mime.startsWith("text/") || part.mime == "application/json") {
                        try {
                            val uri = part.url.toUri()
                            if (uri.scheme != "file" && uri.scheme != "content") null else readLabText(context, uri)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                    else -> null
                }
                val title = when (part) {
                    is UIMessagePart.Text -> "訊息"
                    is UIMessagePart.Document -> part.fileName
                    is UIMessagePart.Image -> "圖片"
                    is UIMessagePart.Video -> "影片"
                    is UIMessagePart.Audio -> "音訊"
                    else -> "附件"
                }
                DraftSection(text, text?.let(LocalRedaction::find).orEmpty(), title)
            }
        }
        selected = items.mapIndexed { index, section -> index to section.spans.toSet() }.toMap()
        sections = items
    }
    val items = sections
    val corrected = items?.mapIndexedNotNull { index, item ->
        val original = parts[index]
        val matches = selected[index].orEmpty()
        val source = item.content
        when {
            source == null -> original.takeIf { index in includeUnscanned }
            matches.isEmpty() -> original
            original is UIMessagePart.Text -> original.copy(text = LocalRedaction.mask(source, matches.toList()))
            original is UIMessagePart.Document -> UIMessagePart.Text("附件 ${original.fileName}（遮罩後的文字）\n${LocalRedaction.mask(source, matches.toList())}")
            else -> original
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("送出前檢查私人內容") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("比對在裝置上處理。這些規則可能漏掉內容，請先檢查。")
                if (items == null) CircularProgressIndicator()
                items?.forEachIndexed { index, item ->
                    if (item.content == null) {
                        Row {
                            Checkbox(index in includeUnscanned, { enabled ->
                                includeUnscanned = if (enabled) includeUnscanned + index else includeUnscanned - index
                            })
                            Text("保留 ${item.name}。這份內容無法在此自動檢查。")
                        }
                    } else if (item.spans.isNotEmpty()) {
                        Text("${item.name}有 ${item.spans.size} 個可能的私人片段。")
                        item.spans.take(15).forEach { span ->
                            val checked = selected[index].orEmpty()
                            Row {
                                Checkbox(span in checked, { enabled ->
                                    selected = selected + (index to if (enabled) checked + span else checked - span)
                                })
                                Text("${span.type} · ${item.content.substring(span.range).take(120)}")
                            }
                        }
                        if (item.spans.size > 15) Text("其餘 ${item.spans.size - 15} 筆也會一併遮蔽。")
                        WorkspaceResult("遮罩預覽", LocalRedaction.mask(item.content, selected[index].orEmpty().toList()).take(3_000))
                        if (parts[index] is UIMessagePart.Document) Text("有遮罩的附件將以文字送出，原始檔案不會一併附上。")
                    }
                }
                if (items != null && items.all { it.content != null && it.spans.isEmpty() }) Text("沒有比對到可能的私人片段。")
            }
        },
        confirmButton = { TextButton(enabled = !corrected.isNullOrEmpty(), onClick = { corrected?.let(onSend) }) { Text("確認送出") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("回到草稿") } },
    )
}
