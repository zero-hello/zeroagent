package me.rerere.zeroagent.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.uuid.Uuid

internal class ZeroSendMotion(val messageId: Uuid, val origin: Offset) {
    var consumed = false
}

internal val LocalZeroSendMotion = staticCompositionLocalOf<ZeroSendMotion?> { null }

@Composable
internal fun zeroSentBubbleMotion(messageId: Uuid): Modifier {
    val transition = LocalZeroSendMotion.current?.takeIf { it.messageId == messageId } ?: return Modifier
    val shouldAnimate = remember(transition) { !transition.consumed }
    if (!shouldAnimate) return Modifier
    var distance by remember(transition) { mutableStateOf<Offset?>(null) }
    val progress = remember(transition) { Animatable(0f) }
    LaunchedEffect(transition, distance != null) {
        if (distance != null) {
            transition.consumed = true
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 185f))
        }
    }
    return Modifier.onGloballyPositioned { coordinates ->
        if (distance == null) distance = transition.origin - coordinates.boundsInRoot().bottomRight
    }.graphicsLayer {
        val remaining = 1f - progress.value
        val offset = distance
        if (offset != null) {
            translationX = offset.x * remaining
            translationY = offset.y * remaining
        }
        alpha = (.7f + progress.value * .3f).coerceIn(0f, 1f)
        scaleX = 1f - .045f * remaining
        scaleY = scaleX
        transformOrigin = TransformOrigin(1f, 1f)
    }
}