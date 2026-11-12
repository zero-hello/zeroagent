package me.rerere.zeroagent.ui.pages.setting.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dokar.sonner.rememberToasterState
import me.rerere.ai.provider.ProviderSetting
import me.rerere.zeroagent.R
import me.rerere.zeroagent.ui.context.LocalToaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class ProviderConnectionFieldsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fieldsKeepNamesUntrimmedAndTrimCredentialsAndEndpoints() {
        val name = mutableStateOf("Name")
        val key = mutableStateOf("key")
        val url = mutableStateOf("https://proxy.example/v1")
        compose.setContent {
            MaterialTheme { Column {
                ProviderNameField(name.value, { name.value = it }, Modifier.testTag("name"))
                ProviderApiKeyField(key.value, { key.value = it }, Modifier.testTag("key"))
                ProviderBaseUrlField(url.value, { url.value = it }, Modifier.testTag("url"))
            } }
        }
        compose.onNodeWithTag("name").performTextReplacement("  My provider  ")
        compose.onNodeWithTag("key").performTextReplacement("  test-key  ")
        compose.onNodeWithTag("url").performTextReplacement("  https://proxy.example/v1beta  ")
        compose.runOnIdle {
            assertEquals("  My provider  ", name.value)
            assertEquals("test-key", key.value)
            assertEquals("https://proxy.example/v1beta", url.value)
        }
    }

    @Test fun passwordVisibilityChangesWithoutEditingTheCredential() {
        var show = ""
        var hide = ""
        val key = mutableStateOf("test-key")
        compose.setContent {
            val resources = LocalResources.current
            show = resources.getString(R.string.accessibility_show_password)
            hide = resources.getString(R.string.accessibility_hide_password)
            MaterialTheme { ProviderApiKeyField(key.value, { key.value = it }, Modifier.testTag("key")) }
        }
        val password = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
        compose.onNodeWithTag("key").assert(password)
        fun displayedText(): String {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("key").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.text.text
        }
        assertNotEquals("test-key", displayedText())
        compose.onNodeWithContentDescription(show).performClick()
        compose.onNodeWithContentDescription(hide).assertExists()
        assertEquals("test-key", displayedText())
        compose.onNodeWithContentDescription(hide).performClick()
        compose.onNodeWithContentDescription(show).assertExists()
        assertNotEquals("test-key", displayedText())
        compose.runOnIdle { assertEquals("test-key", key.value) }
    }

    @Test fun googleAndVertexModesKeepTheirExistingFieldVisibilityAndValues() {
        val provider = mutableStateOf(ProviderSetting.Google(apiKey = "test-key"))
        var keyLabel = ""
        var urlLabel = ""
        compose.setContent {
            val resources = LocalResources.current
            keyLabel = resources.getString(R.string.setting_provider_page_api_key)
            urlLabel = resources.getString(R.string.setting_provider_page_api_base_url)
            CompositionLocalProvider(LocalToaster provides rememberToasterState()) {
                MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProviderConfigure(provider.value) { provider.value = it as ProviderSetting.Google }
                } }
            }
        }
        fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
        field(keyLabel).assertExists()
        field(urlLabel).assertExists()
        compose.runOnIdle { provider.value = provider.value.copy(vertexAI = true) }
        field(keyLabel).assertExists()
        field(urlLabel).assertDoesNotExist()
        compose.runOnIdle { provider.value = provider.value.copy(useServiceAccount = true) }
        field(keyLabel).assertDoesNotExist()
        field(urlLabel).assertDoesNotExist()
        compose.runOnIdle { provider.value = provider.value.copy(vertexAI = false, useServiceAccount = false) }
        field(keyLabel).assertExists()
        field(urlLabel).assertExists()
        compose.runOnIdle {
            assertEquals("test-key", provider.value.apiKey)
            assertEquals(ProviderSetting.Google().baseUrl, provider.value.baseUrl)
        }
    }

    @Test fun googleWrongApiVersionDisplaysGuidanceWithoutChangingTheEndpoint() {
        val provider = ProviderSetting.Google(baseUrl = "https://proxy.example/v1")
        var guidance = ""
        compose.setContent {
            guidance = LocalResources.current.getString(R.string.zeroagent6s_provider_google_url)
            CompositionLocalProvider(LocalToaster provides rememberToasterState()) {
                MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProviderConfigure(provider) { error("Rendering must not edit the provider") }
                } }
            }
        }
        compose.onNodeWithText(guidance).assertExists()
    }
}
