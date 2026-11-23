package me.rerere.zeroagent.ui.pages.chat

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatDrawerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bigScreen = mutableStateOf(false)
    private val drawerEnabled = mutableStateOf(true)
    private var contentTaps = 0
    private lateinit var drawerState: DrawerState
    private var contentBackPresses = 0

    @Test
    fun modalDrawerOpensAndBackClosesItWithoutLeavingChat() {
        showChat()
        compose.onNodeWithText("Conversation list").assertIsNotDisplayed()
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Conversation list").assertIsNotDisplayed()
        compose.onNodeWithText("Chat content").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, contentBackPresses) }
    }

    @Test
    fun permanentDrawerShowsTheSameChatAndConversationList() {
        bigScreen.value = true
        showChat()
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
        compose.onNodeWithText("Chat content").assertIsDisplayed()
    }

    @Test
    fun switchingToPermanentClosesModalStateBeforeReturningToPhone() {
        showChat()
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
        compose.runOnIdle { bigScreen.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(drawerState.isOpen) }
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
        compose.runOnIdle { bigScreen.value = false }
        compose.onNodeWithText("Conversation list").assertIsNotDisplayed()
        compose.onNodeWithText("Chat content").assertIsDisplayed()
    }

    @Test
    fun openingSettingsDetailFromTheDrawerLeavesDetailTouchable() {
        showChat()
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("Open setting detail").performTouchInput { click() }
        compose.onNodeWithText("Detail action").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(DrawerValue.Closed, drawerState.currentValue)
            assertEquals(DrawerValue.Closed, drawerState.targetValue)
            val expectedOffset = with(compose.density) { -120.dp.toPx() }
            assertEquals(expectedOffset, drawerState.currentOffset, 1f)
            assertEquals(1, contentTaps)
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
    }

    private fun showChat() {
        compose.setContent {
            MaterialTheme {
                drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                ChatDrawerLayout(
                    isBigScreen = bigScreen.value,
                    drawerState = drawerState,
                    drawerWidth = 120.dp,
                    enabled = drawerEnabled.value,
                    drawerContent = {
                        Column {
                            Text("Conversation list")
                            Button(onClick = { drawerEnabled.value = false }) { Text("Open setting detail") }
                        }
                    },
                ) {
                    BackHandler {
                        if (drawerEnabled.value) contentBackPresses++ else drawerEnabled.value = true
                    }
                    Column {
                        if (drawerEnabled.value) {
                            Text("Chat content")
                            Button(onClick = { scope.launch { drawerState.open() } }) {
                                Text("Open drawer")
                            }
                        } else {
                            Button(onClick = { contentTaps++ }) { Text("Detail action") }
                        }
                    }
                }
            }
        }
    }
}
