package me.rerere.zeroagent.ui.components.ui

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import android.os.Build
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3
import me.rerere.zeroagent.ui.context.LocalSettings
import me.rerere.zeroagent.data.datastore.DisplaySetting

fun DisplaySetting.safeGlassOpacity(): Float =
    if (liquidGlassOpacity.isFinite()) liquidGlassOpacity.coerceIn(0.15f, 0.95f) else 0.65f

/** The source is live UI content; no screenshot polling or CPU bitmap blur. */
@Composable
fun Modifier.zeroAgentGlass(
    state: HazeState, radius: Int = 22,
    display: DisplaySetting = LocalSettings.current.displaySetting,
): Modifier {
    if (!display.liquidGlassEnabled) return this
    val power = LocalContext.current.getSystemService(PowerManager::class.java)
    val base = MaterialTheme.colorScheme.surfaceContainer
    val shape = RoundedCornerShape(radius.dp)
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = .27f), Color.White.copy(alpha = .025f), Color.White.copy(alpha = .12f)))
    // Bound the sampled layer before and after the effect. Never glass the whole drawer
    // or capture a parent containing its own glass children.
    val bounded = this.graphicsLayer {
        this.shape = shape
        clip = true
        // An offscreen layer masks the complete RenderEffect result on Android 12 too.
        compositingStrategy = CompositingStrategy.Offscreen
    }
    if (power?.isPowerSaveMode == true) return bounded.background(base.copy(alpha = display.safeGlassOpacity())).clip(shape)
    if (Build.VERSION.SDK_INT < 33) {
        val blur = HazeBlurStyle {
            backgroundColor(Color.Transparent)
            blurRadius(8.dp)
            noiseFactor(0f)
            colorEffects(emptyList())
            fallbackColorEffect(HazeColorEffect.tint(base.copy(alpha = display.safeGlassOpacity())))
        }
        return bounded.hazeBlur(input = HazeInput.Sources(state), style = blur,
            performanceMode = HazePerformanceMode.Performance, expandLayerBounds = false)
            .clip(shape).background(base.copy(alpha = display.safeGlassOpacity())).border(0.8.dp, edge, shape)
    }
    val style = GlassStyle.Material3(containerColor = Color.Transparent, tint = base.copy(alpha = display.safeGlassOpacity())) {
        optics(GlassDefaults.optics.copy(
            blurRadius = OpticalSizeValue.Fixed(5.dp),
            depth = OpticalSizeValue.Fixed(0.4f),
            refractionStrength = 0.5f,
            refractionHeightFraction = 0.25f,
            refractionDisplacement = 12.dp,
            refractionFoldStrength = 0.25f,
            refractionDetailIntensity = 0.35f,
        ))
        shape(RoundedCornerShape(radius.dp))
    }
    return bounded.hazeGlass(input = HazeInput.Sources(state), style = style,
        performanceMode = HazePerformanceMode.Performance, expandLayerBounds = false).clip(shape).border(0.8.dp, edge, shape)
}
