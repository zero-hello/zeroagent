package me.rerere.zeroagent.ui.theme

/** Stable IDs retained for settings and backups created before the Zero theme. */
data class PresetTheme(val id: String)

val PresetThemes = listOf(
    "zeroagent", "sakura", "ocean", "spring", "autumn", "black", "minimal", "claude",
).map(::PresetTheme)
