package me.rerere.zeroagent.ui.pages.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.data.lab.ReplyContextSnapshot

@Composable
internal fun ReplyContextDialog(
    snapshot: ReplyContextSnapshot,
    onRetry: ((Set<String>) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var excluded by remember(snapshot) { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("這次帶了什麼") },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("這些資料來自這則回覆的模型請求。紀錄會在關閉應用程式後清除。", style = MaterialTheme.typography.bodyMedium)
                if (snapshot.entries.isEmpty()) Text("這次沒有帶入這裡列管的背景資料。")
                if (onRetry != null && snapshot.entries.isNotEmpty()) Text("勾選要略過的項目。重新回答會使用目前的模型，可能增加用量。", style = MaterialTheme.typography.bodyMedium)
                snapshot.entries.forEach { entry ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onRetry != null) Checkbox(entry.key in excluded, { checked ->
                            excluded = if (checked) excluded + entry.key else excluded - entry.key
                        })
                        Column {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall)
                            Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (onRetry != null && snapshot.entries.isNotEmpty()) TextButton(enabled = excluded.isNotEmpty(), onClick = { onRetry(excluded) }) { Text("略過後重新回答") }
            else TextButton(onClick = onDismiss) { Text("關閉") }
        },
        dismissButton = { if (onRetry != null && snapshot.entries.isNotEmpty()) TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
