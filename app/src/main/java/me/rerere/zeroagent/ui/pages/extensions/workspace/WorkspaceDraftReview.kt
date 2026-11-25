package me.rerere.zeroagent.ui.pages.extensions.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.difflib.DiffUtils
import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog

internal data class WorkspaceTextEdit(val line: Int, val removed: List<String>, val inserted: List<String>)

internal fun calculateWorkspaceEdits(before: String, after: String): List<WorkspaceTextEdit> {
    val original = before.split('\n')
    val replacement = after.split('\n')
    require(before.length + after.length <= 256_000 && original.size.toLong() * replacement.size <= 4_000_000L) {
        "這次的變更太大，請分段修改。"
    }
    return DiffUtils.diff(original, replacement).deltas.map { delta ->
        WorkspaceTextEdit(delta.source.position, delta.source.lines, delta.target.lines)
    }
}

internal fun applyWorkspaceEdits(before: String, edits: List<WorkspaceTextEdit>): String {
    val lines = before.split('\n').toMutableList()
    edits.sortedByDescending { it.line }.forEach { edit ->
        check(lines.subList(edit.line, edit.line + edit.removed.size) == edit.removed)
        repeat(edit.removed.size) { lines.removeAt(edit.line) }
        lines.addAll(edit.line, edit.inserted)
    }
    return lines.joinToString("\n")
}

@Composable
internal fun WorkspaceDraftReview(
    edits: List<WorkspaceTextEdit>,
    onDismiss: () -> Unit,
    onApply: (List<WorkspaceTextEdit>) -> Unit,
) {
    var selected by remember(edits) { mutableStateOf(edits.indices.toSet()) }
    ZeroAgentAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("確認檔案變更") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                edits.forEachIndexed { index, edit ->
                    Row {
                        Checkbox(checked = index in selected, onCheckedChange = {
                            selected = if (it) selected + index else selected - index
                        })
                        Column {
                            Text("第 ${edit.line + 1} 行", style = MaterialTheme.typography.labelLarge)
                            if (edit.removed.isNotEmpty()) Text(edit.removed.joinToString("\n") { "− $it" },
                                color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
                            if (edit.inserted.isNotEmpty()) Text(edit.inserted.joinToString("\n") { "+ $it" },
                                color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onApply(edits.filterIndexed { index, _ -> index in selected }) }) {
                Text("套用所選變更")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}