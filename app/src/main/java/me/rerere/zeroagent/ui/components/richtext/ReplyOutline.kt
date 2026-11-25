package me.rerere.zeroagent.ui.components.richtext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import kotlinx.coroutines.launch
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog

internal class OutlineEntry(val title: String) {
    val requester = BringIntoViewRequester()
}

internal fun Modifier.outlineAnchor(entry: OutlineEntry?): Modifier =
    if (entry == null) this else bringIntoViewRequester(entry.requester)

@Composable
internal fun ReplyOutline(entries: Collection<OutlineEntry>) {
    if (entries.size < 2) return
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("回覆大綱") }
    if (open) ZeroAgentAlertDialog(
        onDismissRequest = { open = false },
        title = { Text("回覆大綱") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                entries.forEach { entry ->
                    TextButton(onClick = {
                        open = false
                        scope.launch { entry.requester.bringIntoView() }
                    }) { Text(entry.title) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("關閉") } },
    )
}