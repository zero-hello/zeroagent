package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.ui.theme.LocalDarkMode

/**
 * Liquid Glass styling inspired by Kyant0's AndroidLiquidGlass & rdev's liquid-glass.
 * Combines translucent refractive backdrop, specular rim reflection, and subtle top-edge glare
 * seamlessly with Material 3 Expressive shapes.
 */
object LiquidGlassDefaults {
    val ShapeSmall: Shape = RoundedCornerShape(16.dp)
    val ShapeMedium: Shape = RoundedCornerShape(24.dp)
    val ShapeLarge: Shape = RoundedCornerShape(32.dp)
    val ShapePill: Shape = CircleShape

    val BorderWidth: Dp = 1.25.dp
}

/**
 * Modifier that applies specular rim highlight and top-edge glare overlay.
 */
fun Modifier.liquidGlassOverlay(
    shape: Shape = LiquidGlassDefaults.ShapeMedium,
    borderAlpha: Float = 0.35f,
    glareAlpha: Float = 0.16f,
): Modifier = this
    .drawWithContent {
        drawContent()
        // Top-edge specular glare overlay (light bouncing from above)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = glareAlpha),
                    Color.White.copy(alpha = glareAlpha * 0.25f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.45f,
            )
        )
    }
    .border(
        width = LiquidGlassDefaults.BorderWidth,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha),
                Color.White.copy(alpha = borderAlpha * 0.35f),
                Color.Transparent,
                Color.White.copy(alpha = borderAlpha * 0.15f),
            ),
            start = Offset(0f, 0f),
            end = Offset.Infinite,
        ),
        shape = shape,
    )

/**
 * Modifier that applies full Liquid Glass effect to any component.
 */
fun Modifier.liquidGlass(
    shape: Shape = LiquidGlassDefaults.ShapeMedium,
    tint: Color? = null,
    borderAlpha: Float = 0.35f,
    glareAlpha: Float = 0.16f,
): Modifier = this
    .clip(shape)
    .then(if (tint != null) Modifier.background(tint) else Modifier)
    .liquidGlassOverlay(shape = shape, borderAlpha = borderAlpha, glareAlpha = glareAlpha)

/**
 * High-level Liquid Glass container surface.
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = LiquidGlassDefaults.ShapeMedium,
    tint: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (LocalDarkMode.current) 0.65f else 0.78f),
    borderAlpha: Float = if (LocalDarkMode.current) 0.35f else 0.5f,
    glareAlpha: Float = 0.16f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .liquidGlassOverlay(shape = shape, borderAlpha = borderAlpha, glareAlpha = glareAlpha),
        content = content,
    )
}
