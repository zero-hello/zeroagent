package me.rerere.zeroagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import me.rerere.zeroagent.R

/** Manrope (SIL OFL 1.1). Android supplies fallback glyphs for CJK scripts. */
@OptIn(ExperimentalTextApi::class)
val ZeroAgentFontFamily = FontFamily(
    Font(R.font.manrope, weight = FontWeight(400), variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, weight = FontWeight(500), variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, weight = FontWeight(600), variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, weight = FontWeight(700), variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

val base = Typography()
val Typography = zeroTypography(ZeroAgentFontFamily)

internal fun zeroTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = fontFamily),
    displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
    displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = fontFamily),
    displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
    displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = fontFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = fontFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
    headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = fontFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
    headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = fontFamily),
    titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
    titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = fontFamily),
    titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
    titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = fontFamily),
    titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
    titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = fontFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, lineHeight = 26.sp),
    bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = fontFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, lineHeight = 22.sp),
    bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = fontFamily),
    bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
    bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = fontFamily),
    labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
    labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = fontFamily),
    labelMedium = base.labelMedium.copy(fontFamily = fontFamily, fontFeatureSettings = "tnum"),
    labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = fontFamily),
    labelSmall = base.labelSmall.copy(fontFamily = fontFamily, fontFeatureSettings = "tnum"),
    labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = fontFamily),
)

@OptIn(ExperimentalTextApi::class)
val JetbrainsMono = FontFamily(
    Font(
        resId = R.font.jetbrains_mono,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight),
        )
    )
)
