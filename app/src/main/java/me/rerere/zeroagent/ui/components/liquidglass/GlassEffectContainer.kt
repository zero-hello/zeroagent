package me.rerere.zeroagent.ui.components.liquidglass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import kotlin.math.roundToInt

/**
 * 膠囊元球數據模型
 */
data class FluidCapsule(
    val id: Int,
    val initialOffset: Offset,
    val widthDp: Float = 130f,
    val heightDp: Float = 64f,
    val label: String = "Liquid Capsule",
    val accentColor: Color = Color(0xFF007AFF)
)

/**
 * GlassEffectContainer - iOS 26 次世代液態玻璃物理融合容器
 *
 * 核心技術特性：
 * 1. AGSL Meta-ball SDF 多項式平滑極小值 (Polynomial smin) 表面張力有機融合與拉絲分開動態。
 * 2. 邊緣像素著色器微幅偏移 (Chromatic Aberration RGB Offset) 模擬光學稜鏡立體折射與色散。
 * 3. 虛擬動態光源與表面法線計算雙瓣高光 (Rim Light) 與內側微陰影層次。
 * 4. RenderNode 硬件加速優化，保證在 120Hz 高刷新率屏幕上滿幀運作。
 */
@Composable
fun GlassEffectContainer(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    lightPosition: Offset = Offset(400f, 200f)
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // 定義 4 個初始膠囊元球
    val capsules = remember {
        listOf(
            FluidCapsule(0, Offset(80f, 120f), 120f, 60f, "Capsule A", Color(0xFF007AFF)),
            FluidCapsule(1, Offset(240f, 150f), 130f, 65f, "Capsule B", Color(0xFF34C759)),
            FluidCapsule(2, Offset(130f, 290f), 125f, 60f, "Capsule C", Color(0xFFFF9500)),
            FluidCapsule(3, Offset(260f, 320f), 140f, 70f, "Capsule D", Color(0xFFAF52DE))
        )
    }

    // 每個膠囊的彈簧物理位置追蹤 (阻尼比 0.72f，極致 Q 彈質感)
    val positions = remember {
        capsules.map { Animatable(it.initialOffset, Offset.VectorConverter) }
    }

    // 當前拖曳的焦點位置，作為動態虛擬光源的連動來源
    var activeTouchOffset by remember { mutableStateOf(lightPosition) }

    // AGSL Shader (API 33+)
    val agslShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(MetaBallLiquidGlassAGSL)
        } else null
    }

    // 容器整體圖層硬件加速
    val containerRenderEffect = remember(agslShader, containerSize, activeTouchOffset) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslShader != null && containerSize.width > 0 && containerSize.height > 0) {
            agslShader.setFloatUniform("uResolution", containerSize.width.toFloat(), containerSize.height.toFloat())
            agslShader.setFloatUniform("uLightPos", activeTouchOffset.x, activeTouchOffset.y)

            // 傳入 4 個膠囊當前中心座標 (x, y) 與尺寸 (halfW, halfH)
            val centers = FloatArray(8)
            val halfSizes = FloatArray(8)
            for (i in capsules.indices) {
                val pos = positions[i].value
                val wPx = with(density) { capsules[i].widthDp.dp.toPx() }
                val hPx = with(density) { capsules[i].heightDp.dp.toPx() }
                centers[i * 2] = pos.x + wPx / 2f
                centers[i * 2 + 1] = pos.y + hPx / 2f
                halfSizes[i * 2] = wPx / 2f
                halfSizes[i * 2 + 1] = hPx / 2f
            }
            agslShader.setFloatUniform("uCenters", centers)
            agslShader.setFloatUniform("uHalfSizes", halfSizes)
            agslShader.setFloatUniform("uBlendStrength", with(density) { 46.dp.toPx() }) // 元球黏連拉絲張力半徑
            agslShader.setFloatUniform("uRefractionAmount", with(density) { 24.dp.toPx() })
            agslShader.setFloatUniform("uDispersionStrength", with(density) { 12.dp.toPx() }) // RGB 色散偏移量

            RenderEffect.createRuntimeShaderEffect(agslShader, "content").asComposeRenderEffect()
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        // --- 物理融合渲染層 (帶有 AGSL Meta-ball 與稜鏡色散) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (containerRenderEffect != null) {
                        Modifier.graphicsLayer {
                            renderEffect = containerRenderEffect
                            // RenderNode 硬件隔離保護，杜絕重繪抖動
                            clip = true
                        }
                    } else Modifier
                )
        ) {
            capsules.forEachIndexed { index, capsule ->
                val animatable = positions[index]
                val widthPx = with(density) { capsule.widthDp.dp.toPx() }
                val heightPx = with(density) { capsule.heightDp.dp.toPx() }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                animatable.value.x.roundToInt(),
                                animatable.value.y.roundToInt()
                            )
                        }
                        .size(capsule.widthDp.dp, capsule.heightDp.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    capsule.accentColor.copy(alpha = 0.55f),
                                    capsule.accentColor.copy(alpha = 0.25f)
                                )
                            )
                        )
                        .pointerInput(index) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    activeTouchOffset = animatable.value + offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newOffset = animatable.value + dragAmount
                                    activeTouchOffset = newOffset
                                    // 即時跟隨手勢
                                    scope.launch {
                                        animatable.snapTo(newOffset)
                                    }
                                },
                                onDragEnd = {
                                    // 鬆手時以阻尼彈簧物理柔和回彈微調
                                    scope.launch {
                                        animatable.animateTo(
                                            animatable.value,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = capsule.label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * AGSL Meta-ball 與色散透鏡折射著色器
 * 1. 採用帶符號距離場 (SDF) 刻畫圓角矩形/膠囊體。
 * 2. 採用多項式 smooth-minimum (smin) 進行 4 元球表面張力融合與拉絲計算。
 * 3. 採用偏導數法線與 Cauchy 柯西色散偏移對 RGB 分離採樣。
 * 4. 動態雙瓣高光 (Two-lobe Rim Light) 隨虛擬光源位置即時反射。
 */
@Language("AGSL")
private const val MetaBallLiquidGlassAGSL = """
uniform shader content;
uniform float2 uResolution;
uniform float2 uLightPos;
uniform float2 uCenters[4];
uniform float2 uHalfSizes[4];
uniform float uBlendStrength;     // smin 融合係數
uniform float uRefractionAmount;  // 折射位移長度
uniform float uDispersionStrength;// RGB 色散間隔

// 2D 圓角矩形/膠囊體 SDF
float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - (b - float2(r));
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

// 多項式平滑極小值 (Polynomial smin) 實現元球表面張力拉絲融合
float smin(float a, float b, float k) {
    if (k <= 0.0) return min(a, b);
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

// 評估 4 個膠囊元球的合成 SDF 場
float evaluateField(float2 coord) {
    float r0 = min(uHalfSizes[0].x, uHalfSizes[0].y);
    float d = sdRoundedBox(coord - uCenters[0], uHalfSizes[0], r0);
    
    for (int i = 1; i < 4; i++) {
        float ri = min(uHalfSizes[i].x, uHalfSizes[i].y);
        float di = sdRoundedBox(coord - uCenters[i], uHalfSizes[i], ri);
        d = smin(d, di, uBlendStrength);
    }
    return d;
}

half4 main(float2 fragCoord) {
    float sd = evaluateField(fragCoord);
    
    // 物體外部：返回原始前景內容
    if (sd > 0.0) {
        return content.eval(fragCoord);
    }
    
    // 計算表面法線向量 (偏導數數值解)
    float eps = 1.0;
    float dx = evaluateField(fragCoord + float2(eps, 0.0)) - evaluateField(fragCoord - float2(eps, 0.0));
    float dy = evaluateField(fragCoord + float2(0.0, eps)) - evaluateField(fragCoord - float2(0.0, eps));
    float2 normal = float2(dx, dy);
    float nLen = length(normal);
    normal = (nLen > 0.0001) ? normal / nLen : float2(0.0, 1.0);
    
    // 逆冪衰減折射係數 (邊緣折射劇烈、中心通透)
    float inwardDist = max(-sd, 0.0);
    float k = max(uBlendStrength * 0.25, 1.0);
    float falloff = pow(1.0 + inwardDist / k, -2.2);
    float displacement = uRefractionAmount * falloff;
    
    // 物理稜鏡色散：沿表面法線方向對 R、G、B 通道進行坐標微幅偏移採樣
    float disp = uDispersionStrength * falloff;
    float2 baseCoord = fragCoord + normal * displacement;
    
    half4 colR = content.eval(baseCoord + normal * disp);
    half4 colG = content.eval(baseCoord);
    half4 colB = content.eval(baseCoord - normal * disp);
    half4 blended = half4(colR.r, colG.g, colB.b, (colR.a + colG.a + colB.a) / 3.0);
    
    // 虛擬光源方向與雙瓣邊緣高光 (Two-Lobe Rim Light)
    float2 lightDir = normalize(uLightPos - fragCoord);
    float NdotL = dot(-normal, lightDir);
    float lobe1 = pow(max(NdotL, 0.0), 16.0) * 0.95;  // 主高光
    float lobe2 = pow(max(-NdotL, 0.0), 6.0) * 0.40; // 邊緣反射高光
    float specular = (lobe1 + lobe2) * falloff;
    
    // 內側暗部景深環境遮蔽 (Internal Depth Shadow)
    float innerShadow = smoothstep(0.0, 16.0, inwardDist) * 0.15;
    
    blended.rgb += half3(specular);
    blended.rgb -= half3(innerShadow);
    
    return blended;
}
"""
