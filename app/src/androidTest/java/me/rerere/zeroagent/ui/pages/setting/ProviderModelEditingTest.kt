package me.rerere.zeroagent.ui.pages.setting

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dokar.sonner.rememberToasterState
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.context.LocalToaster
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProviderModelEditingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modelCanBeRemovedWithoutDiscoveringASwipeGesture() {
        val model = Model(modelId = "example-model", displayName = "Example")
        var deletes = 0
        var delete = ""
        var confirm = ""
        compose.setContent {
            val resources = LocalResources.current
            delete = resources.getString(R.string.chat_page_delete)
            confirm = resources.getString(R.string.confirm_delete)
            MaterialTheme {
                ModelCard(model, onDelete = { deletes++ }, onEdit = {}, parentProvider = ProviderSetting.OpenAI())
            }
        }
        compose.onNodeWithContentDescription(delete).performClick()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText(confirm).performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
    }

    @Test fun modelSettingCanBeClearedAndShowsAnEmptySelection() {
        val model = Model(modelId = "example-model", displayName = "Example")
        val selected = mutableStateOf(model.id)
        val provider = ProviderSetting.OpenAI(models = listOf(model))
        var clear = ""
        var empty = ""
        compose.setContent {
            val resources = LocalResources.current
            clear = resources.getString(R.string.accessibility_clear_model_selection)
            empty = resources.getString(R.string.model_list_select_model)
            MaterialTheme {
                ModelSettingItem("Chat", "Choose a model", selected.value, listOf(provider), { selected.value = it.id })
            }
        }
        compose.onNodeWithContentDescription(clear).performClick()
        compose.onNodeWithText(empty).assertExists()
        compose.onNodeWithContentDescription(clear).assertDoesNotExist()
        compose.runOnIdle { assertNotEquals(model.id, selected.value) }
    }

    @Test fun customProviderFormCanScrollToCredentialsAndSaveTheSelectedProtocol() {
        var saved: ProviderSetting? = null
        var add = ""
        var open = ""
        var name = ""
        var key = ""
        compose.setContent {
            val resources = LocalResources.current
            add = resources.getString(R.string.setting_provider_page_add)
            open = resources.getString(R.string.accessibility_add_provider)
            name = resources.getString(R.string.setting_provider_page_name)
            key = resources.getString(R.string.setting_provider_page_api_key)
            CompositionLocalProvider(LocalToaster provides rememberToasterState()) {
                MaterialTheme { AddButton { saved = it } }
            }
        }
        compose.onNodeWithContentDescription(open).performClick()
        compose.onNodeWithText("Anthropic Messages").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(name)).performScrollTo().performTextReplacement("My proxy")
        compose.onNode(hasSetTextAction() and hasText(key)).performScrollTo().performTextReplacement("fixture-key")
        compose.onNodeWithText(add).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(saved is ProviderSetting.Claude)
            assertEquals("My proxy", saved?.name)
            assertEquals("fixture-key", (saved as ProviderSetting.Claude).apiKey)
        }
    }
}
