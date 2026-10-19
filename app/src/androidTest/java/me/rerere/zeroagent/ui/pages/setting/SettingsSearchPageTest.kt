package me.rerere.zeroagent.ui.pages.setting

import android.content.res.Resources
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.Navigator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Test the production search page without adding a navigation animation clock. */
@RunWith(AndroidJUnit4::class)
class SettingsSearchPageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun colorModeAliasFindsThemeAndKeepsItsExistingRoute() {
        val (resources, stack) = showSearch()
        compose.onNodeWithTag("settings_search_input")
            .performTextInput(resources.getString(R.string.setting_page_color_mode))
        compose.onNode(hasText(resources.getString(R.string.setting_page_preferences_theme)) and
            hasClickAction() and !hasSetTextAction()).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Screen.SettingPreferencesTheme, stack.last()) }
    }

    @Test
    fun unmatchedQueryShowsTheLocalizedEmptyState() {
        val (resources, _) = showSearch()
        compose.onNodeWithTag("settings_search_input").performTextInput("unmatched-setting-93e7")
        compose.onNodeWithText(resources.getString(R.string.settings_search_no_results)).assertIsDisplayed()
    }

    @Test
    fun contextualMemoryAliasUsesTheCurrentModelMemoryRoute() {
        val (resources, stack) = showSearch()
        val modelId = org.koin.java.KoinJavaComponent.getKoin().get<SettingsStore>()
            .settingsFlow.value.chatModelId.toString()

        compose.onNodeWithTag("settings_search_input")
            .performTextInput(resources.getString(R.string.memory12_title))
        compose.onNode(hasText(resources.getString(R.string.zeroagent11_model_memory)) and
            hasClickAction() and !hasSetTextAction()).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Screen.ModelMemory(modelId), stack.last()) }
    }

    @Test
    fun nativeVoiceAliasOpensSpeechConfigurationWithoutStartingACall() {
        val (resources, stack) = showSearch()
        compose.onNodeWithTag("settings_search_input")
            .performTextInput(resources.getString(R.string.zeroagent11_voice_conversation))
        compose.onNode(hasText(resources.getString(R.string.zeroagent11_speech_services)) and
            hasClickAction() and !hasSetTextAction()).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Screen.SettingSpeech, stack.last()) }
    }

    @Test
    fun GeminiAliasOpensProviderSettings() {
        val (resources, stack) = showSearch()
        compose.onNodeWithTag("settings_search_input")
            .performTextInput(resources.getString(R.string.gemini_enable))
        compose.onNode(hasText(resources.getString(R.string.setting_page_providers)) and
            hasClickAction() and !hasSetTextAction()).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Screen.SettingProvider, stack.last()) }
    }

    private fun showSearch(): Pair<Resources, List<NavKey>> {
        val stack = mutableStateListOf<NavKey>(Screen.SettingsSearch)
        lateinit var resources: Resources
        compose.setContent {
            resources = LocalResources.current
            CompositionLocalProvider(LocalNavController provides Navigator(stack)) {
                MaterialTheme { SettingsSearchPage() }
            }
        }
        val store = org.koin.java.KoinJavaComponent.getKoin().get<SettingsStore>()
        compose.waitUntil(timeoutMillis = 10_000) { !store.settingsFlow.value.init }
        compose.waitForIdle()
        return resources to stack
    }
}
