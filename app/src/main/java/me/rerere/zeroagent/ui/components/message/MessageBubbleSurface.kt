package me.rerere.zeroagent.ui.components.message

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.rerere.zeroagent.ui.components.ui.LocalZeroAgentGlassState
import me.rerere.zeroagent.ui.components.ui.zeroAgentGlass

/** Shared by real conversation messages and the live appearance preview. */
@Composable
fun MessageBubbleSurface(
    user: Boolean,
    opacity: Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val base = (if (user) colors.primaryContainer else colors.surfaceContainerHigh)
        .copy(alpha = opacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f)
    val foreground = if (user) colors.onPrimaryContainer else colors.onSurface
    val glass = LocalZeroAgentGlassState.current != null
    val surface = modifier.zeroAgentGlass(backgroundColor = base, shape = MaterialTheme.shapes.medium)
    if (onClick != null) Surface(onClick = onClick, modifier = surface, shape = MaterialTheme.shapes.medium,
        color = if (glass) Color.Transparent else base, contentColor = foreground, content = content)
    else Surface(modifier = surface, shape = MaterialTheme.shapes.medium,
        color = if (glass) Color.Transparent else base, contentColor = foreground, content = content)
}
