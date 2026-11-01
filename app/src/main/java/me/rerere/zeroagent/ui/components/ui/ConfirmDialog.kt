package me.rerere.zeroagent.ui.components.ui

import androidx.compose.material3.ButtonDefaults

import me.rerere.zeroagent.ui.components.ui.ZeroAgentAlertDialog as AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun RikkaConfirmDialog(
    show: Boolean,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: @Composable () -> Unit,
) {
    if (!show) {
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}
