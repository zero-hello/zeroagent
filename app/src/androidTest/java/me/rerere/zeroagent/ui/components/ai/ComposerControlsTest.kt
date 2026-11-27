package me.rerere.zeroagent.ui.components.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real composer controls without permissions, Koin, or network services. */
@RunWith(AndroidJUnit4::class)
class ComposerControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun narrowComposerKeepsEveryActionInsideItsBoundsWithoutOverlappingAtLargeFontSize() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompactComposer {
                ComposerActionRow(
                    loading = true,
                    hasDraft = true,
                    onAttach = {},

                    onVoiceCall = {},
                    onSend = {},
                    onStop = {},
                    onModelOptions = {},
                    onReasoning = {},
                )
            }
        }

        compose.onNodeWithTag("composer_asr").assertDoesNotExist()
        compose.onNodeWithTag("composer_voice").assertDoesNotExist()
        val actions = listOf("attach", "model", "reasoning", "send", "stop")
        val parent = compose.onNodeWithTag("composer_actions").fetchSemanticsNode().boundsInRoot
        val bounds = actions.map { action ->
            val node = compose.onNodeWithTag("composer_$action")
            node.assertIsDisplayed().assertIsEnabled()
            val rect = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$action must have a 48dp-wide target", rect.width >= 48f * density - 1f)
            assertTrue("$action must have a 48dp-high target", rect.height >= 48f * density - 1f)
            assertTrue("$action must stay inside the composer", rect.left >= parent.left - 1f && rect.right <= parent.right + 1f)
            rect
        }
        bounds.forEachIndexed { index, rect ->
            bounds.drop(index + 1).forEach { other ->
                val overlapWidth = minOf(rect.right, other.right) - maxOf(rect.left, other.left)
                val overlapHeight = minOf(rect.bottom, other.bottom) - maxOf(rect.top, other.top)
                assertTrue("Action touch targets must not overlap", overlapWidth <= 0.5f || overlapHeight <= 0.5f)
            }
        }
    }

    @Test
    fun generatingWithANextMessageDraftKeepsStopAndSendSeparatelyOperable() {
        compose.setContent {
            MaterialTheme {
                val result = remember { mutableStateOf("Generating with next draft") }
                Column {
                    ComposerActionRow(
                        loading = true,
                        hasDraft = true,
                        onAttach = {},

                        onVoiceCall = {},
                        onSend = { result.value = "Next draft queued" },
                        onStop = { result.value = "Generation stopped" },
                        onModelOptions = {},
                    )
                    Text(result.value)
                }
            }
        }

        compose.onNodeWithTag("composer_stop").assertIsEnabled().performClick()
        compose.onNodeWithText("Generation stopped").assertIsDisplayed()
        compose.onNodeWithTag("composer_send").assertIsEnabled().performClick()
        compose.onNodeWithText("Next draft queued").assertIsDisplayed()
    }

    @Test
    fun emptyIdleComposerDoesNotOfferSendOrStop() {
        compose.setContent {
            MaterialTheme {
                ComposerActionRow(
                    loading = false,
                    hasDraft = false,
                    onAttach = {},

                    onVoiceCall = null,
                    onSend = {},
                    onStop = {},
                    onModelOptions = {},
                )
            }
        }

        compose.onNodeWithTag("composer_send").assertIsNotEnabled()
        compose.onNodeWithTag("composer_stop").assertDoesNotExist()
        compose.onNodeWithTag("composer_voice").assertDoesNotExist()
    }

    @Test
    fun longPressingSendWithADraftPreservesSendWithoutAnswerWithoutTriggeringNormalSend() {
        compose.setContent {
            MaterialTheme {
                val result = remember { mutableStateOf("Draft ready") }
                Column {
                    ComposerActionRow(
                        loading = true,
                        hasDraft = true,
                        onAttach = {},

                        onVoiceCall = null,
                        onSend = { result.value = "Normal send" },
                        onStop = { result.value = "Generation stopped" },
                        onModelOptions = {},
                        onLongSend = { result.value = "Sent without answer" },
                    )
                    Text(result.value)
                }
            }
        }

        compose.onNodeWithTag("composer_send").performTouchInput { longClick() }
        compose.onNodeWithText("Sent without answer").assertIsDisplayed()
        compose.onNodeWithText("Normal send").assertDoesNotExist()
        compose.onNodeWithTag("composer_stop").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun permissionRemainsOperableWithoutAnExternalModelSelectorOnNarrowScreens() {
        compose.setContent {
            CompactComposer {
                val destination = remember { mutableStateOf("Composer") }
                ComposerPermissionRow {
                    androidx.compose.material3.TextButton(onClick = { destination.value = "Permissions" }) {
                        Text("Conversation access permissions")
                    }
                }
                Text(destination.value)
            }
        }
        compose.onNodeWithText("Conversation access permissions").assertIsDisplayed().performClick()
        compose.onNodeWithText("Permissions").assertIsDisplayed()
        compose.onNodeWithTag("composer_model").assertDoesNotExist()
        compose.onNodeWithTag("composer_reasoning").assertDoesNotExist()
    }

    @Composable
    private fun CompactComposer(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
            MaterialTheme {
                Column(Modifier.width(272.dp)) { content() }
            }
        }
    }
}
