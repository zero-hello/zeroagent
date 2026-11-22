package me.rerere.zeroagent.ui.motion

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.zeroagent.ui.components.ui.rememberMotionEnabled
import kotlin.math.cos
import kotlin.math.sin

/** 流體空間扭曲；時鐘僅在 draw 讀取，系統移除動畫／離開前景時停止。 */
@Composable
fun FluidBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val time = remember { mutableFloatStateOf(0f) }
    val motion = rememberMotionEnabled()
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            time.floatValue += ((now - previous) / 1_000_000_000f).coerceIn(0f, .1f)
            previous = now
            delay(MotionTokens.BackgroundFrameMillis)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(scheme.background, scheme.primary, scheme.tertiary, scheme.secondary)
    Box(modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= 33) ShaderFluid(time, palette)
        else CanvasFluid(time, palette)
        content()
    }
}

@RequiresApi(33)
@Composable
private fun ShaderFluid(time: State<Float>, palette: List<Color>) {
    val shader = remember { runCatching { RuntimeShader(FLUID_SHADER) }.getOrNull() }
    if (shader == null) { CanvasFluid(time, palette); return }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(Modifier.fillMaxSize()) {
        shader.setFloatUniform("resolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        shader.setFloatUniform("time", time.value)
        palette.forEachIndexed { index, c -> shader.setFloatUniform("c$index", c.red, c.green, c.blue, 1f) }
        drawRect(brush)
    }
}

@Composable
private fun CanvasFluid(time: State<Float>, palette: List<Color>) {
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value * .20f
        drawRect(palette[0])
        repeat(4) { index ->
            val p = t + index * 1.8f
            val center = Offset(size.width * (.5f + .48f * sin(p)),
                size.height * (.5f + .42f * cos(p * .71f)))
            val radius = size.maxDimension * (.60f + .10f * sin(p * .6f))
            drawCircle(Brush.radialGradient(listOf(palette[1 + index % 3].copy(alpha = .32f),
                Color.Transparent), center, radius), radius, center)
        }
    }
}

// AGSL 採連續座標變形與柔和波帶，不是只循環換色。保留底色維持文字對比。
private const val FLUID_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float4 c0;
uniform float4 c1;
uniform float4 c2;
uniform float4 c3;
half4 main(float2 coord) {
    float2 uv = coord / resolution;
    float t = time * 0.22;
    float2 p = uv;
    p.x += 0.18 * sin(uv.y * 5.0 + t) + 0.09 * cos(uv.y * 9.0 - t * 0.7);
    p.y += 0.16 * cos(uv.x * 4.0 - t * 0.8);
    float da = p.x - 0.27 - 0.22 * sin(p.y * 3.0 + t);
    float db = p.x - 0.78 - 0.20 * cos(p.y * 4.0 - t);
    float dc = p.y - 0.6 - 0.24 * sin(p.x * 4.0 - t * 0.8);
    float a = exp(-9.0 * da * da);
    float b = exp(-10.0 * db * db);
    float c = exp(-12.0 * dc * dc);
    float3 rgb = mix(c0.rgb, c1.rgb, a * 0.30);
    rgb = mix(rgb, c2.rgb, b * 0.27);
    rgb = mix(rgb, c3.rgb, c * 0.22);
    return half4(rgb, 1.0);
}
"""
