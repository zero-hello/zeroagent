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
        secondary = Color(0xFF626B7C), secondaryContainer = Color(0xFFE9EDF8),
        background = Color(0xFFF7F8FC), onBackground = Color(0xFF20283A),
        surface = Color(0xFFF7F8FC), onSurface = Color(0xFF20283A),
        surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFF0F2FA),
        surfaceContainerHigh = Color(0xFFE9EDF8), surfaceContainerHighest = Color(0xFFE1E5EF),
        surfaceVariant = Color(0xFFE9EDF8), onSurfaceVariant = Color(0xFF626B7C),
        outline = Color(0xFF737D94), outlineVariant = Color(0xFFE1E5EF),
    ),
    standardDark = darkColorScheme(
        primary = Color(0xFFB7C4FF), onPrimary = Color(0xFF233A8A),
        primaryContainer = Color(0xFF334995), onPrimaryContainer = Color(0xFFDDE6FF),
        background = Color(0xFF171923), onBackground = Color(0xFFE9EDF8),
        surface = Color(0xFF171923), onSurface = Color(0xFFE9EDF8),
        surfaceContainer = Color(0xFF262A39), surfaceContainerLow = Color(0xFF1E2130),
        surfaceContainerHigh = Color(0xFF303547), surfaceContainerHighest = Color(0xFF3B4054),
        surfaceVariant = Color(0xFF303547), onSurfaceVariant = Color(0xFFC1C6D8),
        outlineVariant = Color(0xFF444B62),
    ),
)
