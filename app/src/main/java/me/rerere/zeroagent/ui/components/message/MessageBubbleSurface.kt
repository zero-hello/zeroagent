package me.rerere.zeroagent.ui.components.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.ui.designsystem.ZeroTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Shared by real conversation messages and the live appearance preview. */
@Composable
fun MessageBubbleSurface(
    user: Boolean,
    opacity: Float,
    generating: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = ZeroTheme.colors
    val base = if (user) colors.textPrimary else colors.surface
    val foreground = if (user) colors.background else colors.textPrimary
    val shape = RoundedCornerShape(20.dp)
    val borderColor = androidx.compose.animation.animateColorAsState(
        targetValue = when {
            user -> colors.borderStrong
            generating -> colors.accentOlive
            else -> colors.borderSubtle
        }, label = "Message border",
    ).value
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) Surface(onClick = onClick, modifier = modifier, shape = shape,
        color = base, contentColor = foreground, border = border, content = content)
    else Surface(modifier = modifier, shape = shape, color = base, contentColor = foreground,
        border = border, content = content)
}
