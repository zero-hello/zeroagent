package me.rerere.zeroagent.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import me.rerere.zeroagent.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
