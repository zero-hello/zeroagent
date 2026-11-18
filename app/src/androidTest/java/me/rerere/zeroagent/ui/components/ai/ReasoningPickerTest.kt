package me.rerere.zeroagent.ui.components.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.ai.core.ReasoningLevel
import me.rerere.zeroagent.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReasoningPickerTest {
    @get:Rule
    val compose = createComposeRule()

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    private fun text(resource: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    @Test
    fun draggingPreviewsSupportedLevelBeforeCommittingOnRelease() {
        val committed = mutableListOf<ReasoningLevel>()
        compose.setContent {
            MaterialTheme {
                ReasoningPicker(
                    reasoningLevel = ReasoningLevel.LOW,
                    supportedLevels = listOf(ReasoningLevel.LOW, ReasoningLevel.HIGH),
                    capabilitiesDetected = true,
                    onUpdateReasoningLevel = committed::add,
                )
            }
        }

        compose.onNode(slider).performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.9f, centerY))
        }

        // The preview must follow the thumb without changing the assistant's saved setting.
        compose.onNodeWithText(text(R.string.reasoning_heavy)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(committed.isEmpty()) }

        compose.onNode(slider).performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(ReasoningLevel.HIGH), committed) }
    }

    @Test
    fun singleSupportedLevelDoesNotOfferAnInoperableSlider() {
        compose.setContent {
            MaterialTheme {
                ReasoningPicker(
                    reasoningLevel = ReasoningLevel.HIGH,
                    supportedLevels = listOf(ReasoningLevel.HIGH),
                    capabilitiesDetected = true,
                    onUpdateReasoningLevel = {},
                )
            }
        }

        compose.onNode(slider).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.reasoning_heavy)).assertIsDisplayed()
    }

    @Test
    fun unknownCapabilitiesRemainExplicitToTheUser() {
        compose.setContent {
            MaterialTheme {
                ReasoningPicker(
                    reasoningLevel = ReasoningLevel.AUTO,
                    supportedLevels = listOf(ReasoningLevel.AUTO),
                    capabilitiesDetected = false,
                    onUpdateReasoningLevel = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.zeroagent7_reasoning_unknown)).assertIsDisplayed()
    }
}
