package me.rerere.zeroagent.ui.components.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.zeroagent.data.datastore.DisplaySetting
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.ui.context.LocalSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialContentColorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun transparentSurfacesFollowMaterialThemeChanges() {
        val dark = mutableStateOf(true)
        var inherited = Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                ZeroAgentScaffold(containerColor = Color.Transparent) { padding ->
                    val foreground = LocalContentColor.current
                    SideEffect { inherited = foreground }
                    Text("Foreground", Modifier.padding(padding))
                }
            }
        }
        for (darkMode in listOf(true, false, true)) {
            compose.runOnIdle { dark.value = darkMode }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(if (darkMode) darkColorScheme().onSurface else lightColorScheme().onSurface, inherited)
            }
        }
    }
}
