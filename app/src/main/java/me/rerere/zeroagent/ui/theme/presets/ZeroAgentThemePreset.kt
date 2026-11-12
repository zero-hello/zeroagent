package me.rerere.zeroagent.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import me.rerere.zeroagent.ui.theme.PresetTheme

val ZeroAgentThemePreset = PresetTheme(
    id = "zeroagent",
    name = { Text("ZeroAgent") },
    standardLight = lightColorScheme(
        primary = Color(0xFF5269C8), onPrimary = Color.White,
        primaryContainer = Color(0xFFDDE6FF), onPrimaryContainer = Color(0xFF20283A),
        secondary = Color(0xFF626B7C), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9EDF8), onSecondaryContainer = Color(0xFF20283A),
        tertiary = Color(0xFF426975), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD9EDF1), onTertiaryContainer = Color(0xFF203B43),
        background = Color(0xFFF7F8FC), onBackground = Color(0xFF20283A),
        surface = Color(0xFFF7F8FC), onSurface = Color(0xFF20283A),
        surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFF0F2FA),
        surfaceContainerHigh = Color(0xFFE9EDF8), surfaceContainerHighest = Color(0xFFE1E5EF),
        surfaceBright = Color.White, surfaceDim = Color(0xFFDEE2EB),
        surfaceContainerLowest = Color.White,
        surfaceVariant = Color(0xFFE9EDF8), onSurfaceVariant = Color(0xFF626B7C),
        outline = Color(0xFF737D94), outlineVariant = Color(0xFFE1E5EF),
    ),
    standardDark = darkColorScheme(
        primary = Color(0xFFB7C4FF), onPrimary = Color(0xFF233A8A),
        primaryContainer = Color(0xFF334995), onPrimaryContainer = Color(0xFFDDE6FF),
        secondary = Color(0xFFC1C6D8), onSecondary = Color(0xFF2B3040),
        secondaryContainer = Color(0xFF303547), onSecondaryContainer = Color(0xFFE9EDF8),
        tertiary = Color(0xFFA5CDD4), onTertiary = Color(0xFF173840),
        tertiaryContainer = Color(0xFF304F58), onTertiaryContainer = Color(0xFFD9EDF1),
        background = Color(0xFF171923), onBackground = Color(0xFFE9EDF8),
        surface = Color(0xFF171923), onSurface = Color(0xFFE9EDF8),
        surfaceContainer = Color(0xFF262A39), surfaceContainerLow = Color(0xFF1E2130),
        surfaceContainerHigh = Color(0xFF303547), surfaceContainerHighest = Color(0xFF3B4054),
        surfaceBright = Color(0xFF3B4054), surfaceDim = Color(0xFF171923),
        surfaceContainerLowest = Color(0xFF11131C),
        surfaceVariant = Color(0xFF303547), onSurfaceVariant = Color(0xFFC1C6D8),
        outlineVariant = Color(0xFF444B62),
    ),
)
