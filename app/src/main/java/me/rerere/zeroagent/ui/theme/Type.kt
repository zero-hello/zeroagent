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
val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = ZeroAgentFontFamily),
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    displayMedium = base.displayMedium.copy(fontFamily = ZeroAgentFontFamily),
    displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    displaySmall = base.displaySmall.copy(fontFamily = ZeroAgentFontFamily),
    displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = ZeroAgentFontFamily),
    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = ZeroAgentFontFamily),
    headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = ZeroAgentFontFamily),
    headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    titleLarge = base.titleLarge.copy(fontFamily = ZeroAgentFontFamily),
    titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    titleMedium = base.titleMedium.copy(fontFamily = ZeroAgentFontFamily),
    titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    titleSmall = base.titleSmall.copy(fontFamily = ZeroAgentFontFamily),
    titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = ZeroAgentFontFamily, lineHeight = 26.sp),
    bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = ZeroAgentFontFamily, lineHeight = 22.sp),
    bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    bodySmall = base.bodySmall.copy(fontFamily = ZeroAgentFontFamily),
    bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    labelLarge = base.labelLarge.copy(fontFamily = ZeroAgentFontFamily),
    labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    labelMedium = base.labelMedium.copy(fontFamily = ZeroAgentFontFamily),
    labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = ZeroAgentFontFamily),
    labelSmall = base.labelSmall.copy(fontFamily = ZeroAgentFontFamily),
    labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = ZeroAgentFontFamily),
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
