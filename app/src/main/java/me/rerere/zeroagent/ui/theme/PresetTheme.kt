package me.rerere.zeroagent.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import me.rerere.zeroagent.ui.theme.presets.ZeroAgentThemePreset
import me.rerere.zeroagent.ui.theme.presets.AutumnThemePreset
import me.rerere.zeroagent.ui.theme.presets.BlackThemePreset
import me.rerere.zeroagent.ui.theme.presets.ClaudeThemePreset
import me.rerere.zeroagent.ui.theme.presets.MinimalThemePreset
import me.rerere.zeroagent.ui.theme.presets.OceanThemePreset
import me.rerere.zeroagent.ui.theme.presets.SakuraThemePreset
import me.rerere.zeroagent.ui.theme.presets.SpringThemePreset

data class PresetTheme(
    val id: String,
    val name: @Composable () -> Unit,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
) {
    fun getColorScheme(dark: Boolean): ColorScheme {
        return if (dark) standardDark else standardLight
    }
}

val PresetThemes by lazy {
    listOf(
        ZeroAgentThemePreset,
        SakuraThemePreset,
        OceanThemePreset,
        SpringThemePreset,
        AutumnThemePreset,
        BlackThemePreset,
        MinimalThemePreset,
        ClaudeThemePreset,
    )
}

fun findPresetTheme(id: String): PresetTheme {
    return PresetThemes.find { it.id == id } ?: ZeroAgentThemePreset
}

fun findThemeById(id: String, customThemes: List<CustomTheme>): PresetTheme? {
    PresetThemes.find { it.id == id }?.let { return it }
    val custom = customThemes.find { it.id == id } ?: return null
    return PresetTheme(
        id = custom.id,
        name = { androidx.compose.material3.Text(custom.name) },
        standardLight = custom.generateColorScheme(dark = false),
        standardDark = custom.generateColorScheme(dark = true),
    )
}
