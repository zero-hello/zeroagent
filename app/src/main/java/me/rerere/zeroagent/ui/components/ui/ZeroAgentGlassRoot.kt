package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.rerere.zeroagent.ui.context.LocalSettings

val LocalZeroAgentGlassState = staticCompositionLocalOf<HazeState?> { null }

/** Only cross-window sheets/dialogs sample this source; in-window effects use the sibling backdrop. */
val LocalZeroAgentOverlayGlassState = staticCompositionLocalOf<HazeState?> { null }

/** Install inside the theme and LocalSettings provider. The source is a sibling, never its own effect. */
@Composable
fun ZeroAgentGlassRoot(content: @Composable () -> Unit) {
    val state = rememberHazeState()
    val overlayState = rememberHazeState()
    val colors = MaterialTheme.colorScheme
    val enabled = LocalSettings.current.displaySetting.liquidGlassEnabled
    Box(Modifier.fillMaxSize().then(if (enabled) Modifier.hazeSource(overlayState) else Modifier)) {
        if (enabled) {
            Canvas(Modifier.fillMaxSize().hazeSource(state)) {
                drawRect(colors.surface)
                drawRect(Brush.radialGradient(
                    listOf(colors.primaryContainer.copy(alpha = .55f), colors.surface.copy(alpha = 0f)),
                    center = Offset(size.width * .12f, size.height * .18f), radius = size.maxDimension * .8f,
                ))
                drawRect(Brush.radialGradient(
                    listOf(colors.secondaryContainer.copy(alpha = .4f), colors.surface.copy(alpha = 0f)),
                    center = Offset(size.width * .95f, size.height * .8f), radius = size.maxDimension * .65f,
                ))
            }
        }
        CompositionLocalProvider(
            LocalZeroAgentGlassState provides if (enabled) state else null,
            LocalZeroAgentOverlayGlassState provides if (enabled) overlayState else null,
            // Transparent Material surfaces inherit this color instead of the black default.
            LocalContentColor provides colors.onSurface,
        ) {
            content()
        }
    }
}

@Composable
fun Modifier.zeroAgentGlass(
    radius: Int = 24,
    backgroundColor: Color? = null,
    shape: Shape = RoundedCornerShape(radius.dp),
    overlay: Boolean = false,
): Modifier {
    val state = (if (overlay) LocalZeroAgentOverlayGlassState.current else LocalZeroAgentGlassState.current)
        ?: return this
    return zeroAgentGlass(state, radius = radius, backgroundColor = backgroundColor, shape = shape)
}
