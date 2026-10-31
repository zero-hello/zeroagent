package me.rerere.zeroagent.ui.pages.setting.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelGenerationSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class ModelGenerationLocaleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun decimalControlRespondsToConfigurationChangesWithoutRewritingTheModel() {
        val configuration = mutableStateOf(Configuration().apply { setLocale(Locale.US) })
        val model = Model(
            supportedParameters = listOf("temperature"),
            generation = ModelGenerationSettings(temperature = .5f, topP = null),
        )
        var changes = 0
        var updated: Model? = null
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration.value) {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ModelGenerationControls(model, null) { changes++; updated = it }
                    }
                }
            }
        }
        compose.onNodeWithText("0.5").assertIsDisplayed()
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply { setLocale(Locale.forLanguageTag("ru")) }
        }
        compose.onNodeWithText("0,5").assertIsDisplayed()
        compose.runOnIdle { assertEquals("A locale change must not edit inference settings", 0, changes) }
        compose.onNodeWithText("0,5").performTextReplacement("0,75")
        compose.runOnIdle {
            assertEquals(.75f, updated!!.generation.temperature!!, .0001f)
            assertEquals(model.generation.copy(temperature = .75f), updated!!.generation)
        }
    }
}
