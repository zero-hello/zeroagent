package me.rerere.zeroagent.ui.components.liquidglass

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * Interactive showcase implementing the authentic Kyant0 AndroidLiquidGlass experience:
 * - Real-time optical lens refraction & chromatic dispersion (CA)
 * - Gyroscope / accelerometer sensor-driven dynamic highlights
 * - Magnifier text/icon distortion over background
 * - Elastic gelatinous physics & squash-and-stretch
 */
@Composable
fun LiquidGlassShowcase(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableIntStateOf(0) }
    val categories = listOf("按鈕", "開關", "拉桿", "底欄", "控制中心", "元球融合")

    val context = LocalContext.current
    var customPainter: Painter? by remember { mutableStateOf(null) }
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                    if (bitmap != null) {
                        customPainter = BitmapPainter(bitmap)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    val sensor = rememberUISensor()
    val backdrop = rememberLayerBackdrop()

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- 1. Wallpaper Backdrop Layer ---
        if (customPainter != null) {
            Image(
                painter = customPainter!!,
                contentDescription = null,
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Built-in iOS 26 Floral / Aurora Wallpaper
            Box(
                Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF6ED0FA),
                                Color(0xFF388EFA),
                                Color(0xFF1E3A8A),
                                Color(0xFF0F172A)
                            ),
                            radius = 1600f
                        )
                    )
            ) {
                // Background decorative pattern to show off lens refraction & text magnification
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(6) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            repeat(4) { col ->
                                Box(
                                    Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if ((row + col) % 2 == 0) Color.White.copy(0.25f)
                                            else Color(0xFFFF9500).copy(0.35f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${(row * 4 + col + 1)}",
                                        color = Color.White.copy(0.85f),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Top Header & Tabs ---
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Liquid Glass (Backdrop)",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Text("✕", color = Color.White, fontSize = 20.sp)
                }
            }

            // Category Selector
            ScrollableTabRow(
                selectedTabIndex = selectedCategory,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                edgePadding = 16.dp,
                divider = {}
            ) {
                categories.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedCategory == index,
                        onClick = { selectedCategory = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedCategory == index) Color.White else Color.White.copy(0.6f),
                                fontWeight = if (selectedCategory == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // --- 3. Dynamic Interactive Content ---
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (selectedCategory) {
                    0 -> ButtonsShowcase(backdrop)
                    1 -> ToggleShowcase(backdrop)
                    2 -> SliderShowcase(backdrop)
                    3 -> BottomTabsShowcase(backdrop)
                    4 -> ControlCenterShowcase(backdrop, sensor.gravityAngle)
                    5 -> GlassEffectContainer(backdrop = backdrop)
                }
            }

            // --- 4. Bottom Action Bar ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                LiquidButton(
                    onClick = {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    backdrop = backdrop,
                    tint = Color(0xFF0088FF),
                    modifier = Modifier.height(52.dp)
                ) {
                    BasicText(
                        "選擇自訂桌布 (Pick an image)",
                        Modifier.padding(horizontal = 12.dp),
                        style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun ButtonsShowcase(backdrop: Backdrop) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LiquidButton(
            onClick = {},
            backdrop = backdrop,
            surfaceColor = Color.White.copy(0.3f)
        ) {
            BasicText("Surface Liquid Button", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
        }

        LiquidButton(
            onClick = {},
            backdrop = backdrop,
            tint = Color(0xFF0088FF)
        ) {
            BasicText("Tinted Liquid Button (Blue)", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
        }

        LiquidButton(
            onClick = {},
            backdrop = backdrop,
            tint = Color(0xFFFF8D28)
        ) {
            BasicText("Tinted Liquid Button (Orange)", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
        }

        LiquidButton(
            onClick = {},
            backdrop = backdrop,
            surfaceColor = Color.Black.copy(0.45f)
        ) {
            BasicText("Dark Frosted Liquid Button", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun ToggleShowcase(backdrop: Backdrop) {
    var checked1 by rememberSaveable { mutableStateOf(true) }
    var checked2 by rememberSaveable { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Text("開關上直接折射動態桌布 (Live Backdrop)", color = Color.White.copy(0.8f), fontSize = 14.sp)

        LiquidToggle(
            selected = { checked1 },
            onSelect = { checked1 = it },
            backdrop = backdrop
        )

        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedRectangle(28.dp))
                .background(Color.White.copy(0.15f))
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("卡片內次級液態折射 (Nested Backdrop)", color = Color.White, fontSize = 14.sp)
                LiquidToggle(
                    selected = { checked2 },
                    onSelect = { checked2 = it },
                    backdrop = backdrop
                )
            }
        }
    }
}

@Composable
private fun SliderShowcase(backdrop: Backdrop) {
    var refrHeight by rememberSaveable { mutableFloatStateOf(16f) }
    var refrAmount by rememberSaveable { mutableFloatStateOf(28f) }
    var dispersion by rememberSaveable { mutableFloatStateOf(50f) }

    val isDark = isSystemInDarkTheme()
    val previewBackdrop = rememberLayerBackdrop()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Live parameter adjusted lens preview panel
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedRectangle(24.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(24.dp) },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(
                            refractionHeight = refrHeight.dp.toPx(),
                            refractionAmount = refrAmount.dp.toPx(),
                            chromaticAberration = dispersion > 10f
                        )
                    },
                    highlight = { Highlight.Default },
                    onDrawSurface = {
                        drawRect(if (isDark) Color.Black.copy(0.3f) else Color.White.copy(0.25f))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "即時折射與色散透鏡效果\nRefraction: ${refrHeight.toInt()}dp / ${refrAmount.toInt()}dp",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text("Refraction Height (${refrHeight.toInt()}dp)", color = Color.White, fontSize = 13.sp)
        LiquidSlider(
            value = { refrHeight },
            onValueChange = { refrHeight = it },
            valueRange = 0f..40f,
            backdrop = backdrop
        )

        Text("Refraction Amount (${refrAmount.toInt()}dp)", color = Color.White, fontSize = 13.sp)
        LiquidSlider(
            value = { refrAmount },
            onValueChange = { refrAmount = it },
            valueRange = 0f..60f,
            backdrop = backdrop
        )

        Text("Chromatic Aberration (${dispersion.toInt()}%)", color = Color.White, fontSize = 13.sp)
        LiquidSlider(
            value = { dispersion },
            onValueChange = { dispersion = it },
            valueRange = 0f..100f,
            backdrop = backdrop
        )
    }
}

@Composable
private fun BottomTabsShowcase(backdrop: Backdrop) {
    val airplaneModeIcon = rememberVectorPainter(FlightIcon)
    val iconColorFilter = ColorFilter.tint(Color.White)
    var tabIndex3 by rememberSaveable { mutableIntStateOf(0) }
    var tabIndex4 by rememberSaveable { mutableIntStateOf(1) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("3-Tab Liquid Magnification", color = Color.White, fontSize = 14.sp)
        LiquidBottomTabs(
            selectedTabIndex = { tabIndex3 },
            onTabSelected = { tabIndex3 = it },
            backdrop = backdrop,
            tabsCount = 3
        ) {
            repeat(3) { index ->
                LiquidBottomTab(onClick = { tabIndex3 = index }) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                    )
                    BasicText("Tab ${index + 1}", style = TextStyle(Color.White, 12.sp))
                }
            }
        }

        Text("4-Tab Liquid Magnification", color = Color.White, fontSize = 14.sp)
        LiquidBottomTabs(
            selectedTabIndex = { tabIndex4 },
            onTabSelected = { tabIndex4 = it },
            backdrop = backdrop,
            tabsCount = 4
        ) {
            repeat(4) { index ->
                LiquidBottomTab(onClick = { tabIndex4 = index }) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .paint(airplaneModeIcon, colorFilter = iconColorFilter)
                    )
                    BasicText("Tab ${index + 1}", style = TextStyle(Color.White, 12.sp))
                }
            }
        }
    }
}

@Composable
private fun ControlCenterShowcase(backdrop: Backdrop, gravityAngle: Float) {
    val airplaneIcon = rememberVectorPainter(FlightIcon)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("陀螺儀動態高光控制中心 (Tilt device to see glisten)", color = Color.White.copy(0.85f), fontSize = 13.sp)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Large 2x2 Liquid Connectivity Plate
            Box(
                Modifier
                    .weight(1f)
                    .height(150.dp)
                    .clip(RoundedRectangle(26.dp))
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(26.dp) },
                        effects = {
                            vibrancy()
                            blur(12.dp.toPx())
                            lens(14.dp.toPx(), 22.dp.toPx(), chromaticAberration = true)
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = 0.85f, style = HighlightStyle.Default(angle = gravityAngle))
                        },
                        shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(0.12f)) },
                        onDrawSurface = { drawRect(Color.White.copy(0.12f)) }
                    )
                    .padding(14.dp)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF007AFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(22.dp).paint(airplaneIcon, colorFilter = ColorFilter.tint(Color.White)))
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF007AFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(22.dp).paint(airplaneIcon, colorFilter = ColorFilter.tint(Color.White)))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(22.dp).paint(airplaneIcon, colorFilter = ColorFilter.tint(Color.White)))
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34C759)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(22.dp).paint(airplaneIcon, colorFilter = ColorFilter.tint(Color.White)))
                        }
                    }
                }
            }

            // Media & Focus Tiles
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(69.dp)
                        .clip(RoundedRectangle(22.dp))
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(22.dp) },
                            effects = {
                                blur(12.dp.toPx())
                                lens(10.dp.toPx(), 18.dp.toPx(), chromaticAberration = true)
                            },
                            highlight = { Highlight.Default.copy(style = HighlightStyle.Default(angle = gravityAngle)) },
                            onDrawSurface = { drawRect(Color.White.copy(0.12f)) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Now Playing", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(69.dp)
                        .clip(RoundedRectangle(22.dp))
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(22.dp) },
                            effects = {
                                blur(12.dp.toPx())
                                lens(10.dp.toPx(), 18.dp.toPx(), chromaticAberration = true)
                            },
                            highlight = { Highlight.Default.copy(style = HighlightStyle.Default(angle = gravityAngle)) },
                            onDrawSurface = { drawRect(Color.White.copy(0.12f)) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Focus / 專注模式", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
