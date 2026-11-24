package me.rerere.zeroagent.ui.pages.extensions.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.theme.JetbrainsMono

@Composable
internal fun WorkspaceDiffDialog(
    diff: String,
    removing: Boolean,
    restoring: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
) {
    val lines = remember(diff) { diff.lines() }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val added = if (dark) Color(0xffa9e5ad) else Color(0xff205d34)
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_diff_action)) },
        text = {
            Column {
                if (removing) Text(stringResource(R.string.workspace_diff_remove_note), Modifier.padding(bottom = 8.dp))
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 360.dp).horizontalScroll(scroll)) {
                    items(lines) { line ->
                        val color = when {
                            line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                            line.startsWith("+") -> added
                            line.startsWith("-") -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(line, color = color, fontFamily = JetbrainsMono, softWrap = false)
                    }
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onRestore, enabled = !restoring) {
                Text(stringResource(R.string.workspace_diff_restore))
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss, enabled = !restoring) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
