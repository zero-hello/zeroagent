package me.rerere.zeroagent.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.ui.theme.LocalDarkMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule

/**
 * Authentic Liquid Glass lens effect powered by Kyant0 AndroidLiquidGlass.
 * Performs GPU real-time SDF refraction, chromatic dispersion (CA), and ambient highlight over the backdrop.
 */
fun Modifier.authenticLiquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape = { Capsule() },
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 24.dp,
    chromaticAberration: Boolean = true,
    blurRadius: Dp = 2.dp,
    highlight: (() -> Highlight?)? = { Highlight.Default },
    onDrawSurface: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? = null
): Modifier = this.then(
    drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            vibrancy()
            if (blurRadius > 0.dp) {
                blur(blurRadius.toPx())
            }
            lens(
                refractionHeight = refractionHeight.toPx(),
                refractionAmount = refractionAmount.toPx(),
                chromaticAberration = chromaticAberration
            )
        },
        highlight = highlight,
        onDrawSurface = onDrawSurface
    )
)

/**
 * Liquid Glass styling inspired by Kyant0's AndroidLiquidGlass & rdev's liquid-glass-react.
 * Provides authentic, crisp, and performant glassmorphic surfaces with specular rim reflections,
 * subtle top glare, and tactile spring physics — perfectly blended with Material 3 Expressive.
 */
object LiquidGlassDefaults {
    val ShapeSmall: Shape = RoundedCornerShape(16.dp)
    val ShapeMedium: Shape = RoundedCornerShape(24.dp)
    val ShapeLarge: Shape = RoundedCornerShape(32.dp)
    val ShapePill: Shape = CircleShape

    val BorderWidth: Dp = 1.dp
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
        // Top-edge subtle specular glare overlay
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = glareAlpha),
                    Color.White.copy(alpha = glareAlpha * 0.25f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.40f,
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
 * Modifier that applies clean Liquid Glass effect to any component.
 * Ensures foreground text and icons remain 100% crisp, sharp, and readable without distortion.
 */
fun Modifier.liquidGlass(
    shape: Shape = LiquidGlassDefaults.ShapeMedium,
    style: String = "frosted",
    tint: Color? = null,
    borderAlpha: Float? = null,
    glareAlpha: Float? = null,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this

    val effectiveBorderAlpha = borderAlpha ?: if (style == "transparent") 0.45f else 0.32f
    val effectiveGlareAlpha = glareAlpha ?: if (style == "transparent") 0.18f else 0.12f

    return this
        .clip(shape)
        .then(if (tint != null) Modifier.background(tint) else Modifier)
        .liquidGlassOverlay(
            shape = shape,
            borderAlpha = effectiveBorderAlpha,
            glareAlpha = effectiveGlareAlpha,
        )
}

/**
 * High-level Liquid Glass container surface.
 * When advanced materials are enabled, renders a translucent glass card.
 * When disabled, renders a standard Material 3 Surface.
 */
@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = LiquidGlassDefaults.ShapeMedium,
    style: String = LocalSettings.current.displaySetting.advancedMaterialStyle,
    tint: Color? = null,
    borderAlpha: Float? = null,
    glareAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val displaySetting = LocalSettings.current.displaySetting
    if (!displaySetting.advancedMaterialEnabled) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = { Box(content = content) },
        )
        return
    }

    val isDark = LocalDarkMode.current
    val effectiveTint = tint ?: if (style == "transparent") {
        if (isDark) Color(0x281C1E26) else Color(0x3AFFFFFF)
    } else {
        if (isDark) Color(0x44222432) else Color(0x8CFFFFFF)
    }
    val effectiveBorderAlpha = borderAlpha ?: if (style == "transparent") {
        if (isDark) 0.35f else 0.55f
    } else {
        if (isDark) 0.28f else 0.45f
    }
    val effectiveGlareAlpha = glareAlpha ?: if (style == "transparent") 0.18f else 0.12f

    Box(
        modifier = modifier
            .liquidGlass(
                shape = shape,
                style = style,
                tint = effectiveTint,
                borderAlpha = effectiveBorderAlpha,
                glareAlpha = effectiveGlareAlpha,
                enabled = true,
            ),
        content = content,
    )
}

/**
 * Floating circular/pill Liquid Glass icon button with tactile spring bounce.
 * When advanced materials are enabled, renders a glass button.
 * When disabled, renders a standard Material 3 FilledTonalIconButton.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: String = LocalSettings.current.displaySetting.advancedMaterialStyle,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val displaySetting = LocalSettings.current.displaySetting
    if (!displaySetting.advancedMaterialEnabled) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            content = content,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "liquid_btn_scale"
    )
    val isDark = LocalDarkMode.current

    val baseTint = if (style == "transparent") {
        if (isDark) Color(0x30252836) else Color(0x40FFFFFF)
    } else {
        if (isDark) Color(0x4D252836) else Color(0x95FFFFFF)
    }
    val borderAlpha = if (style == "transparent") (if (isDark) 0.40f else 0.60f) else (if (isDark) 0.30f else 0.45f)
    val glareAlpha = if (style == "transparent") 0.20f else 0.14f

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(
                shape = shape,
                style = style,
                tint = baseTint,
                borderAlpha = borderAlpha,
                glareAlpha = glareAlpha,
                enabled = true,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            content()
        }
    }
}

/**
 * Interactive button with Liquid Glass styling and tactile spring bounce.
 * When advanced materials are enabled, renders a glass button with crisp sharp text.
 * When disabled, renders a standard Material 3 OutlinedButton.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = LiquidGlassDefaults.ShapeSmall,
    style: String = LocalSettings.current.displaySetting.advancedMaterialStyle,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val displaySetting = LocalSettings.current.displaySetting
    if (!displaySetting.advancedMaterialEnabled) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            contentPadding = contentPadding,
            content = content,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "liquid_btn_scale"
    )
    val isDark = LocalDarkMode.current

    val baseTint = if (style == "transparent") {
        if (isDark) Color(0x28202330) else Color(0x35FFFFFF)
    } else {
        if (isDark) Color(0x3D202330) else Color(0x85FFFFFF)
    }
    val borderAlpha = if (isDark) 0.32f else 0.50f
    val glareAlpha = if (isDark) 0.14f else 0.20f

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(
                shape = shape,
                style = style,
                tint = baseTint,
                borderAlpha = borderAlpha,
                glareAlpha = glareAlpha,
                enabled = true,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
