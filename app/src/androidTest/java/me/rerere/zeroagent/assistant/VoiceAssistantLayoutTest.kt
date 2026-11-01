package me.rerere.zeroagent.assistant

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.zeroagent.R
import me.rerere.zeroagent.Screen
import me.rerere.zeroagent.ui.context.LocalNavController
import me.rerere.zeroagent.ui.context.Navigator
import me.rerere.zeroagent.ui.theme.ZeroAgentTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real setup page without requesting a role, recording, or starting an assistant. */
@RunWith(AndroidJUnit4::class)
class VoiceAssistantLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun assistantEntryActionsKeepReadableLabelsAt320dpAndDoubleFontSize() {
        val stack = mutableStateListOf<NavKey>(Screen.SettingVoiceAssistant)
        lateinit var resources: Resources
        var pixelsPerDp = 1f
        compose.setContent {
            resources = LocalResources.current
            val density = LocalDensity.current
            pixelsPerDp = density.density
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
                LocalNavController provides Navigator(stack),
            ) {
                ZeroAgentTheme {
                    Box(Modifier.width(320.dp)) { SettingVoiceAssistantPage() }
                }
            }
        }
        compose.waitForIdle()

        listOf(R.string.zeroagent9_choose_assistant, R.string.zeroagent9_try_assistant).forEach { labelId ->
            val label = resources.getString(labelId)
            // Scroll the production LazyColumn to the item before inspecting its unmerged Text.
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
            val text = compose.onNodeWithText(label, useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
            assertTrue("The action label must expose its real text layout: $label", layouts.isNotEmpty())
            val layout = layouts.single()
            assertTrue(
                "Action label collapsed into ${layout.lineCount} lines at 320dp/font200: $label",
                layout.lineCount <= 2,
            )
            assertFalse("Action label exceeded its available lines: $label", layout.multiParagraph.didExceedMaxLines)
            // A wrap-content Text can retain a wider paragraph measurement. Check
            // the actual glyph lines, including truncation, rather than that blank area.
            repeat(layout.lineCount) { line ->
                assertFalse("Action label was ellipsized: $label", layout.isLineEllipsized(line))
                assertTrue("Action label crossed its start edge: $label", layout.getLineLeft(line) >= -1f)
                assertTrue("Action label crossed its end edge: $label", layout.getLineRight(line) <= layout.size.width + 1f)
            }
            assertEquals("Action label was truncated: $label", label.length,
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            assertTrue(
                "Action label must not become a tall column of glyphs: $label",
                layout.size.height <= 96f * pixelsPerDp + 1f,
            )
        }
    }
}
