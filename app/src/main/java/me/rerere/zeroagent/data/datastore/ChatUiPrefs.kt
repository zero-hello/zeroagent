package me.rerere.zeroagent.data.datastore

import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.AI_LOG_LEVEL
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.CUSTOM_THEMES
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.DEVELOPER_DISPLAY_OPTIONS
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.DEVELOPER_MODE
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.DISPLAY_SETTING
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.DYNAMIC_COLOR
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.FLOATING_BALL_COLOR
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.FLOATING_BALL_ENABLED
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.FLOATING_BALL_HIDDEN_ALPHA
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.FLOATING_BALL_ICON_PATH
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.FLOATING_BALL_IDLE_SECONDS
import me.rerere.zeroagent.data.datastore.SettingsStore.Companion.THEME_ID
import me.rerere.zeroagent.ui.theme.CustomTheme
import me.rerere.zeroagent.ui.theme.PresetThemes
import me.rerere.zeroagent.utils.JsonInstant

/** Reads and writes chat appearance, floating window and diagnostics preferences. */
internal object ChatUiPrefs {
    private const val TAG = "PreferencesStore"

    fun writeAppearance(preferences: MutablePreferences, settings: Settings) {
        preferences[DYNAMIC_COLOR] = settings.dynamicColor
        preferences[THEME_ID] = settings.themeId
        preferences[CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
        preferences[DEVELOPER_MODE] = settings.developerMode
        preferences[DEVELOPER_DISPLAY_OPTIONS] = JsonInstant.encodeToString(settings.developerDisplayOptions)
        preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(
            settings.displaySetting.copy(
                pasteLongTextThreshold = settings.displaySetting.pasteLongTextThreshold.coerceIn(100, 10000)
            )
        )
    }

    fun writeOverlayAndDiagnostics(preferences: MutablePreferences, settings: Settings) {
        preferences[FLOATING_BALL_ENABLED] = settings.floatingBallEnabled
        preferences[FLOATING_BALL_IDLE_SECONDS] = settings.floatingBallIdleSeconds.coerceIn(0, 60)
        preferences[FLOATING_BALL_HIDDEN_ALPHA] = settings.floatingBallHiddenAlpha.coerceIn(10, 100)
        preferences[FLOATING_BALL_COLOR] = settings.floatingBallColor?.toString() ?: ""
        preferences[FLOATING_BALL_ICON_PATH] = settings.floatingBallIconPath
        preferences[AI_LOG_LEVEL] = settings.aiLogLevel.preferenceName
    }

    fun themeId(preferences: Preferences): String = preferences[THEME_ID] ?: PresetThemes[0].id

    fun dynamicColor(preferences: Preferences): Boolean = preferences[DYNAMIC_COLOR] ?: false

    fun customThemes(preferences: Preferences): List<CustomTheme> = preferences[CUSTOM_THEMES]?.let { raw ->
        runCatching { JsonInstant.decodeFromString<List<CustomTheme>>(raw) }.getOrElse {
            Log.w(TAG, "Failed to decode customThemes, using default", it)
            emptyList()
        }
    } ?: emptyList()

    fun developerMode(preferences: Preferences): Boolean = preferences[DEVELOPER_MODE] == true

    fun developerDisplayOptions(preferences: Preferences): DeveloperDisplayOptions = preferences[DEVELOPER_DISPLAY_OPTIONS]
        ?.let { DeveloperDisplayOptions.fromPreference(it) } ?: DeveloperDisplayOptions()

    fun floatingBallEnabled(preferences: Preferences): Boolean = preferences[FLOATING_BALL_ENABLED] == true

    fun floatingBallIdleSeconds(preferences: Preferences): Int = preferences[FLOATING_BALL_IDLE_SECONDS] ?: 3

    fun floatingBallHiddenAlpha(preferences: Preferences): Int = preferences[FLOATING_BALL_HIDDEN_ALPHA] ?: 50

    fun floatingBallColor(preferences: Preferences): Int? = preferences[FLOATING_BALL_COLOR]?.takeIf { it.isNotBlank() }?.toIntOrNull()

    fun floatingBallIconPath(preferences: Preferences): String = preferences[FLOATING_BALL_ICON_PATH] ?: ""

    fun aiLogLevel(preferences: Preferences): AiLogLevel = AiLogLevel.fromPreference(preferences[AI_LOG_LEVEL])

    // `enableLatexRendering` is forced on: rendering LaTeX is strictly better than
    // showing the raw `$...$` source, so the switch was removed and the stored value
    // ignored. The field is kept on DisplaySetting so old blobs still decode.
    fun displaySetting(preferences: Preferences): DisplaySetting = runCatching {
        JsonInstant.decodeFromString<DisplaySetting>(preferences[DISPLAY_SETTING] ?: "{}")
    }.getOrElse {
        Log.w(TAG, "Failed to decode displaySetting, using default", it)
        DisplaySetting()
    }.copy(enableLatexRendering = true)
}
