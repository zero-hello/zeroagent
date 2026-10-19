package me.rerere.zeroagent.ui.components.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Material's dialog layout and accessibility with the app's shared glass surface. */
@Composable
fun ZeroAgentAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val glass = LocalZeroAgentGlassState.current != null
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.zeroAgentGlass(radius = 24, backgroundColor = containerColor, shape = shape, overlay = true),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (glass) Color.Transparent else containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = if (glass) 0.dp else tonalElevation,
        properties = properties,
    )
}

/** Custom-content dialogs retain the same shared shape and backdrop as standard confirmations. */
@Composable
fun ZeroAgentAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.BasicAlertDialog(onDismissRequest, modifier, properties) {
        androidx.compose.material3.Surface(
            modifier = Modifier.zeroAgentGlass(radius = 24, overlay = true),
            shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
            color = if (LocalZeroAgentGlassState.current != null) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
            content = content,
        )
    }
}
