package me.rerere.zeroagent.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import me.rerere.zeroagent.ui.theme.zeroTypography

@Immutable
data class ZeroColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val accentOlive: Color,
    val accentOliveSubtle: Color,
    val destructive: Color,
    val destructiveSubtle: Color,
    val diffAdded: Color,
)

private val light = ZeroColors(
    Color(0xFFFAFAFA), Color(0xFFFFFFFF), Color(0xFFF4F4F4),
    Color(0xFF111111), Color(0xFF555555), Color(0xFF696969),
    Color(0xFFE2E2E2), Color(0xFF111111), Color(0xFF7A8B69),
    Color(0xFFD5DDCB), Color(0xFF9E5A54), Color(0xFFF6E7E5), Color(0xFF365C40),
)
private val dark = ZeroColors(
    Color(0xFF0D0D0D), Color(0xFF141414), Color(0xFF1B1B1B),
    Color(0xFFF2F2F2), Color(0xFFA0A0A0), Color(0xFF969696),
    Color(0xFF262626), Color(0xFFF2F2F2), Color(0xFF94A682),
    Color(0xFF4C5744), Color(0xFFC47A74), Color(0xFF342321), Color(0xFFA6C8A9),
)

object ZeroTheme {
    private val palette = compositionLocalOf { light }
    internal fun paletteFor(isDark: Boolean): ZeroColors = if (isDark) dark else light

    val qrInk: Color get() = light.textPrimary
    val qrPaper: Color get() = light.surface
    val scrim: Color get() = light.textPrimary
    val colors: ZeroColors
        @Composable @ReadOnlyComposable get() = palette.current

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Provide(isDark: Boolean, content: @Composable () -> Unit) {
        val target = paletteFor(isDark)
        val background = animateColorAsState(target.background, tween(220), label = "Zero background").value
        val surface = animateColorAsState(target.surface, tween(220), label = "Zero surface").value
        val elevated = animateColorAsState(target.surfaceElevated, tween(220), label = "Zero elevated").value
        val border = animateColorAsState(target.borderSubtle, tween(220), label = "Zero border").value
        val colors = target.copy(background = background, surface = surface, surfaceElevated = elevated, borderSubtle = border)
        val scheme = remember(colors, isDark) { colors.asMaterialColors(isDark) }
        val appearance = rememberZeroAppearance()
        val family = appearance.fontFamily
        PreloadZeroFeedback(appearance.soundEnabled)
        val type = remember(family) { zeroTypography(family) }
        MaterialTheme(colorScheme = scheme, typography = type, shapes = zeroShapes) {
            CompositionLocalProvider(
                palette provides colors,
                LocalContentColor provides colors.textPrimary,
                LocalTextSelectionColors provides TextSelectionColors(colors.accentOlive, colors.accentOlive.copy(alpha = .25f)),
                LocalTonalElevationEnabled provides false,
                LocalRippleConfiguration provides null,
                LocalIndication provides remember(colors.textPrimary) { ZeroIndication(colors.textPrimary) },
                content = content,
            )
        }
    }
}

private fun ZeroColors.asMaterialColors(isDark: Boolean): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = textPrimary, onPrimary = background,
        primaryContainer = surfaceElevated, onPrimaryContainer = textPrimary,
        secondary = textSecondary, onSecondary = background,
        secondaryContainer = surfaceElevated, onSecondaryContainer = textPrimary,
        tertiary = textSecondary, onTertiary = background,
        tertiaryContainer = surfaceElevated, onTertiaryContainer = textPrimary,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary, surfaceTint = surface,
        surfaceVariant = surfaceElevated, onSurfaceVariant = textSecondary,
        surfaceDim = background, surfaceBright = surface,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceElevated,
        inverseSurface = textPrimary, inverseOnSurface = background, inversePrimary = background,
        outline = textMuted, outlineVariant = borderSubtle,
        error = destructive, onError = background,
        errorContainer = destructiveSubtle, onErrorContainer = destructive,
    )
}

private val zeroShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private data class ZeroIndication(private val ink: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node(), DrawModifierNode {
        private val presses = mutableSetOf<PressInteraction.Press>()
        private val focuses = mutableSetOf<FocusInteraction.Focus>()

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { event ->
                    when (event) {
                        is PressInteraction.Press -> presses.add(event)
                        is PressInteraction.Release -> presses.remove(event.press)
                        is PressInteraction.Cancel -> presses.remove(event.press)
                        is FocusInteraction.Focus -> focuses.add(event)
                        is FocusInteraction.Unfocus -> focuses.remove(event.focus)
                        else -> Unit
                    }
                    invalidateDraw()
                }
            }
        }

        override fun onDetach() {
            presses.clear()
            focuses.clear()
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val radius = CornerRadius(8.dp.toPx())
            if (presses.isNotEmpty()) drawRoundRect(ink.copy(alpha = .07f), cornerRadius = radius)
            if (focuses.isNotEmpty()) drawRoundRect(ink.copy(alpha = .55f), cornerRadius = radius, style = Stroke(1.dp.toPx()))
        }
    }
}